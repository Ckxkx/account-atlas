package dev.huichen.atlas

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.Instant
import kotlin.random.Random

sealed class Route {
  data class IdentityR(val id: String) : Route()
  data class AccountR(val id: String) : Route()
  data class NewIdentity(val type: String, val via: String? = null) : Route()
  data class EditIdentity(val id: String) : Route()
  data class EditAccount(val id: String) : Route()
  data class Picker(val preset: String?) : Route()
  data object Settings : Route()
  data object Share : Route()
}

data class Entry(val key: Long, val route: Route)

data class Toast(val text: String, val action: String? = null, val id: Long = System.nanoTime(), val tap: Boolean = false)

/** App state. One instance, Compose reads the mutableState fields directly. */
object Store {
  private lateinit var prefs: SharedPreferences
  private lateinit var file: File
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  var data by mutableStateOf(AtlasData())
  /** 2.5 level 1: account values (email, number, QQ, 微信号) are masked everywhere unless turned off */
  var maskValues by mutableStateOf(true)
  /** 2.5 level 2: labels and usernames are hidden too (off by default) */
  var hideNames by mutableStateOf(false)
  /** cards whose values were revealed with the eye; forgotten when the app goes to the background */
  var revealed by mutableStateOf(setOf<String>())
  /** 操作演示: the screen shows sandbox data; nothing is saved or synced until it ends */
  var sandbox by mutableStateOf(false); private set
  private var realSnap: AtlasData? = null
  /** the restore point offered in 设置 (reason: demo | import | wipe | restore), null when there is none */
  var restoreWhy by mutableStateOf<String?>(null); private set
  var restoreAt by mutableStateOf(0L); private set
  var kind by mutableStateOf("email")
  var theme by mutableStateOf("dark")
  var activeID by mutableStateOf<String?>(null)
  /** sheet stack: the last one is on top (iOS presents a sheet over a sheet) */
  var routes by mutableStateOf(listOf<Entry>())
  private var nextKey = 0L
  var toast by mutableStateOf<Toast?>(null)
  var demoFocus by mutableStateOf<String?>(null)
  var toastAction: (() -> Unit)? = null
  var onSaved: (() -> Unit)? = null
  var ready = false; private set
  /** nothing was saved on this device: the app starts empty and offers the sample data once */
  var firstRun = false; private set
  /** the data file couldn't be read at launch: it was set aside and the last good copy loaded */
  var recovered = false; private set
  /** 2.4: how many duplicate identities wait on the one-time 合并 question (0 = don't ask) */
  var mergeAsk by mutableIntStateOf(0)

  /** Accounts saved before 2.3.1 with no icon of their own and no bundled one (王者荣耀, 金铲铲之战 …) were
   *  drawn with a parent site's logo or a tiny favicon. Look each name up on the App Store once and keep
   *  the artwork of an exact-name match. A few names per launch; each name is tried only once. */
  fun backfillIcons() {
    if (data.demo == true) return
    scope.launch {
      kotlinx.coroutines.delay(4000)
      val tried = (prefs.getStringSet("atlas.iconTried", emptySet()) ?: emptySet()).toMutableSet()
      val names = data.accounts.filter { it.icon.isNullOrEmpty() && IconCache.preset(it.name, it.domain) == null }
        .map { it.name.lowercase() }.filter { it.isNotEmpty() && it !in tried }.distinct().take(6)
      for (k in names) {
        val hits = AppStore.search(k)
        tried += k; prefs.edit().putStringSet("atlas.iconTried", tried.toSet()).apply()
        val h = hits.firstOrNull { it.name.lowercase() == k || it.trackName.lowercase() == k } ?: continue
        val ic = AppStore.icon(h) ?: continue
        if (data.accounts.none { it.name.lowercase() == k && it.icon.isNullOrEmpty() }) continue
        mutate(null, undo = false) { d -> d.copy(accounts = d.accounts.map { if (it.name.lowercase() == k && it.icon.isNullOrEmpty()) it.copy(icon = ic) else it }) }
      }
    }
  }

