package dev.huichen.atlas

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 检查更新 (on by default, 设置 → 软件更新 → 自动检查更新). On resume, at most once a day, a plain GET of a
 * static file on the website (no account data, no ids). A newer version shows one dialog: 去下载 / 跳过此版本 /
 * 以后再说 (again the next day). Errors and 404 are ignored and don't count as the day's check, so the next
 * launch tries again. The dialog waits while the 应用锁, the 操作演示 or a sheet is up (see UpdateDialog).
 * 设置 → 软件更新 (UpdatePage) asks every time it opens and shows the result on the page itself.
 */
object Updater {
  const val URL = "https://huichen.dev/account-atlas/version.json"
  private lateinit var prefs: SharedPreferences
  var auto by mutableStateOf(true); private set
  var pending by mutableStateOf<UpdateInfo?>(null); private set
  var checking by mutableStateOf(false); private set
  /** what the 软件更新 page shows */
  var screen by mutableStateOf<UpdateScreen>(UpdateScreen.Checking); private set
  private var pageRun = 0
  var installed: String = "dev"; private set
  private val http by lazy {
    OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS).build()
  }

  fun init(ctx: Context) {
    prefs = ctx.getSharedPreferences("atlas", Context.MODE_PRIVATE)
    auto = prefs.getBoolean("atlas.updateCheck", true)
    installed = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "dev"
  }
  fun putAuto(v: Boolean) { auto = v; prefs.edit().putBoolean("atlas.updateCheck", v).apply() }
  private fun record(info: UpdateInfo?) {
    val last = prefs.getLong("atlas.updateLast", 0L)
    val next = UpdateRules.lastAfter(info != null, last, System.currentTimeMillis())
    if (next != last) prefs.edit().putLong("atlas.updateLast", next).apply()
  }

  fun maybeCheck() {
    if (!::prefs.isInitialized || checking || pending != null) return
    if (!UpdateRules.due(auto, installed, prefs.getLong("atlas.updateLast", 0L), System.currentTimeMillis())) return
    checking = true
    Store.scope.launch {
      val info = fetch()
      checking = false
      record(info)   // offline / 404 / bad file: not recorded, so the next launch tries again
      if (info != null && UpdateRules.popup(info, installed, prefs.getString("atlas.updateSkip", null))) pending = info
    }
  }

  /** the 软件更新 page: asks every time it opens (and on 重试); a skipped version is shown anyway */
  fun checkPage() {
    if (!::prefs.isInitialized) return
    val run = ++pageRun
    screen = UpdateScreen.Checking
    Store.scope.launch {
      val info = fetch()
      record(info)
      if (run == pageRun) screen = UpdateScreen.of(info, installed)
    }
  }

  fun skip() { pending?.let { prefs.edit().putString("atlas.updateSkip", it.version).apply() }; pending = null }
  /** 以后再说: the check ran today, so the next one (and the dialog) comes after a day */
  fun later() { pending = null }
  /** 去下载: copies the 蓝奏云 password and opens the download page */
  fun download(ctx: Context, p: UpdateInfo, open: (String) -> Unit) {
    if (p.password.isNotEmpty()) runCatching {
      val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
      cm.setPrimaryClip(android.content.ClipData.newPlainText("蓝奏云密码", p.password))
    }
    if (pending?.version == p.version) pending = null
    runCatching { open(p.url) }.onFailure { Store.show("打不开下载页") }
  }

  private suspend fun fetch(): UpdateInfo? = withContext(Dispatchers.IO) {
    runCatching {
      val req = Request.Builder().url(URL).header("Cache-Control", "no-cache").build()
      http.newCall(req).execute().use { r ->
        if (!r.isSuccessful) null else r.body?.string()?.takeIf { it.length < 20_000 }?.let { UpdateInfo.parse(it) }
      }
    }.getOrNull()
  }
}

@Composable
fun UpdateDialog() {
  val p = Updater.pending ?: return
  // never over the lock screen, the 操作演示, another question or a sheet
  if (AppLock.blocking || Tour.on || Store.mergeAsk > 0 || Store.routes.isNotEmpty()) return
  val ctx = LocalContext.current
  val uri = LocalUriHandler.current
  val body = buildString {
    p.notes.forEach { append("• ").append(it).append('\n') }
    if (p.notes.isNotEmpty()) append('\n')
    if (p.password.isNotEmpty()) append("蓝奏云密码 ${p.password}，点「去下载」会自动复制。") else append("在蓝奏云下载，直接覆盖安装，数据不会丢失。")
  }
  GlassAlertDialog(onDismissRequest = { Updater.later() },
    title = { Text("发现新版本 ${p.version}") },
    text = { Text(body) },
    confirmButton = { TextButton({ Updater.download(ctx, p) { uri.openUri(it) } }) { Text("去下载") } },
    dismissButton = {
      Row {
        TextButton({ Updater.skip() }) { Text("跳过此版本") }
        TextButton({ Updater.later() }) { Text("以后再说") }
      }
    })
}

