package dev.huichen.atlas

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.PlaylistRemove
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun SettingRow(icon: ImageVector?, title: String, color: Color? = null, trailing: (@Composable () -> Unit)? = null, chevron: Boolean = false, onClick: (() -> Unit)? = null) {
  Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.press(1f, onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 13.dp),
    verticalAlignment = Alignment.CenterVertically) {
    if (icon != null) { Icon(icon, null, Modifier.size(22.dp), tint = color ?: pal.accent); Spacer(Modifier.width(14.dp)) }
    T(title, 17f, color = color ?: pal.primary, modifier = Modifier.weight(1f))
    trailing?.invoke()
    if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = pal.tertiary)
  }
}

fun syncStatus(): String = when (Sync.state) {
  "off" -> "未开启"; "syncing" -> "同步中…"; "error" -> "出错了"
  else -> if (Sync.at > 0) "已同步 " + DateUtils.getRelativeTimeSpanString(Sync.at.toLong(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) else "已开启"
}

@Composable
fun SettingsSheet() {
  var page by remember { mutableStateOf("main") }
  AnimatedContent(page, transitionSpec = {
    if (targetState != "main") (slideInHorizontally(calm()) { it } + fadeIn()) togetherWith (slideOutHorizontally(calm()) { -it / 3 } + fadeOut())
    else (slideInHorizontally(calm()) { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally(calm()) { it } + fadeOut())
  }, label = "settings") { p ->
    when (p) {
      "sync" -> SyncPage { page = "main" }
      "order" -> OrderPage { page = "main" }
      "update" -> UpdatePage { page = "main" }
      else -> SettingsMain { page = it }
    }
  }
}

@Composable
fun SettingsMain(go: (String) -> Unit) {
  val ctx = LocalContext.current
  var askWipe by remember { mutableStateOf(false) }
  var themeMenu by remember { mutableStateOf(false) }
  val act = ctx.fragmentActivity()
  // 2.6: what 导出 writes (encrypted or plain) is made first, then the file picker saves exactly that
  var pendingExport by remember { mutableStateOf<String?>(null) }
  var askExport by remember { mutableStateOf(false) }
  val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    val text = pendingExport; pendingExport = null
    if (uri != null && text != null) runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray(Charsets.UTF_8)) } }
      .onSuccess { Store.show("备份已导出") }.onFailure { Store.show("导出失败") }
  }
  var pendingImport by remember { mutableStateOf<String?>(null) }
  var encImport by remember { mutableStateOf<String?>(null) }
  var relockMenu by remember { mutableStateOf(false) }
  // a plain backup (as read from the file, or just decrypted) goes down the existing import path
  val importPlain: (String) -> Unit = { text ->
    when {
      Store.readBackup(text) == null -> Store.show("这不是账号星图的备份文件")
      // replacing what is there is asked first; the old data is kept as a restore point either way
      Store.data.identities.isNotEmpty() || Store.data.accounts.isNotEmpty() -> pendingImport = text
      Store.importBackup(text) -> Store.popAll()
    }
  }
  var askDemo by remember { mutableStateOf(false) }
  var askRestore by remember { mutableStateOf(false) }
  val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      val text = runCatching { ctx.contentResolver.openInputStream(uri)!!.use { String(it.readBytes(), Charsets.UTF_8) } }.getOrNull()
      when {
        text == null -> Store.show("这不是账号星图的备份文件")
        BackupCrypto.isEnvelope(text) -> encImport = text   // 2.6: password-protected backup
        else -> importPlain(text)
      }
    }
  }
  Column {
    SheetBar(null, "设置", "完成") { Store.pop() }
    FormScroll {
      FormGroup {
        SettingRow(Icons.Outlined.Cloud, "加密同步", trailing = { T(syncStatus(), 14f, color = pal.secondary) }, chevron = true) { go("sync") }
      }
      FormGroup {
        // 2.6: with the 应用锁 on, showing every value (turning this off) asks for authentication first
        val setMask: (Boolean) -> Unit = { v -> if (v) Store.putMask(true) else AppLock.reveal(act) { Store.putMask(false) } }
        SettingRow(Icons.Outlined.VisibilityOff, "默认隐藏账号", trailing = {
          Switch(Store.maskValues, { setMask(it) }, colors = SwitchDefaults.colors(checkedTrackColor = hex("#34c759"), checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent))
        }) { setMask(!Store.maskValues) }
        Divider()
        SettingRow(Icons.Outlined.PersonOff, "隐藏用户名", trailing = {
          Switch(Store.hideNames, { Store.putHideNames(it) }, colors = SwitchDefaults.colors(checkedTrackColor = hex("#34c759"), checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent))
        }) { Store.putHideNames(!Store.hideNames) }
        Divider()
        Box {
          SettingRow(Icons.Outlined.Contrast, "外观", trailing = { T(mapOf("dark" to "深色", "light" to "浅色", "system" to "跟随系统")[Store.theme] ?: "深色", 17f, color = pal.secondary) }) { themeMenu = true }
          DropdownMenu(themeMenu, { themeMenu = false }, Modifier.align(Alignment.TopEnd)) {
            listOf("dark" to "深色", "light" to "浅色", "system" to "跟随系统").forEach { (k, n) ->
              DropdownMenuItem({ Text(n) }, { themeMenu = false; Store.putTheme(k) })
            }
          }
        }
        Divider()
        SettingRow(Icons.Outlined.SwapVert, "调整卡片顺序", chevron = true) { go("order") }
        Divider()
        SettingRow(Icons.Outlined.School, "操作演示", chevron = true) { Tour.start(firstRun = false) }
      }
      // 2.6 应用锁
      val lockOK = remember(AppLock.resumeTick) { AppLock.available(ctx) }
      FormGroup(footer = if (!lockOK) "请先在系统设置中设置锁屏密码" else "打开账号星图、或离开后再回来时，要验证指纹、面部或锁屏密码。开启后，查看完整账号也要先验证。") {
        SettingRow(Icons.Outlined.Lock, "应用锁", color = if (lockOK || AppLock.enabled) null else pal.tertiary, trailing = {
          Switch(AppLock.enabled, { on -> act?.let { AppLock.setEnabled(it, on) } }, enabled = lockOK || AppLock.enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = hex("#34c759"), checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent))
        }) { if (lockOK || AppLock.enabled) act?.let { AppLock.setEnabled(it, !AppLock.enabled) } }
        if (AppLock.enabled) {
          Divider()
          Box {
            SettingRow(Icons.Outlined.Timer, "重新锁定", trailing = { T(AppLock.relockLabel(), 17f, color = pal.secondary) }) { relockMenu = true }
            DropdownMenu(relockMenu, { relockMenu = false }, Modifier.align(Alignment.TopEnd)) {
              AppLock.RELOCK.forEach { (sec, n) -> DropdownMenuItem({ Text(if (sec == 0) "立即" else "离开 $n 后") }, { relockMenu = false; AppLock.setRelock(sec) }) }
            }
          }
        }
      }
      FormGroup(footer = "生成一张卡片图片：有哪些 App、各用哪个身份。默认不显示任何邮箱和号码。") {
        SettingRow(Icons.Outlined.Share, "分享我的星图", chevron = true) { Store.push(Route.Share) }
      }
      FormGroup(footer = "备份是 JSON 文件，和网页版、iOS 版通用。" + when {
        Store.hasDemo -> ""
        Sync.on -> "开着同步时不能载入示例。"
        Demo.hasReal(Store.data) -> "已经有自己的数据时不能载入示例，免得和真实数据混在一起。"
        else -> ""
      }) {
        // 2.6: exporting always asks for the screen lock first (when the device has one), then for a password
        SettingRow(Icons.Outlined.Upload, "导出备份") { act?.let { AppLock.require(it, "导出备份") { askExport = true } } }
        Divider()
        SettingRow(Icons.Outlined.Download, "导入备份") { AppLock.expectReturn(); importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        if (Store.hasDemo) {
          Divider()
          SettingRow(Icons.Outlined.PlaylistRemove, "清除示例数据", color = pal.red) { Store.clearDemo() }
        } else {
          Divider()
          val ok = Store.canLoadDemo
          SettingRow(Icons.Outlined.PlaylistAdd, "载入示例", color = if (ok) null else pal.tertiary) { if (ok) askDemo = true }
        }
        Store.restoreTitle()?.let { t ->
          Divider()
          SettingRow(Icons.Outlined.Restore, t, trailing = {
            T(java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(Store.restoreAt)), 13f, color = pal.secondary)
          }) { askRestore = true }
        }
      }
      FormGroup(footer = "在蓝奏云下载，密码 0874。直接覆盖安装，数据不会丢失。") {
        val ver = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() }
        val uri = LocalUriHandler.current
        SettingRow(Icons.Outlined.NewReleases, "下载新版本", trailing = { if (ver != null) T("当前 $ver", 14f, color = pal.secondary) }, chevron = true) {
          runCatching { uri.openUri("https://wwbsj.lanzoup.com/b01i61dn1a") }
        }
        // 软件更新: its own page (checks when opened; 自动检查更新 is there too)
        Divider()
        SettingRow(Icons.Outlined.Update, "软件更新", chevron = true) { go("update") }
        Divider()
        SettingRow(Icons.Outlined.Code, "源代码 / GitHub 仓库", chevron = true) {
          runCatching { uri.openUri("https://github.com/Ckxkx/account-atlas") }
        }
      }
      FormGroup {
        SettingRow(Icons.Outlined.DeleteOutline, "清空全部数据", color = pal.red) { askWipe = true }
      }
      Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T("账号星图", 15f, FontWeight.SemiBold)
        T("不保存任何密码 · 数据只在本机和你自己的私有 Gist / WebDAV", 12f, color = pal.secondary, modifier = Modifier.padding(top = 6.dp), align = TextAlign.Center)
      }
    }
  }
  if (askWipe) Confirm("清空全部数据？",
    if (Sync.on) "所有邮箱、手机号和网站记录都会删除，开了同步的其他设备也会一起清空。建议先导出备份。" else "所有邮箱、手机号和网站记录都会从这台设备上删除。建议先导出备份。",
    "清空全部数据", { askWipe = false }) {
    Store.saveRestorePoint("wipe")
    Store.mutate("已清空") { it.copy(identities = emptyList(), accounts = emptyList()) }
    Store.setKind(Store.kind); Store.pop()
  }
  pendingImport?.let { text ->
    Confirm("导入备份？", "导入会替换现在的 ${Store.data.identities.size} 个身份和 ${Store.data.accounts.size} 个网站与 App。替换前的数据会先存一份，可以在设置里恢复。", "导入", { pendingImport = null }) {
      if (Store.importBackup(text)) Store.popAll() else Store.show("这不是账号星图的备份文件")
    }
  }
  if (askExport) ExportDialog({ askExport = false }) { text ->
    askExport = false; pendingExport = text
    AppLock.expectReturn(); exporter.launch("账号备份-${LocalDate.now()}.json")
  }
  encImport?.let { env -> BackupPasswordDialog(env, { encImport = null }) { plain -> encImport = null; importPlain(plain) } }
  if (askDemo) Confirm("载入示例数据？", "会加入几个虚构的邮箱、手机号和 App，都带示例标记，「清除示例数据」只会删掉它们。", "载入", { askDemo = false }) {
    Store.loadDemo(); Store.pop()
  }
  if (askRestore) Confirm(Store.restoreTitle() ?: "恢复数据", "会把数据换回那时的样子。现在的数据也会先存一份，可以再换回来。", "恢复", { askRestore = false }) {
    Store.restore(); Store.pop()
  }
}

