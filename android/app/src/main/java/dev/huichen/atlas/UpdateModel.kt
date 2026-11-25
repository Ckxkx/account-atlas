package dev.huichen.atlas

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// 检查更新 / 软件更新: the pure parts (no Android, no network), unit-tested in UpdateCheckTest.

/** version numbers like 2.6 / 2.6.1 (an optional leading v). Anything else is not comparable. */
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

/** once a day at most; only a check that got a readable version.json counts (a failed one retries next launch) */
object UpdateRules {
  const val DAY = 24 * 60 * 60 * 1000L
  fun due(auto: Boolean, installed: String?, last: Long, now: Long): Boolean =
    auto && VersionCmp.isRelease(installed) && (now - last !in 0 until DAY)
  /** the "last checked" time to keep after a fetch */
  fun lastAfter(fetched: Boolean, last: Long, now: Long): Long = if (fetched) now else last
  /** the automatic popup: newer than installed and not skipped */
  fun popup(info: UpdateInfo, installed: String?, skipped: String?): Boolean =
    VersionCmp.isNewer(info.version, installed) && skipped != info.version
}

/** what the 软件更新 page shows */
sealed class UpdateScreen {
  data object Checking : UpdateScreen()
  data object Failed : UpdateScreen()
  /** up to date: the notes only when version.json is about this very version */
  data class Latest(val notes: List<String>) : UpdateScreen()
  /** a newer version (a skipped one too: the page always shows it) */
  data class Newer(val info: UpdateInfo) : UpdateScreen()
  /** CI test build: the latest release, whatever its number */
  data class TestBuild(val info: UpdateInfo) : UpdateScreen()
  companion object {
    fun of(info: UpdateInfo?, installed: String?): UpdateScreen = when {
      info == null -> Failed
      !VersionCmp.isRelease(installed) -> TestBuild(info)
      VersionCmp.isNewer(info.version, installed) -> Newer(info)
      else -> Latest(if (VersionCmp.compare(info.version, installed) == 0) info.notes else emptyList())
    }
  }
}
