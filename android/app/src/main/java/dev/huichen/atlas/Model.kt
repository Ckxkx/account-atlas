package dev.huichen.atlas

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToLong

// Same JSON shape as the web app and the iOS app, so backups and sync interoperate.
// Pure Kotlin (no Android imports) so it can be unit tested on the JVM.

/** Millisecond timestamps: written as plain integers when whole (like JS / Swift do), never 1.7E12. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object MsSerializer : KSerializer<Double> {
  override val descriptor = PrimitiveSerialDescriptor("atlas.ms", PrimitiveKind.DOUBLE)
  override fun serialize(encoder: Encoder, value: Double) {
    val e = encoder as? JsonEncoder
    if (e == null) { encoder.encodeDouble(value); return }
    if (value == Math.floor(value) && !value.isInfinite() && kotlin.math.abs(value) < 9e15) e.encodeJsonElement(JsonPrimitive(value.toLong()))
    else e.encodeJsonElement(JsonUnquotedLiteral(java.math.BigDecimal.valueOf(value).toPlainString()))
  }
  override fun deserialize(decoder: Decoder): Double {
    val d = decoder as? JsonDecoder ?: return decoder.decodeDouble()
    return d.decodeJsonElement().jsonPrimitive.double
  }
}

@Serializable
data class Identity(
  val id: String,
  val type: String = "email",          // "email" | "phone" | "login"
  val value: String = "",
  val label: String = "",
  val color: String = "#0a84ff",
  val notes: String = "",
  @Serializable(with = MsSerializer::class) val updatedAt: Double? = null,
  @Serializable(with = MsSerializer::class) val order: Double? = null,
  /** Phone only: an eSIM (no physical card). Written only when true; key omitted otherwise. */
  val esim: Boolean? = null,
  /** Login only: "wechat" | "qq" ("apple" only in data older than 2.4, converted on load). Key omitted for email / phone. */
  val provider: String? = null,
  /** Login only: id of the email / phone identity this login is bound to. Key omitted when empty. */
  val via: String? = null,
  /** Email / phone: this address or number is also an Apple ID (2.4). Written only when true. */
  val apple: Boolean? = null,
  /** 2.5, login only: every email / phone it is bound to when there is more than one (via stays the first,
   *  for older clients). Read as the union of via and vias. Key omitted when there is at most one. */
  val vias: List<String>? = null,
  /** 2.5: part of the sample data (「清除示例数据」 removes exactly these). Written only when true. */
  val demo: Boolean? = null,
) {
  /** 2.5: every email / phone id this 微信 / QQ is bound to, via first (union of via and vias, no repeats) */
  val bindIds: List<String> get() {
    val out = ArrayList<String>()
    via?.takeIf { it.isNotEmpty() }?.let { out += it }
    vias?.forEach { if (it.isNotEmpty() && it !in out) out += it }
    return out
  }
  /** the same identity bound to exactly these ids: via = the first, vias only when there are two or more */
  fun withBinds(ids: List<String>): Identity {
    val u = ids.filter { it.isNotEmpty() && it != id }.distinct()
    return copy(via = u.firstOrNull(), vias = if (u.size > 1) u else null)
  }
  val isDemo get() = demo == true
  val isPhone get() = type == "phone"
  val isESim get() = isPhone && esim == true
  val isLogin get() = type == "login"
  /** a pre-2.4 Apple login card (converted to its email / phone on load) */
  val isApple get() = isLogin && provider == "apple"
  /** 2.4: an email / phone that is also an Apple ID */
  val isAppleID get() = !isLogin && apple == true
  val isQQ get() = isLogin && provider == "qq"
  val isWechat get() = isLogin && !isApple && !isQQ
}

// MARK: 2.4 — one identity per email / phone; Apple ID is a flag on it (mirrors app.js / Model.swift)

fun normEmail(v: String) = v.trim().lowercase()
fun normPhone(v: String): String {
  var d = v.filter { it in '0'..'9' }
  if (d.startsWith("0086")) d = d.drop(4) else if (d.startsWith("86") && d.length == 13) d = d.drop(2)
  return d
}
/** The key two identities share when they are the same thing; null = never merged. */
fun idKey(type: String, provider: String?, value: String): String? {
  val v = value.trim()
  if (v.isEmpty()) return null
  return when (type) {
    "email" -> "e:" + normEmail(v)
    "phone" -> normPhone(v).takeIf { it.isNotEmpty() }?.let { "p:$it" }
    "login" -> when (provider) {
      "apple" -> null
      "qq" -> "q:" + (if (v.contains('@')) normEmail(v) else v.filter { it in '0'..'9' })
      else -> "w:" + v.lowercase()
    }
    else -> null
  }
}
fun idKey(i: Identity) = idKey(i.type, i.provider, i.value)