/** Long-press a row and drag it; the first card in each group shows first in the wallet. */
@Composable
fun OrderPage(back: () -> Unit) {
  Column {
    SheetBar("设置", "卡片顺序", null, onLeft = back, leftIcon = true)
    FormScroll {
      // the hint goes under the last group that has cards
      val last = listOf("login", "phone", "email").firstOrNull { t -> Store.data.identities.any { it.type == t } }
      val hint = "长按拖动排序，排在第一位的卡会在卡包里最先出现。"
      OrderGroup("email", "邮箱", if (last == "email") hint else null)
      OrderGroup("phone", "手机号", if (last == "phone") hint else null)
      OrderGroup("login", "微信 / QQ", if (last == "login") hint else null)
    }
  }
}

@Composable
fun OrderGroup(type: String, title: String, footer: String?) {
  val list = Store.data.identities.filter { it.type == type }
  if (list.isEmpty()) return
  val hap = haptics()
  val scope = rememberCoroutineScope()
  val rowH = 58.dp
  val slot = with(LocalDensity.current) { rowH.toPx() }
  val ids = list.map { it.id }
  // Local order; rows never move in the layout while a finger is down. Neighbours slide out of the
  // way with a translation, the lifted row follows the finger, and the new order is committed once,
  // after the lifted row has settled into its slot (same frame the translations are dropped).
  var order by remember { mutableStateOf(ids) }
  var dragId by remember { mutableStateOf<String?>(null) }
  var settling by remember { mutableStateOf(false) }
  var from by remember { mutableIntStateOf(0) }
  var to by remember { mutableIntStateOf(0) }
  var dy by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(ids) { if (dragId == null) order = ids }
  FormGroup(title, footer) {
    order.forEachIndexed { k, id ->
      key(id) {
        val i = list.firstOrNull { it.id == id }
        if (i != null) {
          val dragging = dragId == id
          val target = when {
            dragId == null || dragging -> 0f
            k in (from + 1)..to -> -slot
            k in to until from -> slot
            else -> 0f
          }
          val shift = remember { Animatable(0f) }
          LaunchedEffect(target, dragId) { if (dragId == null) shift.snapTo(0f) else shift.animateTo(target, calm(Spring.StiffnessMedium)) }
          val lift by animateFloatAsState(if (dragging && !settling) 1.03f else 1f, calm(), label = "lift")
          val shadow by animateFloatAsState(if (dragging && !settling) 14f else 0f, calm(), label = "shadow")
          val sep = pal.stroke
          Row(Modifier.fillMaxWidth().height(rowH).zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
              translationY = if (dragId == null) 0f else if (dragging) dy else shift.value
              scaleX = lift; scaleY = lift
              shadowElevation = shadow; shape = RoundedCornerShape(12.dp); clip = false
            }
            .background(if (dragging) pal.field else Color.Transparent, RoundedCornerShape(12.dp))
            .drawBehind { if (k > 0 && dragId == null) drawRect(sep, Offset(16.dp.toPx(), 0f), Size(size.width - 16.dp.toPx(), 0.5.dp.toPx())) }
            .pointerInput(id) {
              detectDragGesturesAfterLongPress(
                onDragStart = {
                  if (dragId == null && !settling) {
                    from = order.indexOf(id); to = from; dy = 0f; dragId = id
                    hap(android.view.HapticFeedbackConstants.LONG_PRESS)
                  }
                },
                onDragEnd = {
                  if (dragId == id && !settling) {
                    settling = true
                    scope.launch {
                      animate(dy, (to - from) * slot, animationSpec = calm(Spring.StiffnessMedium)) { v, _ -> dy = v }
                      val next = order.toMutableList().apply { removeAt(from); add(to, id) }
                      val moved = from != to
                      order = next; dragId = null; dy = 0f; settling = false
                      if (moved) Store.mutate(null) { s ->
                        val ranked = next.mapIndexed { n, x -> x to n.toDouble() }.toMap()
                        val updated = s.identities.map { if (it.id in ranked) it.copy(order = ranked[it.id]) else it }
                        val grp = next.mapNotNull { x -> updated.firstOrNull { it.id == x } }
                        val others = updated.filter { it.type != type }
                        val groups = KINDS.map { t -> if (type == t) grp else others.filter { it.type == t } }
                        s.copy(identities = groups.flatten() + others.filter { it.type !in KINDS })
                      }
                    }
                  }
                },
                onDragCancel = {
                  if (dragId == id && !settling) {
                    settling = true
                    scope.launch { animate(dy, 0f, animationSpec = calm(Spring.StiffnessMedium)) { v, _ -> dy = v }; to = from; dragId = null; dy = 0f; settling = false }
                  }
                },
              ) { ch, d ->
                if (dragId != id || settling) return@detectDragGesturesAfterLongPress
                ch.consume()
                dy = (dy + d.y).coerceIn(-from * slot, (order.size - 1 - from) * slot)
                val n = (from + (dy / slot).roundToInt()).coerceIn(0, order.size - 1)
                if (n != to) { to = n; hap(TICK) }
              }
            }
            .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallCard(i, kindIcon(i), 46.dp, 30.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
              T(Store.title(i), 16f, FontWeight.Medium, lines = 1)
              if (i.label.isNotEmpty() || i.isLogin) T(Store.display(i), 13f, color = pal.secondary, lines = 1)
            }
            Icon(Icons.Filled.DragHandle, "拖动", tint = pal.tertiary)
          }
        }
      }
    }
  }
}