  private var seen = HashMap<String, Any>()
  private var undoSnap: AtlasData? = null
  private var toastJob: Job? = null

  fun init(ctx: Context) {
    if (ready) return
    prefs = ctx.getSharedPreferences("atlas", Context.MODE_PRIVATE)
    file = File(ctx.filesDir, "atlas.json")
    maskValues = prefs.getBoolean("atlas.maskValues", true)
    hideNames = prefs.getBoolean("atlas.hideNames", false)
    restoreWhy = prefs.getString("atlas.restoreWhy", null)?.takeIf { File(ctx.filesDir, "atlas.restore.json").exists() }
    restoreAt = prefs.getLong("atlas.restoreAt", 0L)
    kind = prefs.getString("atlas.kind", "email")?.takeIf { it in KINDS } ?: "email"
    theme = prefs.getString("atlas.theme", "dark") ?: "dark"
    val raw = if (file.exists()) runCatching { file.readText() }.getOrNull() else null
    data = if (raw.isNullOrBlank()) { firstRun = true; AtlasData() } else runCatching { decodeAtlas(raw) }.fold(
      onSuccess = { d ->
        // keep the last copy that opened fine, so an update can never be the only copy of the data
        if (d.identities.isNotEmpty() || d.accounts.isNotEmpty()) runCatching { File(ctx.filesDir, "atlas.prev.json").writeText(raw) }
        d
      },
      onFailure = {
        // the file is there but can't be read: never start over on top of it. Set it aside and
        // fall back to the last good copy.
        runCatching { File(ctx.filesDir, "atlas.unreadable-${System.currentTimeMillis() / 1000}.json").writeText(raw) }
        recovered = true
        runCatching { decodeAtlas(File(ctx.filesDir, "atlas.prev.json").readText()) }.getOrDefault(AtlasData())
      })
    remember()
    // 2.5: sample data is marked per item. Older builds kept a device list of sample ids (and a whole-file
    // flag): exactly the listed items get the marker, never anything else.
    run {
      val legacy = prefs.getStringSet(DEMO_KEY, emptySet()) ?: emptySet()
      val m = Demo.adoptLegacy(data, legacy)
      if (m !== data) { data = m; save() }
      if (legacy.isNotEmpty()) prefs.edit().remove(DEMO_KEY).apply()
    }
    // 2.4 (atlas.prev.json above is still the untouched copy): Apple login cards become their email /
    // phone, silently and losslessly. Duplicates are merged only after the user says yes, asked once.
    migrate24()
    if (Demo.hasReal(data) && prefs.getString("atlas.merge24", "") != "no") mergeAsk = Migrate24.dupCount(data)
    activeID = deck.firstOrNull()?.id
    ready = true
  }
  fun prefs() = prefs

  fun putMask(v: Boolean) { maskValues = v; revealed = emptySet(); prefs.edit().putBoolean("atlas.maskValues", v).apply() }
  fun putHideNames(v: Boolean) { hideNames = v; prefs.edit().putBoolean("atlas.hideNames", v).apply() }
  fun isRevealed(id: String) = !maskValues || id in revealed
  fun toggleReveal(id: String) { revealed = if (id in revealed) revealed - id else revealed + id }
  /** the app went to the background: every value is masked again */
  fun remask() { if (revealed.isNotEmpty()) revealed = emptySet() }
  fun putTheme(v: String) { theme = v; prefs.edit().putString("atlas.theme", v).apply() }
  private fun setKindPref(v: String) { kind = v; prefs.edit().putString("atlas.kind", v).apply() }

  fun push(r: Route) { routes = routes + Entry(nextKey++, r) }
  fun pop() { routes = routes.dropLast(1) }
  fun popAll() { routes = emptyList() }