object Migrate24 {
  /** Silent, lossless fix-ups (ids kept): an Apple login card becomes the email / phone it is, flagged
   *  apple (its via stays in the JSON, unused); a QQ card whose QQ is an email you already have points at
   *  that email. Returns the same instance when nothing changed. */
  fun autoFix(d: AtlasData): AtlasData {
    var changed = false
    var ids = d.identities.map { i ->
      if (i.type == "login" && i.provider == "apple") { changed = true; i.copy(type = if (i.value.contains('@')) "email" else "phone", provider = null, apple = true) } else i
    }
    ids = ids.map { i ->
      if (i.type == "login" && i.provider == "qq" && i.via.isNullOrEmpty() && i.value.contains('@')) {
        val m = ids.firstOrNull { it.type == "email" && normEmail(it.value) == normEmail(i.value) }
        if (m != null) { changed = true; i.copy(via = m.id) } else i
      } else i
    }
    return if (changed) d.copy(identities = ids) else d
  }
  /** Groups of identity ids that are the same email / phone / 微信 / QQ; the first (oldest) is kept. */
  fun groups(d: AtlasData): List<List<String>> {
    val by = LinkedHashMap<String, MutableList<String>>()
    for (i in d.identities) { val k = idKey(i) ?: continue; by.getOrPut(k) { ArrayList() }.add(i.id) }
    return by.values.filter { it.size > 1 }
  }
  fun dupCount(d: AtlasData) = groups(d).sumOf { it.size - 1 }
  /** Merges every group into its first identity: label, notes and flags carry over, every account and
   *  binding is repointed. Removed ids become tombstones when Store.save() stamps the change. */
  fun merge(d: AtlasData): Pair<AtlasData, Int> {
    val to = HashMap<String, String>()
    val byId = d.identities.associateBy { it.id }.toMutableMap()
    for (g in groups(d)) {
      var keep = byId[g[0]] ?: continue
      for (oid in g.drop(1)) {
        val o = byId[oid] ?: continue
        to[oid] = keep.id
        if (keep.label.isEmpty() && o.label.isNotEmpty()) keep = keep.copy(label = o.label)
        if (o.notes.isNotEmpty() && !keep.notes.contains(o.notes)) keep = keep.copy(notes = if (keep.notes.isEmpty()) o.notes else keep.notes + "\n" + o.notes)
        if (o.apple == true) keep = keep.copy(apple = true)
        if (o.esim == true && keep.type == "phone") keep = keep.copy(esim = true)
        if (o.bindIds.any { it != keep.id && it !in keep.bindIds }) keep = keep.withBinds(keep.bindIds + o.bindIds)
        if (keep.order == null && o.order != null) keep = keep.copy(order = o.order)
        if (keep.demo == true && o.demo != true) keep = keep.copy(demo = null)   // merged with a real one: real
      }
      byId[keep.id] = keep
    }
    if (to.isEmpty()) return d to 0
    val ids = d.identities.filter { it.id !in to }.map { byId[it.id] ?: it }.map { i ->
      if (i.bindIds.none { it in to }) i else i.withBinds(i.bindIds.map { to[it] ?: it })
    }
    val accs = d.accounts.map { a ->
      val out = ArrayList<String>()
      for (x in a.identityIds) { val y = to[x] ?: x; if (y !in out) out.add(y) }
      if (out == a.identityIds) a else a.copy(identityIds = out)
    }
    return d.copy(identities = ids, accounts = accs) to to.size
  }
}

