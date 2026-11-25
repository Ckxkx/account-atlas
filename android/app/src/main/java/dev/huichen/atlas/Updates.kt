package dev.huichen.atlas

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 检查更新: version numbers like 2.6 / 2.6.1 (an optional leading v). Anything else is not comparable. */
object VersionCmp {
  fun parse(v: String?): List<Int>? {
    val s = v?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
    if (s.isEmpty()) return null
    val parts = s.split('.')
    if (parts.size > 4) return null
    return parts.map { p -> if (p.isEmpty() || p.length > 6 || !p.all { it in '0'..'9' }) return null else p.toInt() }
  }
  /** <0 / 0 / >0, or null when either side is not a version number. 2.6 == 2.6.0 */
  fun compare(a: String?, b: String?): Int? {
    val x = parse(a) ?: return null
    val y = parse(b) ?: return null
    for (i in 0 until maxOf(x.size, y.size)) {
      val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
      if (d != 0) return d
    }
    return 0
  }
  fun isNewer(remote: String?, installed: String?): Boolean = (compare(remote, installed) ?: 0) > 0
  /** a released build: CI test builds are "dev" (Android) / "0.0" (iOS) and never check by themselves */
  fun isRelease(installed: String?): Boolean = parse(installed)?.any { it > 0 } == true
}

/** what https://huichen.dev/account-atlas/version.json says. Unknown fields are ignored. */
data class UpdateInfo(val version: String, val notes: List<String>, val url: String, val password: String, val github: String?) {
  companion object {
    const val DEFAULT_URL = "https://wwbsj.lanzoup.com/b01i61dn1a"
    const val DEFAULT_PASSWORD = "0874"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private fun str(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
    fun parse(text: String): UpdateInfo? = runCatching {
      val o = json.parseToJsonElement(text).jsonObject
      val v = str(o, "version")?.removePrefix("v") ?: return null
      if (VersionCmp.parse(v) == null) return null
      val notes = (o["notes"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.trim()?.take(200) }
        .filter { it.isNotEmpty() }.take(10)
      val url = str(o, "url")?.takeIf { it.startsWith("https://") && it.length < 300 } ?: DEFAULT_URL
      val pw = str(o, "password")?.takeIf { it.length in 1..16 } ?: (if (o.containsKey("password")) "" else DEFAULT_PASSWORD)
      val gh = str(o, "github")?.takeIf { it.startsWith("https://") }
      UpdateInfo(v, notes, url, pw, gh)
    }.getOrNull()
  }
}

/**
 * 检查更新 (on by default, 设置 → 自动检查更新). On resume, at most once a day, a plain GET of a static
 * file on the website (no account data, no ids). A newer version shows one dialog: 去下载 / 跳过此版本 /
 * 以后再说 (again the next day). Errors and 404 are ignored. The dialog waits while the 应用锁 or the
 * 操作演示 is up (see UpdateDialog in AtlasRoot).
 */
object Updater {
  const val URL = "https://huichen.dev/account-atlas/version.json"
  private const val DAY = 24 * 60 * 60 * 1000L
  private lateinit var prefs: SharedPreferences
  var auto by mutableStateOf(true); private set
  var pending by mutableStateOf<UpdateInfo?>(null); private set
  /** the pending one came from the 检查更新 row: shows over the settings sheet too */
  var manual by mutableStateOf(false); private set
  var checking by mutableStateOf(false); private set
  var status by mutableStateOf<String?>(null); private set
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

  fun maybeCheck() {
    if (!::prefs.isInitialized || !auto || checking || pending != null || !VersionCmp.isRelease(installed)) return
    val last = prefs.getLong("atlas.updateLast", 0L)
    val now = System.currentTimeMillis()
    if (now - last in 0 until DAY) return
    checking = true
    Store.scope.launch {
      val info = fetch()
      checking = false
      if (info == null) return@launch   // offline / 404 / bad file: quietly try again next time
      prefs.edit().putLong("atlas.updateLast", System.currentTimeMillis()).apply()
      if (VersionCmp.isNewer(info.version, installed) && prefs.getString("atlas.updateSkip", null) != info.version) { manual = false; pending = info }
    }
  }

  /** the 检查更新 row: always asks, and shows the dialog even for a skipped version */
  fun checkNow() {
    if (checking) return
    checking = true; status = null
    Store.scope.launch {
      val info = fetch()
      checking = false
      if (info == null) { Store.show("检查失败，请稍后再试"); return@launch }
      prefs.edit().putLong("atlas.updateLast", System.currentTimeMillis()).apply()
      if (VersionCmp.isNewer(info.version, installed) || !VersionCmp.isRelease(installed)) { manual = true; pending = info }
      else { status = "已是最新版本"; Store.show("已是最新版本") }
    }
  }

  fun skip() { pending?.let { prefs.edit().putString("atlas.updateSkip", it.version).apply() }; pending = null }
  /** 以后再说: the check ran today, so the next one (and the dialog) comes after a day */
  fun later() { pending = null }
  fun download(ctx: Context, open: (String) -> Unit) {
    val p = pending ?: return
    if (p.password.isNotEmpty()) runCatching {
      val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
      cm.setPrimaryClip(android.content.ClipData.newPlainText("蓝奏云密码", p.password))
    }
    pending = null
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
  // never over the lock screen, the 操作演示 or another question; an automatic one also waits for the sheets to close
  if (AppLock.blocking || Tour.on || Store.mergeAsk > 0 || (!Updater.manual && Store.routes.isNotEmpty())) return
  val ctx = LocalContext.current
  val uri = LocalUriHandler.current
  val body = buildString {
    p.notes.forEach { append("• ").append(it).append('\n') }
    if (p.notes.isNotEmpty()) append('\n')
    if (p.password.isNotEmpty()) append("蓝奏云密码 ${p.password}，点「去下载」会自动复制。") else append("在蓝奏云下载，直接覆盖安装，数据不会丢失。")
    if (!VersionCmp.isRelease(Updater.installed)) append("\n\n（当前是测试版）")
  }
  AlertDialog(onDismissRequest = { Updater.later() },
    title = { Text("发现新版本 ${p.version}") },
    text = { Text(body) },
    confirmButton = { TextButton({ Updater.download(ctx) { uri.openUri(it) } }) { Text("去下载") } },
    dismissButton = {
      Row {
        TextButton({ Updater.skip() }) { Text("跳过此版本") }
        TextButton({ Updater.later() }) { Text("以后再说") }
      }
    })
}