  val deck: List<Identity> get() = data.identities.filter { it.type == kind }
  val active: Identity? get() = deck.firstOrNull { it.id == activeID } ?: if (activeID == "add") null else deck.firstOrNull()
  fun identity(id: String) = data.identities.firstOrNull { it.id == id }
  fun account(id: String) = data.accounts.firstOrNull { it.id == id }
  fun accountsOf(id: String) = data.accounts.filter { id in it.identityIds }.sortedWith(compareBy(nameOrder) { it.name })
  /** Every email / phone a 微信 / QQ is bound to (2.5: can be several); dangling or odd references count as none. */
  fun bindsOf(i: Identity): List<Identity> = if (!i.isLogin) emptyList() else i.bindIds.mapNotNull { identity(it) }.filter { !it.isLogin && it.id != i.id }
  /** the first binding (what older clients show) */
  fun viaOf(i: Identity): Identity? = bindsOf(i).firstOrNull()
  /** 微信 / QQ cards bound to this email / phone. */
  fun loginsVia(id: String) = data.identities.filter { it.isLogin && id in it.bindIds }
  /** Sites that log in with a 微信 / Apple card bound to this email / phone, minus the ones using it directly. */
  fun indirectAccounts(id: String): List<Pair<Identity, List<Account>>> {
    val direct = data.accounts.filter { id in it.identityIds }.map { it.id }.toSet()
    return loginsVia(id).map { l -> l to accountsOf(l.id).filter { it.id !in direct } }.filter { it.second.isNotEmpty() }
  }

  /** the value as shown: masked by default (2.5 level 1) unless revealed with the eye */
  fun display(i: Identity): String = if (isRevealed(i.id)) i.value else maskValue(i)
  /** labels and usernames: shown unless 隐藏用户名 is on (2.5 level 2) */
  fun labelText(s: String) = if (hideNames) maskName(s) else s
  fun handleText(h: String) = labelText(h)
  fun title(i: Identity) = if (i.label.isNotEmpty()) labelText(i.label) else if (i.isLogin) providerName(i.provider) else display(i)

  // every change goes through here: snapshot for undo, stamp, persist, sync
  fun mutate(message: String? = null, undo: Boolean = true, f: (AtlasData) -> AtlasData) {
    if (undo) undoSnap = data   // a silent fix-up (a late icon) must not replace what 撤销 restores
    data = f(data).copy(demo = null)
    save()
    if (message != null) show(message, if (undo) "撤销" else null) { undo() }
  }
  fun undo() { undoSnap?.let { data = it; save(); fixActive() } }

  private fun remember() {
    seen = HashMap()
    for (x in data.identities) seen[x.id] = x.copy(updatedAt = null)
    for (x in data.accounts) seen[x.id] = x.copy(updatedAt = null)
  }
  fun save() {
    val now = nowMS()
    val del = HashMap(data.deleted)
    val live = HashSet<String>()
    val ids = data.identities.map { x -> live += x.id; del.remove(x.id); if (seen[x.id] != x.copy(updatedAt = null) || x.updatedAt == null) x.copy(updatedAt = now) else x }
    val accs = data.accounts.map { x -> live += x.id; del.remove(x.id); if (seen[x.id] != x.copy(updatedAt = null) || x.updatedAt == null) x.copy(updatedAt = now) else x }
    for (id in seen.keys) if (id !in live) del[id] = now
    data = data.copy(identities = ids, accounts = accs, deleted = del)
    remember(); persist(); if (!sandbox) onSaved?.invoke()
  }
  fun persist() {
    if (sandbox) return   // 操作演示: nothing reaches the file
    runCatching {
      val tmp = File(file.parentFile, "atlas.json.tmp"); tmp.writeText(encodeAtlas(data)); tmp.renameTo(file)
    }
  }
  /** replace everything (sync merge), without new stamps */
  fun adopt(d: AtlasData) {
    if (sandbox) { realSnap = d; writeFile(d); return }   // the real data changed under the 操作演示
    data = d; remember(); persist(); migrate24(); fixActive()   // an older client may still add an Apple login card
  }
  /** what is really saved, even while the 操作演示 shows sandbox data */
  fun realData(): AtlasData = if (sandbox) realSnap ?: data else data
  private fun writeFile(d: AtlasData) {
    runCatching { val tmp = File(file.parentFile, "atlas.json.tmp"); tmp.writeText(encodeAtlas(d)); tmp.renameTo(file) }
  }

