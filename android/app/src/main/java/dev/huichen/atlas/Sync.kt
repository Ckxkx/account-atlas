package dev.huichen.atlas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.AEADBadTagException

// Same encrypted file as the web app and iOS: PBKDF2-SHA256 (310k) -> AES-GCM, stored in a private gist (Gitee or GitHub)
// or, apps only, on a WebDAV server (坚果云 etc.) as <server>/AccountAtlas/atlas.enc.json.
// WebDAV: provider "webdav", url = server folder (ends with /), user = account, token = 应用密码; gistId stays null.
// url / user are optional so configs saved by older builds still decode.
@Serializable
data class SyncConf(
  val provider: String? = null,
  val token: String,
  val salt: String,
  val key: String,
  val gistId: String? = null,
  val at: Double? = null,
  val url: String? = null,
  val user: String? = null,
)

class SyncError(msg: String) : Exception(msg)

object Sync {
  const val FILE = "atlas.enc.json"
  const val DESC = "账号星图 · 加密数据（只有同步密码能解开）"
  var state by mutableStateOf("off")     // off | idle | syncing | error
  var err by mutableStateOf("")
  var at by mutableDoubleStateOf(0.0)
  private var conf: SyncConf? = null
  private var key: ByteArray? = null
  private var busy = false
  private var again = false
  private var timer: Job? = null
  private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

  val on get() = conf != null
  val provider get() = conf?.provider ?: "github"
  /** where the file lives, for the status line: 「存放在 …」 */
  val place get() = when (provider) {
    "gitee" -> "Gitee 私有 Gist"
    "webdav" -> davHost(conf?.url).let { h -> if ("jianguoyun" in h) "坚果云 WebDAV" else "WebDAV · $h" }
    else -> "GitHub 私有 Gist"
  }

  fun init() {
    val s = Store.prefs().getString("atlas.sync", null) ?: return
    val c = runCatching { JSON.decodeFromString(SyncConf.serializer(), s) }.getOrNull() ?: return
    conf = c; at = c.at ?: 0.0; state = "idle"
    key = runCatching { AtlasCrypto.unb64(c.key) }.getOrNull()
    if (key == null) { state = "error"; err = "本机的同步信息损坏，请重新开启" }
  }
  private fun persistConf() {
    val c = conf
    if (c != null) Store.prefs().edit().putString("atlas.sync", JSON.encodeToString(SyncConf.serializer(), c)).apply()
    else Store.prefs().edit().remove("atlas.sync").apply()
  }

  // MARK: api (blocking, call on IO)
  private fun api(path: String, method: String = "GET", body: JsonObject? = null, token: String, prov: String): JsonElement {
    val gitee = prov == "gitee"
    var url = (if (gitee) "https://gitee.com/api/v5" else "https://api.github.com") + path
    if (gitee && method == "GET") url += (if ("?" in url) "&" else "?") + "access_token=" + URLEncoder.encode(token, "UTF-8")
    val rb = Request.Builder().url(url).header("Cache-Control", "no-cache")
    val json = "application/json".toMediaType()
    if (gitee) {
      if (method != "GET") {
        val b = JsonObject((body ?: JsonObject(emptyMap())) + ("access_token" to JsonPrimitive(token)))
        rb.method(method, b.toString().toRequestBody(json))
      }
    } else {
      rb.header("Authorization", "Bearer $token").header("Accept", "application/vnd.github+json")
      if (body != null) rb.method(method, body.toString().toRequestBody(json))
    }
    val name = if (gitee) "Gitee" else "GitHub"
    val resp = try { http.newCall(rb.build()).execute() } catch (e: Exception) {
      throw SyncError("连不上 $name" + if (gitee) "" else "，国内网络建议改用 Gitee")
    }
    resp.use {
      val code = it.code
      if (code == 401) throw SyncError("令牌无效或已过期")
      if (code == 403 || code == 404) throw SyncError(if (gitee) "令牌没有 gists 权限" else "令牌没有 gist 权限")
      if (code !in 200..299) throw SyncError("$name 返回 $code")
      val t = it.body?.string() ?: ""
      return if (t.isBlank()) JsonNull else JSON.parseToJsonElement(t)
    }
  }
  private fun idOf(e: JsonElement?): String? = (e as? JsonPrimitive)?.contentOrNull
  private fun readGist(id: String, token: String, prov: String): String? {
    val g = api("/gists/$id", token = token, prov = prov) as? JsonObject ?: return null
    val f = (g["files"] as? JsonObject)?.get(FILE) as? JsonObject ?: return null
    val content = (f["content"] as? JsonPrimitive)?.contentOrNull
    val truncated = (f["truncated"] as? JsonPrimitive)?.booleanOrNull == true
    if (content != null && !truncated) return content
    val raw = (f["raw_url"] as? JsonPrimitive)?.contentOrNull ?: return null
    return http.newCall(Request.Builder().url(raw).build()).execute().use { it.body?.string() }
  }