@Composable
fun SyncPage(back: () -> Unit) {
  var prov by remember { mutableStateOf("gitee") }
  var token by remember { mutableStateOf("") }
  var pass by remember { mutableStateOf("") }
  var davUrl by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
  var davPick by remember { mutableStateOf("") }
  var davUser by remember { mutableStateOf("") }
  val urlFocus = remember { androidx.compose.ui.focus.FocusRequester() }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf("") }
  var askOff by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val uri = LocalUriHandler.current
  Column {
    SheetBar("设置", "加密同步", null, onLeft = back, leftIcon = true)
    FormScroll {
      if (Sync.on) {
        FormGroup {
          Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val (ic, c) = when (Sync.state) { "error" -> Icons.Outlined.ErrorOutline to hex("#ff9f0a"); "syncing" -> Icons.Outlined.CloudSync to pal.accent; else -> Icons.Outlined.CheckCircle to pal.accent }
            Icon(ic, null, Modifier.size(32.dp), tint = c)
            Spacer(Modifier.width(12.dp))
            Column {
              T(if (Sync.state == "error") Sync.err else if (Sync.state == "syncing") "正在同步…" else "已开启加密同步", 16f, FontWeight.SemiBold)
              T("存放在 " + Sync.place +
                (if (Sync.at > 0) " · " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date(Sync.at.toLong())) else ""), 13f, color = pal.secondary)
            }
          }
          Divider()
          SettingRow(null, "立即同步", color = if (Sync.state == "syncing") pal.tertiary else pal.accent) { if (Sync.state != "syncing") Sync.runNow() }
        }
        FormGroup(footer = "关闭后这台设备不再同步，云端和本机的数据都会保留。") {
          SettingRow(null, "关闭同步", color = pal.red) { askOff = true }
        }
      } else {
        FormGroup("同步方式") {
          listOf("gitee" to "Gitee · 国内直连", "github" to "GitHub Gist", "webdav" to "WebDAV · 坚果云、NAS 等").forEachIndexed { k, (v, n) ->
            if (k > 0) Divider()
            SettingRow(null, n, trailing = { if (prov == v) Icon(Icons.Outlined.CheckCircle, null, tint = pal.accent) }) { prov = v; error = "" }
          }
        }
        if (prov == "webdav") {
          val url = davUrl.text
          val nut = "jianguoyun" in url
          val preset = Sync.DAV_PRESETS.firstOrNull { it.id == davPick }
          FormGroup(footer = if (nut) "坚果云要用「第三方应用密码」，不是登录密码：网页版 账户信息 › 安全选项，或坚果云 App 设置 › 第三方应用管理 里添加。" +
            "数据先用同步密码加密，再存到服务器的 AccountAtlas/atlas.enc.json，别人看不到内容。每台设备填同样的账号、应用密码和同步密码即可。同步密码忘了就解不开。"
            else (preset?.hint?.takeIf { it.isNotEmpty() }?.let { "$it\n" } ?: "") +
            "数据先用同步密码加密，再存到服务器的 AccountAtlas/atlas.enc.json，别人看不到内容。每台设备填同样的服务器地址、账号、密码和同步密码即可。同步密码忘了就解不开。") {
            Box(Modifier.padding(vertical = 12.dp)) {
              Chips(Sync.DAV_PRESETS.map { it.name to null }, preset?.name) { name ->
                val p = Sync.DAV_PRESETS.first { it.name == name }
                davPick = p.id; error = ""
                // a template selects its first placeholder so typing replaces it
                val ph = listOf("NAS地址", "你的域名", "节点名", "地址").firstOrNull { it in p.url }
                val at = if (ph != null) p.url.indexOf(ph) else -1
                davUrl = androidx.compose.ui.text.input.TextFieldValue(p.url,
                  if (ph != null && at >= 0) androidx.compose.ui.text.TextRange(at, at + ph.length) else androidx.compose.ui.text.TextRange(p.url.length))
                if (p.template || p.url.isEmpty()) urlFocus.requestFocus()
              }
            }
            Divider()
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
              if (url.isEmpty()) T("https://example.com/dav/", 17f, color = pal.tertiary, lines = 1)
              androidx.compose.foundation.text.BasicTextField(davUrl, { davUrl = it }, Modifier.fillMaxWidth().focusRequester(urlFocus), singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = pal.primary, fontSize = androidx.compose.ui.unit.TextUnit(17f, androidx.compose.ui.unit.TextUnitType.Sp)),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(pal.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None))
            }
            Divider()
            PlainField(davUser, { davUser = it }, if (nut) "账号（坚果云登录邮箱）" else "账号", KeyboardType.Email)
            Divider()
            SecretField(token, { token = it }, if (nut || "koofr" in url) "应用密码" else "密码")
            Divider()
            SecretField(pass, { pass = it }, "同步密码")
            if (nut) {
              Divider()
              SettingRow(null, "怎么获取坚果云应用密码", color = pal.accent) { uri.openUri("https://help.jianguoyun.com/?p=2064") }
            }
          }
        } else FormGroup(footer = "数据先用同步密码加密，再存进你自己的私有 Gist，别人看不到内容。每台设备填同一个令牌和同步密码即可。同步密码忘了就解不开。") {
          SecretField(token, { token = it }, if (prov == "gitee") "Gitee 私人令牌（勾选 gists）" else "GitHub 令牌（gist 权限）")
          Divider()
          SecretField(pass, { pass = it }, "同步密码")
          Divider()
          SettingRow(null, if (prov == "gitee") "去 Gitee 创建令牌" else "去 GitHub 创建令牌", color = pal.accent) {
            uri.openUri(if (prov == "gitee") "https://gitee.com/profile/personal_access_tokens/new" else "https://github.com/settings/tokens/new?scopes=gist&description=%E8%B4%A6%E5%8F%B7%E6%98%9F%E5%9B%BE")
          }
        }
        if (error.isNotEmpty()) FormGroup { T(error, 15f, color = pal.red, modifier = Modifier.padding(16.dp)) }
        FormGroup {
          val ok = !busy && token.isNotBlank() && pass.length >= 4 && (prov != "webdav" || (davUrl.text.isNotBlank() && davUser.isNotBlank()))
          Box(Modifier.fillMaxWidth().height(50.dp).press(1f, enabled = ok) {
            busy = true; error = ""
            scope.launch {
              try {
                val found = Sync.enable(token, pass, prov, davUrl.text, davUser)
                Store.setKind(Store.kind)
                Store.show(if (found) "已连接，云端数据已合并" else "同步已开启")
              } catch (e: Exception) { error = e.message ?: "同步失败" }
              busy = false
            }
          }, contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else T("开启同步", 17f, FontWeight.SemiBold, if (ok) pal.accent else pal.tertiary)
          }
        }
      }
    }
  }
  if (askOff) Confirm("关闭同步？", "关闭后这台设备不再同步，云端和本机的数据都会保留。", "关闭同步", { askOff = false }) { Sync.disable() }
}