  // MARK: 操作演示 sandbox: the tour runs on the real screens with throwaway data

  fun beginSandbox() {
    if (sandbox) return
    realSnap = data
    sandbox = true
    undoSnap = null
    val sample = Identity(newID(), "email", "me@example.com", "示例邮箱", COLORS[0], demo = true)
    data = AtlasData(identities = listOf(sample))
    remember()
    setKind("email", sample.id)
  }
  fun endSandbox() {
    if (!sandbox) return
    data = realSnap ?: data
    realSnap = null; sandbox = false; undoSnap = null
    remember(); fixActive()
    popAll()
  }

  // MARK: restore point: written before anything that replaces data (sample, import, wipe, restore)

  fun saveRestorePoint(why: String) {
    if (sandbox) return
    val d = data
    if (d.identities.isEmpty() && d.accounts.isEmpty()) return   // nothing to lose
    runCatching { File(file.parentFile, "atlas.restore.json").writeText(encodeAtlas(d)) }.onSuccess {
      restoreWhy = why; restoreAt = System.currentTimeMillis()
      prefs.edit().putString("atlas.restoreWhy", why).putLong("atlas.restoreAt", restoreAt).apply()
    }
  }
  fun restoreTitle(): String? = when (restoreWhy) {
    null -> null
    "demo" -> "恢复导入示例前的数据"
    "import" -> "恢复导入备份前的数据"
    "wipe" -> "恢复清空前的数据"
    else -> "撤回刚才的恢复"
  }
  fun restore() {
    val f = File(file.parentFile, "atlas.restore.json")
    val b = runCatching { decodeAtlas(f.readText()) }.getOrNull() ?: run { show("备份读不出来了"); return }
    val keep = runCatching { encodeAtlas(data) }.getOrNull()
    mutate("已恢复 ${b.identities.size} 个身份和 ${b.accounts.size} 个网站") { it.copy(identities = b.identities, accounts = b.accounts) }
    // the data that was just replaced becomes the new restore point, so a restore can be taken back too
    if (keep != null) runCatching { f.writeText(keep) }.onSuccess {
      restoreWhy = "restore"; restoreAt = System.currentTimeMillis()
      prefs.edit().putString("atlas.restoreWhy", "restore").putLong("atlas.restoreAt", restoreAt).apply()
    }
    setKind(kind)
  }

  /** the silent part of the 2.4 migration; safe to run any time (idempotent) */
  fun migrate24() {
    val fixed = Migrate24.autoFix(data)
    if (fixed === data) return
    val demo = data.demo
    mutate(null, undo = false) { Migrate24.autoFix(it) }
    if (demo == true) { data = data.copy(demo = true); persist() }
  }
  fun mergeDuplicates() {
    mergeAsk = 0
    var n = 0
    mutate(null) { val (m, k) = Migrate24.merge(it); n = k; m }
    show("已合并 $n 个重复的身份", "撤销") { undo() }
    fixActive()
  }
  fun declineMerge() {
    mergeAsk = 0
    prefs.edit().putString("atlas.merge24", "no").apply()
  }
  /** The identity already holding this value (other than [except]), if any. */
  fun existing(type: String, provider: String?, value: String, except: String? = null): Identity? {
    val k = idKey(type, provider, value) ?: return null
    return data.identities.firstOrNull { it.id != except && idKey(it) == k }
  }
  /** The identity with this value, or a new one (reuse first: no value is ever typed in twice). Returns its id and whether it existed. */
  fun ensureIdentity(type: String, value: String, provider: String? = null, via: String? = null): Pair<String, Boolean> =
    ensureIdentity(type, value, provider, listOfNotNull(via))
  fun ensureIdentity(type: String, value: String, provider: String?, binds: List<String>): Pair<String, Boolean> {
    val v = value.trim()
    existing(type, provider, v)?.let { return it.id to true }
    val bound = binds.toMutableList()
    // a QQ whose QQ is an email you already have is bound to that email, without typing it twice
    if (type == "login" && provider == "qq" && v.contains('@'))
      data.identities.firstOrNull { it.type == "email" && normEmail(it.value) == normEmail(v) }?.id?.let { if (it !in bound) bound += it }
    val color = if (type == "login") providerColor(provider) else COLORS[data.identities.size % COLORS.size]
    val i = Identity(newID(), type, v, "", color, "", provider = if (type == "login") provider else null).let { if (type == "login") it.withBinds(bound) else it }
    mutate(null, undo = false) { it.copy(identities = it.identities + i) }
    return i.id to false
  }
  private fun fixActive() { if (active == null || deck.none { it.id == activeID }) activeID = deck.firstOrNull()?.id ?: "add" }