  // MARK: WebDAV (blocking, call on IO)
  // GET / PUT with HTTP Basic auth; a strong ETag goes back as If-Match so two devices can't overwrite each other.
  // OkHttp sends any method name, so MKCOL needs no workaround.
  const val DAV_DIR = "AccountAtlas/"
  const val DAV_PRESET = "https://dav.jianguoyun.com/dav/"
  private class Remote(val text: String?, val etag: String? = null)

  fun davHost(url: String?): String = url?.toHttpUrlOrNull()?.host ?: "WebDAV"
  /** normalizes what the user typed: adds https:// when missing, always ends with / */
  fun davBase(raw: String): String {
    var s = raw.trim()
    if (s.isEmpty()) throw SyncError("请填写 WebDAV 服务器地址")
    if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "https://$s"
    if (!s.endsWith("/")) s += "/"
    s.toHttpUrlOrNull() ?: throw SyncError("服务器地址格式不对")
    return s
  }
  private fun davCall(rb: Request.Builder, user: String, pass: String): okhttp3.Response {
    rb.header("Authorization", Credentials.basic(user, pass, Charsets.UTF_8)).header("Cache-Control", "no-cache")
    return try { http.newCall(rb.build()).execute() } catch (e: Exception) { throw SyncError("连不上 WebDAV 服务器，检查服务器地址和网络") }
  }
  private fun davFail(code: Int): Nothing = throw SyncError(when (code) {
    401 -> "账号或应用密码不对（坚果云要用第三方应用密码，不是登录密码）"
    403 -> "WebDAV 拒绝访问（没有权限，或请求太频繁，稍后再试）"
    404, 409 -> "服务器地址不对，找不到这个目录"
    507 -> "WebDAV 空间已满"
    else -> "WebDAV 返回 $code"
  })
  /** null text = no file yet */
  private fun davRead(base: String, user: String, pass: String): Remote {
    davCall(Request.Builder().url(base + DAV_DIR + FILE), user, pass).use {
      if (it.code == 404) return Remote(null)
      if (it.code !in 200..299) davFail(it.code)
      return Remote(it.body?.string() ?: "", it.header("ETag"))
    }
  }
  /** MKCOL AccountAtlas/; 405 = it already exists */
  private fun davMkdir(base: String, user: String, pass: String) {
    davCall(Request.Builder().url(base + DAV_DIR).method("MKCOL", null), user, pass).use {
      if (it.code !in 200..299 && it.code != 405) davFail(it.code)
    }
  }
  /** false = 412, someone else wrote in between (read, merge and try again) */
  private fun davWrite(base: String, user: String, pass: String, content: String, etag: String?, mkdir: Boolean = true): Boolean {
    val rb = Request.Builder().url(base + DAV_DIR + FILE).put(content.toRequestBody("application/json; charset=utf-8".toMediaType()))
    // weak ETags (W/"…") never match If-Match, so only strong ones are sent back
    if (etag != null && !etag.startsWith("W/")) rb.header("If-Match", etag)
    davCall(rb, user, pass).use {
      if (it.code == 412) return false
      // folder missing (deleted on the server, or first write): create it once and retry
      if ((it.code == 404 || it.code == 409) && mkdir) { davMkdir(base, user, pass); return davWrite(base, user, pass, content, etag, false) }
      if (it.code !in 200..299) davFail(it.code)
      return true
    }
  }

  private fun readRemote(c: SyncConf): Remote =
    if (c.provider == "webdav") davRead(c.url ?: "", c.user ?: "", c.token)
    else Remote(readGist(c.gistId ?: "", c.token, c.provider ?: "github"))
  private fun writeRemote(c: SyncConf, content: String, etag: String?): Boolean {
    if (c.provider == "webdav") return davWrite(c.url ?: "", c.user ?: "", c.token, content, etag)
    api("/gists/${c.gistId}", "PATCH", filesBody(content), c.token, c.provider ?: "github")
    return true
  }

  // MARK: run
  fun schedule() {
    if (conf == null) return
    timer?.cancel()
    timer = Store.scope.launch { delay(1200); run() }
  }

  fun runNow() { Store.scope.launch { run() } }