/** A 微信 / QQ card's binding without the full number: 尾号 1234, or ab•••@qq.com */
fun bindTag(v: Identity): String {
  if (v.type == "phone") { val d = v.value.filter { it.isDigit() }; return if (d.length >= 4) "尾号 " + d.takeLast(4) else "" }
  val parts = v.value.split("@", limit = 2)
  return parts[0].take(2) + "•••" + (if (parts.size > 1) "@" + parts[1] else "")
}
/** 2.5 map / picker tag of every binding: the first one, then "+1" for each more */
fun bindTags(binds: List<Identity>): String {
  val first = binds.firstOrNull()?.let { bindTag(it) }.orEmpty()
  if (first.isEmpty()) return ""
  return if (binds.size > 1) first + " +" + (binds.size - 1) else first
}

// MARK: 2.5 masking (level 1: values; level 2: labels / usernames) — mirrors app.js / Model.swift

/** The value with most of it hidden: ab•••@gmail.com, ••• •••• 1234, wx•••. */
fun maskValue(i: Identity): String {
  val v = i.value
  if (i.type == "email" || (i.isLogin && v.contains('@'))) {
    val parts = v.split("@", limit = 2)
    return parts[0].take(2) + "•••" + (if (parts.size > 1) "@" + parts[1] else "")
  }
  if (i.isLogin && !i.isQQ && !i.isApple) return v.take(2) + "•••"
  val digits = v.filter { it.isDigit() }
  return if (digits.length > 7) "••• •••• " + digits.takeLast(4) else if (digits.length >= 4) "•••" + digits.takeLast(2) else "••••"
}
/** A label or username with all but its first character hidden. */
fun maskName(s: String): String = if (s.isEmpty()) s else s.take(1) + "•••"
/** what kind of identity this is, in words */
fun kindName(i: Identity): String = if (i.isLogin) providerName(i.provider) else if (i.isPhone) "手机号" else "邮箱"

// MARK: 2.5 sample data: marked per item, added next to real data, removed by marker only

object Demo {
  fun ids(d: AtlasData): Set<String> = (d.identities.filter { it.isDemo }.map { it.id } + d.accounts.filter { it.demo == true }.map { it.id }).toSet()
  fun has(d: AtlasData) = d.identities.any { it.isDemo } || d.accounts.any { it.demo == true }
  /** anything the user made themselves */
  fun hasReal(d: AtlasData) = d.identities.any { !it.isDemo } || d.accounts.any { it.demo != true }
  /** Older builds kept the sample ids in a device list (and a whole-file flag). Mark exactly the items in
   *  that list; nothing else is ever treated as sample data. Same instance when nothing changed. */
  fun adoptLegacy(d: AtlasData, list: Set<String>): AtlasData {
    if (list.isEmpty() && d.demo == null) return d
    var changed = d.demo != null
    val ids = d.identities.map { if (it.id in list && !it.isDemo) { changed = true; it.copy(demo = true) } else it }
    val accs = d.accounts.map { if (it.id in list && it.demo != true) { changed = true; it.copy(demo = true) } else it }
    return if (changed) d.copy(identities = ids, accounts = accs, demo = null) else d
  }
  /** Adds the sample next to what is there; never replaces or edits a real item. */
  fun add(d: AtlasData, sample: AtlasData): AtlasData {
    val have = (d.identities.map { it.id } + d.accounts.map { it.id }).toSet()
    return d.copy(
      identities = d.identities + sample.identities.filter { it.id !in have }.map { it.copy(demo = true) },
      accounts = d.accounts + sample.accounts.filter { it.id !in have }.map { it.copy(demo = true) },
      demo = null,
    )
  }
  /** Removes only marked items; real items just lose their links to them. */
  fun clear(d: AtlasData): AtlasData {
    val gone = ids(d)
    if (gone.isEmpty()) return d
    return d.copy(
      identities = d.identities.filter { it.id !in gone }.map { if (it.bindIds.any { b -> b in gone }) it.withBinds(it.bindIds.filter { b -> b !in gone }) else it },
      accounts = d.accounts.filter { it.id !in gone }.map { a -> if (a.identityIds.any { it in gone }) a.copy(identityIds = a.identityIds.filter { it !in gone }) else a },
      demo = null,
    )
  }
}