@Composable
fun PlainField(value: String, onChange: (String) -> Unit, hint: String, keyboard: KeyboardType = KeyboardType.Text) {
  Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
    if (value.isEmpty()) T(hint, 17f, color = pal.tertiary, lines = 1)
    androidx.compose.foundation.text.BasicTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true,
      textStyle = androidx.compose.ui.text.TextStyle(color = pal.primary, fontSize = androidx.compose.ui.unit.TextUnit(17f, androidx.compose.ui.unit.TextUnitType.Sp)),
      cursorBrush = androidx.compose.ui.graphics.SolidColor(pal.accent),
      keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None))
  }
}

@Composable
fun SecretField(value: String, onChange: (String) -> Unit, hint: String) {
  Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
    if (value.isEmpty()) T(hint, 17f, color = pal.tertiary, lines = 1)
    androidx.compose.foundation.text.BasicTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true,
      textStyle = androidx.compose.ui.text.TextStyle(color = pal.primary, fontSize = androidx.compose.ui.unit.TextUnit(17f, androidx.compose.ui.unit.TextUnitType.Sp)),
      cursorBrush = androidx.compose.ui.graphics.SolidColor(pal.accent), visualTransformation = PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false))
  }
}


// MARK: 2.6 password-protected backups (same file as the web app and iOS, see BackupCrypto)

