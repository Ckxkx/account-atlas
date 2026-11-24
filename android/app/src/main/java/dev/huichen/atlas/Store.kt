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
  data class NewIdentity(val type: String) : Route()
  data class EditIdentity(val id: String) : Route()
  data class EditAccount(val id: String) : Route()
  data class Picker(val preset: String?) : Route()
  data object Settings : Route()
}

data class Entry(val key: Long, val route: Route)

data class Toast(val text: String, val action: String? = null, val id: Long = System.nanoTime())

/** App state. One instance, Compose reads the mutableState fields directly. */
object Store {
  private lateinit var prefs: SharedPreferences
  private lateinit var file: File
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  var data by mutableStateOf(AtlasData())
  var privacy by mutableStateOf(false)
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
  /** 新手教程 overlay (first launch, or 设置 › 使用教程); tourDemo = offer the sample data on its last page */
  var tour by mutableStateOf(false)
  var tourDemo = false

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
    privacy = prefs.getBoolean("atlas.privacy", false)
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
    activeID = deck.firstOrNull()?.id
    ready = true
  }
  fun prefs() = prefs

  fun putPrivacy(v: Boolean) { privacy = v; prefs.edit().putBoolean("atlas.privacy", v).apply() }
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
  /** The email / phone a login card is bound to; a dangling or odd reference counts as none. */
  fun viaOf(i: Identity): Identity? = if (!i.isLogin) null else i.via?.let { identity(it) }?.takeIf { !it.isLogin && it.id != i.id }
  /** 微信 / Apple cards bound to this email / phone. */
  fun loginsVia(id: String) = data.identities.filter { it.isLogin && it.via == id }
  /** Sites that log in with a 微信 / Apple card bound to this email / phone, minus the ones using it directly. */
  fun indirectAccounts(id: String): List<Pair<Identity, List<Account>>> {
    val direct = data.accounts.filter { id in it.identityIds }.map { it.id }.toSet()
    return loginsVia(id).map { l -> l to accountsOf(l.id).filter { it.id !in direct } }.filter { it.second.isNotEmpty() }
  }

  fun display(i: Identity): String {
    if (!privacy) return i.value
    if (i.isWechat) return i.value.take(2) + "•••"
    if (i.type == "email" || (i.isLogin && i.value.contains('@'))) {
      val parts = i.value.split("@", limit = 2)
      return parts[0].take(2) + "•••" + (if (parts.size > 1) "@" + parts[1] else "")
    }
    val digits = i.value.filter { it.isDigit() }
    return if (digits.length > 7) "••• •••• " + digits.takeLast(4) else "••••"
  }
  fun handleText(h: String) = if (privacy) h.take(1) + "•••" else h
  fun title(i: Identity) = i.label.ifEmpty { if (i.isLogin) providerName(i.provider) else display(i) }

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
    remember(); persist(); onSaved?.invoke()
  }
  fun persist() {
    runCatching {
      val tmp = File(file.parentFile, "atlas.json.tmp"); tmp.writeText(encodeAtlas(data)); tmp.renameTo(file)
    }
  }
  /** replace everything (sync merge), without new stamps */
  fun adopt(d: AtlasData) { data = d; remember(); persist(); fixActive() }
  private fun fixActive() { if (active == null || deck.none { it.id == activeID }) activeID = deck.firstOrNull()?.id ?: "add" }

  /** 小提示: at most once a week, the card that hasn't had a new site in two weeks or more */
  fun nudge() {
    val now = nowMS(); val day = 86_400_000.0
    if (firstRun || recovered || data.demo == true || now - prefs.getFloat("atlas.nudgeAtDays", 0f).toDouble() * day < 7 * day) return
    val demo = demoIds()
    var pick: Identity? = null; var oldest = Double.MAX_VALUE
    for (i in data.identities) {
      if (i.id in demo) continue
      val t = data.accounts.filter { i.id in it.identityIds }.maxOfOrNull { it.createdAt } ?: continue
      if (t < oldest) { oldest = t; pick = i }
    }
    val p = pick ?: return
    if (now - oldest < 14 * day) return
    prefs.edit().putFloat("atlas.nudgeAtDays", (now / day).toFloat()).apply()
    // never the full address or number: the user's own label, else a masked form
    val v = p.value; val at = v.indexOf('@')
    val masked = if (p.type == "phone") "尾号 ${v.filter { it.isDigit() }.takeLast(4)} 的手机号" else if (at > 0) "${v.take(1)}•••${v.substring(at)}" else "这个账号"
    val name = p.label.ifEmpty { masked }
    show("好久没给「$name」记录新账号啦，要不要来看看呢～", "去看看") { push(Route.IdentityR(p.id)) }
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
  fun importBackup(text: String): Boolean {
    val raw = runCatching { JSON.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject }.getOrNull() ?: return false
    if (raw["identities"] !is kotlinx.serialization.json.JsonArray || raw["accounts"] !is kotlinx.serialization.json.JsonArray) return false
    val b = runCatching { decodeAtlas(text) }.getOrNull() ?: return false
    mutate("已导入 ${b.identities.size} 个身份和 ${b.accounts.size} 个网站") { it.copy(identities = b.identities, accounts = b.accounts) }
    setKind(kind)
    return true
  }

  // sample data is opt-in; its ids stay on this device so 「清除示例数据」 removes only them
  private const val DEMO_KEY = "atlas.demoIds"
  fun demoIds(): Set<String> =
    if (data.demo == true) (data.identities.map { it.id } + data.accounts.map { it.id }).toSet()
    else prefs.getStringSet(DEMO_KEY, emptySet()) ?: emptySet()
  val hasDemo: Boolean get() { val d = demoIds(); return data.identities.any { it.id in d } || data.accounts.any { it.id in d } }

  fun loadDemo() {
    val d = demo()
    val fresh = data.identities.isEmpty() && data.accounts.isEmpty()
    prefs.edit().putStringSet(DEMO_KEY, (d.identities.map { it.id } + d.accounts.map { it.id }).toSet()).apply()
    mutate("已载入示例，可在「更多」里清除") { it.copy(identities = it.identities + d.identities, accounts = it.accounts + d.accounts) }
    if (fresh) { data = data.copy(demo = true); persist() }
    setKind("email")
  }
  fun clearDemo() {
    val d = demoIds()
    mutate("已清除示例数据") { s ->
      s.copy(
        identities = s.identities.filter { it.id !in d }.map { if (it.via != null && it.via in d) it.copy(via = null) else it },
        accounts = s.accounts.filter { it.id !in d }.map { a -> if (a.identityIds.any { it in d }) a.copy(identityIds = a.identityIds.filter { it !in d }) else a },
      )
    }
    setKind(kind)
  }
  /** CI screenshots start from the sample data */
  fun seedDemo() {
    if (!firstRun) return
    firstRun = false
    data = demo(); remember(); persist(); fixActive()
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
    val ap = Identity(newID(), "login", "me@icloud.com", "", "#1d1d1f", provider = "apple", via = g)
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