/** What's wrong with a value for this kind ("email" | "phone" | "wechat" | "qq"), or null when it's fine. */
fun idValueError(kind: String, value: String): String? {
  val v = value.trim()
  return when (kind) {
    "email" -> if (Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""").matches(v)) null else "邮箱地址格式不对"
    "phone" -> if (v.count { it.isDigit() } >= 5) null else "手机号至少要 5 位数字"
    "qq" -> if (validLogin("qq", v)) null else "QQ 号是 5 到 12 位数字，或填 QQ 邮箱"
    else -> if (v.isEmpty()) "填一下微信号" else null
  }
}

/** Identity kinds in wallet order. */
val KINDS = listOf("email", "phone", "login")
fun kindRank(t: String) = KINDS.indexOf(t).let { if (it < 0) KINDS.size else it }

fun providerName(p: String?) = when (p) { "apple" -> "Apple 账户"; "qq" -> "QQ"; else -> "微信" }   // apple: pre-2.4 data only
fun providerColor(p: String?) = when (p) { "apple" -> "#1d1d1f"; "qq" -> "#12b7f5"; else -> "#07c160" }

/** 微信号: at least one char. QQ: a 5–12 digit number or an email. Apple ID: an email, or a phone number with >= 5 digits. */
fun validLogin(provider: String?, value: String): Boolean {
  val v = value.trim()
  if (v.isEmpty()) return false
  if (provider == "qq") {
    val d = v.filter { it != ' ' }
    return (d.length in 5..12 && d.all { it.isDigit() }) || Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""").matches(v)
  }
  if (provider != "apple") return true
  if (Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""").matches(v)) return true
  return !v.contains('@') && v.count { it.isDigit() } >= 5 && v.all { it.isDigit() || it in " +-()" }
}

@Serializable
data class Account(
  val id: String,
  val name: String = "?",
  val domain: String = "",
  val category: String = "其他",
  val identityIds: List<String> = emptyList(),
  val notes: String = "",
  val handle: String? = null,
  @Serializable(with = MsSerializer::class) val createdAt: Double = nowMS(),
  @Serializable(with = MsSerializer::class) val updatedAt: Double? = null,
  /** Custom / App Store icon: data:image/png|jpeg;base64 URL of a square image (<= 128 px). Omitted when absent. */
  val icon: String? = null,
  /** 2.5: part of the sample data. Written only when true. */
  val demo: Boolean? = null,
)

@Serializable
data class AtlasData(
  val identities: List<Identity> = emptyList(),
  val accounts: List<Account> = emptyList(),
  val deleted: Map<String, @Serializable(with = MsSerializer::class) Double> = emptyMap(),
  val demo: Boolean? = null,
)

val JSON = Json {
  ignoreUnknownKeys = true
  coerceInputValues = true
  explicitNulls = false
  encodeDefaults = true
  isLenient = true
}

/** Whole-file decode first; if one damaged entry breaks it, decode entry by entry and skip only that one. */
private fun decodeLossy(text: String): AtlasData = runCatching { JSON.decodeFromString(AtlasData.serializer(), text) }.getOrElse { e ->
  val o = runCatching { JSON.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject }.getOrNull() ?: throw e
  fun <T> list(key: String, ser: KSerializer<T>): List<T> =
    (o[key] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { el -> runCatching { JSON.decodeFromJsonElement(ser, el) }.getOrNull() } ?: emptyList()
  AtlasData(
    identities = list("identities", Identity.serializer()),
    accounts = list("accounts", Account.serializer()),
    deleted = runCatching { JSON.decodeFromJsonElement(AtlasData.serializer(), kotlinx.serialization.json.JsonObject(mapOf("deleted" to (o["deleted"] ?: kotlinx.serialization.json.JsonObject(emptyMap()))))).deleted }.getOrDefault(emptyMap()),
    demo = (o["demo"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull(),
  )
}

fun decodeAtlas(text: String): AtlasData {
  val d = decodeLossy(text)
  return d.copy(identities = d.identities.map { i ->
    var x = i
    if (x.esim == false) x = x.copy(esim = null)
    if (x.apple == false) x = x.copy(apple = null)
    if (x.provider != null && x.provider!!.isBlank()) x = x.copy(provider = null)
    if (x.via != null && x.via!!.isBlank()) x = x.copy(via = null)
    if (x.vias != null && x.vias!!.none { it.isNotBlank() }) x = x.copy(vias = null)
    if (x.demo == false) x = x.copy(demo = null)
    x
  }, accounts = d.accounts.map { a ->
    var x = a
    if (x.demo == false) x = x.copy(demo = null)
    if (x.handle != null && x.handle!!.isEmpty()) x = x.copy(handle = null)
    if (x.icon != null && x.icon!!.isBlank()) x = x.copy(icon = null)
    x
  })
}
fun encodeAtlas(d: AtlasData): String = JSON.encodeToString(AtlasData.serializer(), d)

fun newID(): String {
  val a = "abcdefghijklmnopqrstuvwxyz0123456789"
  val r = (0 until 8).map { a.random() }.joinToString("")
  val t = System.currentTimeMillis().toString(36)
  return r + t.takeLast(4)
}
fun nowMS(): Double = System.currentTimeMillis().toDouble()

val CATS = listOf("社交", "购物", "支付金融", "开发", "AI", "娱乐", "游戏", "出行", "工具", "加密", "其他")

data class Preset(val name: String, val domain: String, val category: String, val color: String, val aliases: String = "")

/** search match: name or domain contains the (lowercased) query, or one of the aliases starts with it */
fun siteHit(p: Preset, q: String): Boolean =
  p.name.lowercase().contains(q) || (p.domain.isNotEmpty() && p.domain.contains(q)) || (p.aliases.isNotEmpty() && (" " + p.aliases).contains(" $q"))

/** category of a hand-added app: from the presets, else the site directory, else 其他 */
fun categoryFor(name: String, domain: String): String {
  val d = cleanDomain(domain)
  return presetFor(name, domain)?.category
    ?: Presets.directory.firstOrNull { (d.isNotEmpty() && it.domain == d) || it.name.lowercase() == name.lowercase() }?.category ?: "其他"
}

/** An app the user added by hand (or has an account for) and its own icon, if any. */
data class CustomApp(val preset: Preset, val icon: String?)

/**
 * The picker's "我添加的" list: the saved hand-added apps (atlas.custom rows [name, domain, category, color, icon]),
 * then apps that have accounts, newest first. Presets and names the user removed (atlas.custom.hidden) are left out.
 */
fun mergeCustomApps(saved: List<List<String>>, accounts: List<Account>, hidden: Set<String>, isPreset: (String) -> Boolean): List<CustomApp> {
  val out = ArrayList<CustomApp>(); val seen = HashSet<String>()
  val rows = saved.map { it to it.getOrNull(4) } + accounts.reversed().map { listOf(it.name, it.domain, it.category) to it.icon }
  for ((t, icon) in rows) {
    val n = t.getOrNull(0)?.trim().orEmpty(); val k = n.lowercase()
    if (k.isEmpty() || k in seen || k in hidden || isPreset(n)) continue
    seen += k
    out += CustomApp(Preset(n, t.getOrElse(1) { "" }, t.getOrNull(2)?.ifEmpty { null } ?: "其他", ""), icon?.takeIf { it.startsWith("data:image") })
  }
  return out
}

fun cleanDomain(d: String): String {
  var s = d.trim().lowercase()
  for (p in listOf("https://", "http://")) if (s.startsWith(p)) s = s.removePrefix(p)
  if (s.startsWith("www.")) s = s.removePrefix("www.")
  return s.split('/', '?', '#').firstOrNull { it.isNotEmpty() } ?: ""
}

fun presetFor(name: String, domain: String): Preset? = presetMatch(name, domain, byName = true)

/** The built-in app an account is. Exact domain first, then (byName) the name, then a parent domain,
 *  the longest one, and only when the names agree too: 王者荣耀 on pvp.qq.com is not QQ, and
 *  企业微信 on work.weixin.qq.com is not 微信. */
fun presetMatch(name: String, domain: String, byName: Boolean): Preset? {
  val d = cleanDomain(domain)
  fun norm(s: String) = s.lowercase().filter { !it.isWhitespace() }
  val n = norm(name)
  if (d.isNotEmpty()) Presets.all.firstOrNull { it.domain == d }?.let { return it }
  if (byName && n.isNotEmpty()) Presets.all.firstOrNull { norm(it.name) == n }?.let { return it }
  if (d.isEmpty() || n.isEmpty()) return null
  return Presets.all.filter { d.endsWith("." + it.domain) && n.contains(norm(it.name)) }.maxByOrNull { it.domain.length }
}

fun carrier(v: String): String {
  var d = v.filter { it.isDigit() }
  if (d.startsWith("0086")) d = d.drop(4) else if (d.startsWith("86") && d.length == 13) d = d.drop(2)
  if (d.length != 11 || !d.startsWith("1")) {
    val t = v.trim()
    if (t.startsWith("+")) { val cc = t.drop(1).takeWhile { it.isDigit() }.take(3); if (cc.isNotEmpty() && cc != "86") return "+$cc" }
    return ""
  }
  val p3 = d.take(3); val p4 = d.take(4)
  val mobile = setOf("134","135","136","137","138","139","147","148","150","151","152","157","158","159","172","178","182","183","184","187","188","195","197","198")
  val unicom = setOf("130","131","132","145","146","155","156","166","167","171","175","176","185","186","196")
  val telecom = setOf("133","149","153","173","174","177","180","181","189","190","191","193","199")
  if (p4 == "1349") return "中国电信"
  if (p3 in mobile) return "中国移动"
  if (p3 in unicom) return "中国联通"
  if (p3 in telecom) return "中国电信"
  if (p3 == "192") return "中国广电"
  return ""
}

// MARK: App Store search (itunes.apple.com/search) — mirrors app.js / the iOS app

data class AppHit(
  val trackName: String,
  val sellerUrl: String? = null,
  val primaryGenreName: String = "",
  val genres: List<String> = emptyList(),
  val genreIds: List<String> = emptyList(),
  val artworkUrl100: String? = null,
  val artworkUrl512: String? = null,
  val contentAdvisoryRating: String? = null,
  val trackContentRating: String? = null,
  val advisories: List<String> = emptyList(),
) {
  /** 18+ / sexual content: never shown in search (see [isAdultApp]) */
  val adult: Boolean get() = isAdultApp(trackName, primaryGenreName, genres, contentAdvisoryRating, trackContentRating, advisories)
  /** "支付宝-你的AI生活伙伴" -> "支付宝"; only when what is left before the dash is >= 2 chars. */
  val name: String get() = appStoreName(trackName)
  val domain: String get() = sellerUrl?.let { cleanDomain(it) } ?: ""
  val category: String get() = appStoreCategory(primaryGenreName, genres, genreIds)
  /** big artwork to download for the stored icon */
  val artwork: String? get() = artworkUrl512?.takeIf { it.isNotBlank() } ?: artworkUrl100?.replace("100x100", "256x256")
}

fun appStoreURL(term: String): String =
  "https://itunes.apple.com/search?term=" + java.net.URLEncoder.encode(term.trim(), "UTF-8") + "&country=cn&entity=software&limit=10&explicit=No"

/** 2.5: adult apps never show up in App Store search. An 18+ rating, a frequent / intense (or graphic)
 *  sexual-content or nudity advisory, or an adult word in the name or genre. Mild advisories (common on
 *  social apps and games) are fine. Mirrors app.js / AppStore.swift. */
private val ADULT_WORDS = Regex("""\b(sex|sexy|porn|porno|nsfw|hentai|adult|xxx|nude|nudes|onlyfans|camgirl|hookup)\b|18\s*\+|18禁|性爱|色情|约炮|裸聊|福利姬|情色|黄片|一夜情|成年人|av女优|成人(?!教育|高考|自考|学历|英语|本科|大学|学习)""", RegexOption.IGNORE_CASE)
private val SEXUAL = Regex("""sexual|nudity|性内容|性相关|裸露|色情""", RegexOption.IGNORE_CASE)
private val STRONG = Regex("""\b(frequent|intense|graphic)\b|频繁|强烈|极端|大量""", RegexOption.IGNORE_CASE)
fun isAdultApp(name: String, genre: String = "", genres: List<String> = emptyList(), rating: String? = null, trackRating: String? = null, advisories: List<String> = emptyList()): Boolean {
  if (listOf(rating, trackRating).any { it?.trim()?.startsWith("18") == true }) return true
  if (advisories.any { SEXUAL.containsMatchIn(it) && STRONG.containsMatchIn(it) }) return true
  return ADULT_WORDS.containsMatchIn(name) || ADULT_WORDS.containsMatchIn(genre) || genres.any { ADULT_WORDS.containsMatchIn(it) }
}

fun appStoreName(raw: String): String {
  val t = raw.trim()
  val m = Regex("""\s*[-–—]""").find(t) ?: return t
  val head = t.substring(0, m.range.first).trim()
  return if (head.length >= 2) head else t
}

fun appStoreCategory(primary: String, genres: List<String>, ids: List<String> = emptyList()): String {
  val byName = mapOf(
    "Social Networking" to "社交", "社交" to "社交",
    "Shopping" to "购物", "购物" to "购物",
    "Finance" to "支付金融", "财务" to "支付金融",
    "Developer Tools" to "开发", "开发工具" to "开发",
    "Games" to "游戏", "游戏" to "游戏",
    "Entertainment" to "娱乐", "娱乐" to "娱乐", "Music" to "娱乐", "音乐" to "娱乐", "Video" to "娱乐",
    "Photo & Video" to "娱乐", "摄影与录像" to "娱乐",
    "Travel" to "出行", "旅游" to "出行", "旅行" to "出行", "Navigation" to "出行", "导航" to "出行",
  )
  val byId = mapOf("6005" to "社交", "6024" to "购物", "6015" to "支付金融", "6026" to "开发", "6014" to "游戏",
    "6016" to "娱乐", "6011" to "娱乐", "6008" to "娱乐", "6003" to "出行", "6010" to "出行")
  byName[primary.trim()]?.let { return it }
  for (g in genres) byName[g.trim()]?.let { return it }
  for (i in ids) byId[i.trim()]?.let { return it }
  return "工具"
}

fun parseAppStore(text: String): List<AppHit> = runCatching {
  val root = JSON.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject ?: return emptyList()
  val arr = root["results"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
  fun kotlinx.serialization.json.JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
  fun kotlinx.serialization.json.JsonObject.list(k: String) =
    (this[k] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
  arr.mapNotNull { e ->
    val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
    val n = o.str("trackName")?.trim().orEmpty()
    if (n.isEmpty()) null
    else AppHit(n, o.str("sellerUrl"), o.str("primaryGenreName").orEmpty(), o.list("genres"), o.list("genreIds"), o.str("artworkUrl100"), o.str("artworkUrl512"),
      o.str("contentAdvisoryRating"), o.str("trackContentRating"), o.list("advisories"))
  }.filter { !it.adult }
}.getOrDefault(emptyList())

/** Natural, locale-aware-ish name order (like localizedStandardCompare). */
val nameOrder: Comparator<String> = run {
  val c = java.text.Collator.getInstance(java.util.Locale.CHINA)
  Comparator { a, b -> c.compare(a, b) }
}

// MARK: merge (per item, newest wins, tombstones) — mirrors Sync.swift / app.js
object Merge {
  fun merge(a: AtlasData, b: AtlasData, now: Double = nowMS()): AtlasData {
    val del = HashMap(a.deleted)
    for ((id, t) in b.deleted) del[id] = maxOf(del[id] ?: 0.0, t)
    fun <T> pick(xs: List<T>, ys: List<T>, id: (T) -> String, up: (T) -> Double): List<T> {
      val order = ArrayList<String>(); val map = HashMap<String, T>()
      for (x in xs + ys) {
        val c = map[id(x)]
        if (c != null) { if (up(x) > up(c)) map[id(x)] = x } else { map[id(x)] = x; order.add(id(x)) }
      }
      return order.mapNotNull { map[it] }.filter { !((del[id(it)] ?: -1.0) >= up(it)) }
    }
    var ids = pick(a.identities, b.identities, { it.id }) { it.updatedAt ?: 0.0 }
    var accs = pick(a.accounts, b.accounts, { it.id }) { it.updatedAt ?: 0.0 }
    val live = ids.map { it.id }.toSet() + accs.map { it.id }.toSet()
    for (k in del.keys.toList()) if (k in live) del.remove(k)
    ids = ids.withIndex().sortedWith(compareBy({ it.value.order ?: 1e9 }, { it.index.toDouble() })).map { it.value }
    val idSet = ids.map { it.id }.toSet()
    accs = accs.map { it.copy(identityIds = it.identityIds.filter { x -> x in idSet }) }
    val old = now - 90 * 86_400_000.0
    return AtlasData(identities = ids, accounts = accs, deleted = del.filterValues { it >= old })
  }
  /** Signature used to decide whether anything changed (order-sensitive like the iOS one). */
  fun sig(d: AtlasData): String = encodeAtlas(AtlasData(d.identities, d.accounts, d.deleted.toSortedMap()))
}