/** 导出备份: 「设置密码加密」 is on by default; off exports the plain JSON exactly as before. */
@Composable
fun ExportDialog(onDismiss: () -> Unit, onReady: (String) -> Unit) {
  if (AppLock.blocking) return
  var encrypt by remember { mutableStateOf(true) }
  var p1 by remember { mutableStateOf("") }
  var p2 by remember { mutableStateOf("") }
  var error by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  GlassAlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("导出备份") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().press(1f) { encrypt = !encrypt; error = "" }, verticalAlignment = Alignment.CenterVertically) {
          androidx.compose.material3.Checkbox(encrypt, { encrypt = it; error = "" })
          Text("设置密码加密")
        }
        if (encrypt) {
          PasswordBox(p1, { p1 = it; error = "" }, "密码（至少 4 位）")
          PasswordBox(p2, { p2 = it; error = "" }, "再输一次")
          if (error.isNotEmpty()) Text(error, color = pal.red)
          Text("忘记密码将无法恢复这份备份，我们也无法找回", color = hex("#ff9f0a"), fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp))
        } else {
          Text("不加密的备份是明文 JSON，拿到文件的人能看到里面所有的邮箱和手机号。", color = pal.secondary, fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp))
        }
      }
    },
    confirmButton = {
      androidx.compose.material3.TextButton({
        if (busy) return@TextButton
        val plain = Store.backupJSON()
        if (!encrypt) { onReady(plain); return@TextButton }
        when {
          p1.length < 4 -> error = "密码至少 4 位"
          p1 != p2 -> error = "两次输入的密码不一样"
          else -> {
            busy = true
            scope.launch {
              val out = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { BackupCrypto.encrypt(plain, p1) } }.getOrNull()
              busy = false
              if (out != null) onReady(out) else error = "加密失败"
            }
          }
        }
      }, enabled = !busy) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("导出") }
    },
    dismissButton = { androidx.compose.material3.TextButton({ onDismiss() }, enabled = !busy) { Text("取消") } })
}