  suspend fun run() {
    val c = conf ?: return; val k = key ?: return
    if (c.provider == "webdav") { if (c.url == null || c.user == null) return } else if (c.gistId == null) return
    if (busy) { again = true; return }
    busy = true; state = "syncing"
    try {
      // read-merge-write; on WebDAV a 412 (another device wrote in between) reads again, the 3rd try writes without If-Match
      var tries = 0
      while (true) {
        val r = withContext(Dispatchers.IO) { readRemote(c) }
        val text = r.text
        val remote = withContext(Dispatchers.Default) { text?.let { AtlasCrypto.openData(it, k) } ?: AtlasData() }
        // during the 操作演示 the screen shows sandbox data: sync only ever sees (and updates) the real data
        val local = Store.realData()
        val merged = Merge.merge(local, remote)
        if (Merge.sig(merged) != Merge.sig(local)) Store.adopt(merged)
        if (text == null || Merge.sig(merged) != Merge.sig(remote)) {
          val content = AtlasCrypto.sealData(merged, k, c.salt)
          val etag = if (tries < 2) r.etag else null
          if (!withContext(Dispatchers.IO) { writeRemote(c, content, etag) }) { tries++; continue }
        }
        break
      }
      state = "idle"; err = ""; at = nowMS(); conf = conf?.copy(at = at); persistConf()
    } catch (e: Exception) {
      state = "error"
      err = when (e) { is SyncError -> e.message ?: "同步失败"; is AEADBadTagException -> "同步密码不对"; is AtlasCrypto.BadFormat -> e.message!!; else -> "同步失败" }
    }
    busy = false
    if (again) { again = false; run() }
  }

  private fun filesBody(content: String, public: Boolean? = null) = buildJsonObject {
    put("description", DESC)
    if (public != null) put("public", public)
    put("files", buildJsonObject { put(FILE, buildJsonObject { put("content", content) }) })
  }

  /** returns true when existing data was found in the cloud */
  /** WebDAV: [raw] is the 应用密码, [url] / [user] the server and account */
  suspend fun enable(raw: String, pass: String, prov: String, url: String? = null, user: String? = null): Boolean {
    val token = raw.trim()
    val dav = prov == "webdav"
    val base = if (dav) davBase(url ?: "") else null
    val u = user?.trim() ?: ""
    if (dav && u.isEmpty()) throw SyncError("请填写 WebDAV 账号")
    var gid: String? = null
    val existing: String? = if (dav) withContext(Dispatchers.IO) { davRead(base!!, u, token) }.text else {
      val list = withContext(Dispatchers.IO) { api("/gists?per_page=100", token = token, prov = prov) } as? JsonArray ?: JsonArray(emptyList())
      val g = list.firstOrNull { ((it as? JsonObject)?.get("files") as? JsonObject)?.containsKey(FILE) == true } as? JsonObject
      val id = g?.let { idOf(it["id"]) }
      if (id != null) { gid = id; withContext(Dispatchers.IO) { readGist(id, token, prov) } ?: throw SyncError("云端的同步文件损坏") } else null
    }
    val salt: String; val k: ByteArray; var found = false
    if (existing != null) {
      val text = existing
      val s = AtlasCrypto.saltOf(text) ?: throw SyncError("云端的同步文件损坏")
      val sd = runCatching { AtlasCrypto.unb64(s) }.getOrNull() ?: throw SyncError("云端的同步文件损坏")
      salt = s
      k = withContext(Dispatchers.Default) { AtlasCrypto.derive(pass, sd) }
      try { AtlasCrypto.open(text, k) } catch (e: Exception) { throw SyncError("同步密码不对（和第一次设置的不一样）") }
      found = true
    } else {
      val b = AtlasCrypto.randomBytes(16)
      salt = AtlasCrypto.b64(b)
      k = withContext(Dispatchers.Default) { AtlasCrypto.derive(pass, b) }
    }
    // sample data stays on this device: it is taken out (without tombstones) before the first merge
    Store.realData().let { if (Demo.has(it)) Store.adopt(Demo.clear(it)) }
    if (dav) {
      // first device on this server: create AccountAtlas/ and put the file there now, so a bad address shows up here
      if (existing == null) withContext(Dispatchers.IO) {
        davMkdir(base!!, u, token)
        davWrite(base, u, token, AtlasCrypto.sealData(Store.data, k, salt), null)
      }
    } else if (gid == null) {
      val content = AtlasCrypto.sealData(Store.data, k, salt)
      val made = withContext(Dispatchers.IO) { api("/gists", "POST", filesBody(content, public = false), token, prov) } as? JsonObject
      gid = idOf(made?.get("id"))
    }
    conf = SyncConf(prov, token, salt, AtlasCrypto.b64(k), gid, 0.0, base, if (dav) u else null)
    key = k; persistConf(); state = "idle"
    run()
    return found
  }

  fun disable() { conf = null; key = null; state = "off"; err = ""; persistConf() }
}