// MARK: 设置 → 软件更新 (like iOS 设置 → 通用 → 软件更新)

@Composable
fun UpdatePage(back: () -> Unit) {
  LaunchedEffect(Unit) { Updater.checkPage() }
  Column {
    SheetBar("设置", "软件更新", null, onLeft = back, leftIcon = true)
    FormScroll {
      UpdateHeader()
      UpdateStatus(Updater.screen)
      FormGroup(footer = "每天最多检查一次，只读取最新版本号，不发送任何数据。") {
        SettingRow(Icons.Outlined.Autorenew, "自动检查更新", trailing = {
          Switch(Updater.auto, { Updater.putAuto(it) }, colors = SwitchDefaults.colors(checkedTrackColor = hex("#34c759"), checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent))
        }) { Updater.putAuto(!Updater.auto) }
      }
    }
  }
}

@Composable
private fun UpdateHeader() {
  val test = !VersionCmp.isRelease(Updater.installed)
  Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Image(painterResource(R.mipmap.ic_launcher_flat), null, Modifier.size(76.dp).clip(RoundedCornerShape(17.dp)))
    Spacer(Modifier.height(12.dp))
    T("账号星图", 20f, FontWeight.SemiBold)
    T("当前版本 " + (if (test) "测试版" else Updater.installed), 14f, color = pal.secondary, modifier = Modifier.padding(top = 4.dp))
  }
}

@Composable
private fun UpdateStatus(s: UpdateScreen) {
  when (s) {
    UpdateScreen.Checking -> FormGroup {
      Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        T("正在检查更新…", 15f, color = pal.secondary)
      }
    }
    UpdateScreen.Failed -> FormGroup {
      StatusLine(Icons.Outlined.ErrorOutline, hex("#ff9f0a"), "检查失败，请稍后再试")
      Divider()
      SettingRow(null, "重试", color = pal.accent) { Updater.checkPage() }
    }
    is UpdateScreen.Latest -> {
      FormGroup { StatusLine(Icons.Outlined.CheckCircle, hex("#34c759"), "已是最新版本") }
      if (s.notes.isNotEmpty()) NotesGroup(s.notes)
    }
    is UpdateScreen.Newer -> NewVersion(s.info, "发现新版本 ${s.info.version}")
    is UpdateScreen.TestBuild -> NewVersion(s.info, "最新版本 ${s.info.version}")
  }
}

@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, text: String) {
  Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, Modifier.size(24.dp), tint = tint)
    Spacer(Modifier.width(10.dp))
    T(text, 16f, FontWeight.SemiBold)
  }
}

@Composable
private fun NotesGroup(notes: List<String>) {
  FormGroup(header = "更新日志") {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      notes.forEach { n -> Row { T("•", 15f, color = pal.secondary); Spacer(Modifier.width(8.dp)); T(n, 15f) } }
    }
  }
}

@Composable
private fun NewVersion(info: UpdateInfo, title: String) {
  val ctx = LocalContext.current
  val uri = LocalUriHandler.current
  var copied by remember(info.version) { mutableStateOf(false) }
  FormGroup { StatusLine(Icons.Outlined.Autorenew, pal.accent, title) }
  if (info.notes.isNotEmpty()) NotesGroup(info.notes)
  val hint = when {
    info.password.isEmpty() -> "在蓝奏云下载，直接覆盖安装，数据不会丢失。"
    copied -> "已复制蓝奏云密码 ${info.password}，在下载页粘贴即可。直接覆盖安装，数据不会丢失。"
    else -> "蓝奏云密码 ${info.password}，点「去下载」会自动复制。直接覆盖安装，数据不会丢失。"
  }
  FormGroup(footer = hint) {
    Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(pal.accent)
      .press(0.97f) { Updater.download(ctx, info) { uri.openUri(it) }; copied = info.password.isNotEmpty() },
      contentAlignment = Alignment.Center) { T("去下载", 17f, FontWeight.SemiBold, Color.White) }
  }
}