/** An encrypted backup was picked: ask for its password; a wrong one shows 密码错误 and can be tried again. */
@Composable
fun BackupPasswordDialog(env: String, onDismiss: () -> Unit, onPlain: (String) -> Unit) {
  if (AppLock.blocking) return
  var pass by remember { mutableStateOf("") }
  var error by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  GlassAlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("加密的备份") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("这份备份设置了密码，输入导出时设的密码。", color = pal.secondary)
        PasswordBox(pass, { pass = it; error = "" }, "备份密码")
        if (error.isNotEmpty()) Text(error, color = pal.red)
      }
    },
    confirmButton = {
      androidx.compose.material3.TextButton({
        if (busy || pass.isEmpty()) return@TextButton
        busy = true
        scope.launch {
          val r = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { BackupCrypto.decrypt(env, pass) } }
          busy = false
          r.onSuccess { onPlain(it) }.onFailure { e ->
            if (e is BackupCrypto.WrongPassword) error = "密码错误"
            else { onDismiss(); Store.show("这不是账号星图的备份文件") }
          }
        }
      }, enabled = !busy && pass.isNotEmpty()) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("解锁") }
    },
    dismissButton = { androidx.compose.material3.TextButton({ onDismiss() }, enabled = !busy) { Text("取消") } })
}

@Composable
fun PasswordBox(value: String, onChange: (String) -> Unit, hint: String) {
  androidx.compose.material3.OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true,
    placeholder = { Text(hint) }, visualTransformation = PasswordVisualTransformation(),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false))
}