  /** 小提示: at most once a week, the card that hasn't had a new site in two weeks or more */
  fun nudge() {
    val now = nowMS(); val day = 86_400_000.0
    if (firstRun || recovered || sandbox || !Demo.hasReal(data) || now - prefs.getFloat("atlas.nudgeAtDays", 0f).toDouble() * day < 7 * day) return
    val demo = Demo.ids(data)
    var pick: Identity? = null; var oldest = Double.MAX_VALUE
    for (i in data.identities) {
      if (i.id in demo) continue
      val t = data.accounts.filter { i.id in it.identityIds }.maxOfOrNull { it.createdAt } ?: continue
      if (t < oldest) { oldest = t; pick = i }
    }
    val p = pick ?: return
    if (now - oldest < 14 * day) return
    prefs.edit().putFloat("atlas.nudgeAtDays", (now / day).toFloat()).apply()
    show("好久没来记录新账号啦，要不要来看看呢～")   // plain text only: no address, number or name on screen
  }

  // MARK: 2.5 copying an account value: marked sensitive (no clipboard preview), cleared after 60 s

  private var clip: Pair<String, Long>? = null
  fun copySecret(ctx: Context, value: String, shown: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val c = android.content.ClipData.newPlainText("账号", value)
    c.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    runCatching { cm.setPrimaryClip(c) }.onFailure { show("复制失败"); return }
    clip = value to System.currentTimeMillis()
    show("已复制 $shown · 60 秒后自动清除")
    val app = ctx.applicationContext
    scope.launch { delay(60_500); checkClipboard(app) }
  }
  /** clears the clipboard if it still holds what was copied 60 s ago (also checked when the app comes back) */
  fun checkClipboard(ctx: Context) {
    val (v, at) = clip ?: return
    if (System.currentTimeMillis() - at < 60_000) return
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    // in the background Android doesn't let an app read the clipboard: try again when it's back
    val now = runCatching { cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString() }.getOrNull() ?: return
    clip = null
    if (now == v) runCatching {
      if (android.os.Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
    }
  }

  /** 2.6: a plain line (no button) that does something when tapped; swipe it up to dismiss */
  fun tip(text: String, fn: () -> Unit) {
    toastAction = fn
    val t = Toast(text, tap = true)
    toast = t
    toastJob?.cancel()
    toastJob = scope.launch { delay(6000); if (toast?.id == t.id) toast = null }
  }

  fun show(text: String, action: String? = null, fn: (() -> Unit)? = null) {
    toastAction = fn
    val t = Toast(text, action)
    toast = t
    toastJob?.cancel()
    toastJob = scope.launch { delay(if (action == null) 2200 else 4500); if (toast?.id == t.id) toast = null }
  }

  fun setKind(k: String, focus: String? = null) {
    setKindPref(k)
    activeID = focus ?: deck.firstOrNull()?.id ?: "add"
  }

  // apps the user typed in by hand come back in the picker. Local only, not synced.
  /** bumped when the local lists change, so the picker recomposes (prefs are not observable) */
  var customRev by mutableIntStateOf(0); private set
  private fun savedCustom(): List<List<String>> = runCatching {
    JSON.decodeFromString<List<List<String>>>(prefs.getString("atlas.custom", "[]") ?: "[]")
  }.getOrDefault(emptyList())
  private fun hiddenApps(): Set<String> = runCatching {
    JSON.decodeFromString<List<String>>(prefs.getString("atlas.custom.hidden", "[]") ?: "[]").toSet()
  }.getOrDefault(emptySet())
  private fun putHidden(h: Collection<String>) { prefs.edit().putString("atlas.custom.hidden", JSON.encodeToString(h.toList())).apply() }
  private fun isPreset(n: String) = Presets.all.any { it.name.lowercase() == n.lowercase() }

  val customAppList: List<CustomApp> get() {
    customRev
    return mergeCustomApps(savedCustom(), data.accounts, hiddenApps(), ::isPreset)
  }
  val customApps: List<Preset> get() = customAppList.map { it.preset }

  /** Remember a hand-added app (with its own icon, if one was chosen); adding it again un-hides it. */
  fun rememberApp(p: Preset, icon: String? = null) {
    unhideApps(listOf(p.name))
    if (isPreset(p.name)) return
    val saved = savedCustom().filter { it.firstOrNull()?.lowercase() != p.name.lowercase() }.toMutableList()
    saved.add(0, if (icon.isNullOrEmpty()) listOf(p.name, p.domain, p.category) else listOf(p.name, p.domain, p.category, "", icon))
    prefs.edit().putString("atlas.custom", JSON.encodeToString(saved.take(80))).apply()
    customRev++
  }
  fun unhideApps(names: List<String>) {
    val h = hiddenApps(); val keys = names.map { it.trim().lowercase() }.toSet()
    if (h.any { it in keys }) { putHidden(h - keys); customRev++ }
  }
  /** "从列表移除": drop it from the saved list and hide it, accounts stay as they are. */
  fun removeApp(name: String) {
    val k = name.trim().lowercase()
    val saved = savedCustom().filter { it.firstOrNull()?.trim()?.lowercase() != k }
    prefs.edit().putString("atlas.custom", JSON.encodeToString(saved)).apply()
    putHidden(hiddenApps() + k)
    customRev++
  }

  fun backupJSON(): String {
    val o = kotlinx.serialization.json.buildJsonObject {
      put("app", kotlinx.serialization.json.JsonPrimitive("atlas"))
      put("version", kotlinx.serialization.json.JsonPrimitive(1))
      put("exportedAt", kotlinx.serialization.json.JsonPrimitive(Instant.now().toString()))
      put("identities", JSON.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(Identity.serializer()), data.identities))
      put("accounts", JSON.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(Account.serializer()), data.accounts))
    }
    return kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), o)
  }
  /** a backup file, read but not applied (null when it isn't one) */
  fun readBackup(text: String): AtlasData? {
    val raw = runCatching { JSON.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject }.getOrNull() ?: return null
    if (raw["identities"] !is kotlinx.serialization.json.JsonArray || raw["accounts"] !is kotlinx.serialization.json.JsonArray) return null
    return runCatching { decodeAtlas(text) }.getOrNull()
  }
  fun importBackup(text: String): Boolean {
    val b = readBackup(text) ?: return false
    saveRestorePoint("import")   // the data it replaces can always be brought back from 设置
    mutate("已导入 ${b.identities.size} 个身份和 ${b.accounts.size} 个网站") { it.copy(identities = b.identities, accounts = b.accounts) }
    setKind(kind)
    return true
  }

  // 2.5 sample data: every sample item carries demo: true; it is only ever added next to what is there,
  // and 「清除示例数据」 removes exactly the marked items. Older builds' device id list is converted at launch.
  private const val DEMO_KEY = "atlas.demoIds"
  fun demoIds(): Set<String> = Demo.ids(data)
  val hasDemo: Boolean get() = Demo.has(data)
  /** 载入示例 is offered only while there is nothing of the user's own */
  val canLoadDemo: Boolean get() = !sandbox && !Sync.on && !Demo.hasReal(data) && !Demo.has(data)

  fun loadDemo() {
    if (Demo.hasReal(data)) { show("已有自己的数据，不能载入示例"); return }
    saveRestorePoint("demo")
    val d = demo()
    mutate("已载入示例，可在「设置」里清除") { Demo.add(it, d) }
    setKind("email")
  }
  fun clearDemo() {
    mutate("已清除示例数据") { Demo.clear(it) }
    setKind(kind)
  }
  /** CI screenshots start from the sample data */
  fun seedDemo() {
    if (!firstRun) return
    firstRun = false
    data = Demo.add(AtlasData(), demo()); remember(); persist(); fixActive()
  }

  fun demo(): AtlasData {
    val ids = listOf(
      Identity(newID(), "email", "me@gmail.com", "主力邮箱", "#0a84ff"),
      Identity(newID(), "phone", "+86 138 0000 1234", "主力手机号", "#30d158", "移动，已实名"),
      Identity(newID(), "email", "shopping.fun@qq.com", "购物小号", "#ff375f"),
      Identity(newID(), "email", "dev@outlook.com", "开发专用", "#bf5af2"),
      Identity(newID(), "phone", "+86 170 0000 5678", "副卡", "#ff9f0a", esim = true),
    )
    val g = ids[0].id; val p1 = ids[1].id; val q = ids[2].id; val o = ids[3].id; val p2 = ids[4].id
    val wx = Identity(newID(), "login", "atlas_wx", "", "#07c160", provider = "wechat", via = p1)
    val ap = Identity(newID(), "email", "me@icloud.com", "Apple ID", "#1d1d1f", apple = true)
    val w = wx.id; val ai = ap.id
    val day = 86_400_000.0; val now = nowMS()
    fun mk(n: String, d: String, c: String, i: List<String>, ago: Double, notes: String = "") =
      Account(newID(), n, d, c, i, notes, null, (now - ago * day - Random.nextDouble(0.0, 3e6)).let { Math.round(it).toDouble() })
    return AtlasData(ids + wx + ap, listOf(
      mk("GitHub", "github.com", "开发", listOf(o, p1), 0.0, "已开两步验证"), mk("Claude", "claude.ai", "AI", listOf(g), 0.0),
      mk("Cloudflare", "cloudflare.com", "开发", listOf(o), 1.0), mk("ChatGPT", "chatgpt.com", "AI", listOf(g), 1.0),
      mk("小红书", "xiaohongshu.com", "社交", listOf(w), 3.0), mk("Binance", "binance.com", "加密", listOf(o, p1), 3.0, "已完成 KYC"),
      mk("淘宝", "taobao.com", "购物", listOf(q, p1), 9.0), mk("京东", "jd.com", "购物", listOf(q, p2), 12.0),
      mk("微信", "weixin.qq.com", "社交", listOf(p1), 30.0), mk("支付宝", "alipay.com", "支付金融", listOf(p1), 30.0),
      mk("哔哩哔哩", "bilibili.com", "娱乐", listOf(p2), 40.0), mk("Steam", "steampowered.com", "游戏", listOf(q), 60.0, "令牌在主力手机上"),
      mk("Apple ID", "apple.com", "工具", listOf(g, p1), 90.0), mk("Notion", "notion.so", "工具", listOf(g), 120.0), mk("网易云音乐", "music.163.com", "娱乐", listOf(p2, ai), 150.0),
      mk("美团", "meituan.com", "出行", listOf(w), 6.0), mk("拼多多", "pinduoduo.com", "购物", listOf(w), 15.0),
    ), demo = true)
  }
}
