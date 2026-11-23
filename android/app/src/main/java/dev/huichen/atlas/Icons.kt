package dev.huichen.atlas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Custom account icons (App Store artwork or a photo): square, 128 px, stored as a data URL in Account.icon. */
object CustomIcon {
  const val SIZE = 128
  private const val MAX_BYTES = 24 * 1024

  /** Decoded custom icons, keyed by account id + icon hash, so lists never decode base64 twice. */
  private val cache = ConcurrentHashMap<String, ImageBitmap>()
  fun key(id: String, icon: String) = "$id:${icon.length}:${icon.hashCode()}"
  fun cached(key: String): ImageBitmap? = cache[key]

  suspend fun decode(key: String, icon: String): ImageBitmap? = cache[key] ?: withContext(Dispatchers.Default) {
    runCatching {
      val comma = icon.indexOf(',')
      if (!icon.startsWith("data:image") || comma < 0) return@runCatching null
      val bytes = Base64.decode(icon.substring(comma + 1), Base64.DEFAULT)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.also { it.prepareToDraw(); cache[key] = it }
    }.getOrNull()
  }

  /** Center-crop to a square, scale to 128 px, PNG (or JPEG q85 if the PNG is over 24 KB). */
  fun encode(src: Bitmap): String {
    val s = minOf(src.width, src.height)
    var cur = Bitmap.createBitmap(src, (src.width - s) / 2, (src.height - s) / 2, s, s)
    // halve first for a smoother downscale
    while (cur.width / 2 >= SIZE * 2) cur = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
    val out = if (cur.width == SIZE) cur else Bitmap.createScaledBitmap(cur, SIZE, SIZE, true)
    val png = ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    if (png.size <= MAX_BYTES) return "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP)
    val flat = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    Canvas(flat).apply { drawColor(AColor.WHITE); drawBitmap(out, null, Rect(0, 0, SIZE, SIZE), Paint(Paint.FILTER_BITMAP_FLAG)) }
    val jpg = ByteArrayOutputStream().also { flat.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    return "data:image/jpeg;base64," + Base64.encodeToString(jpg, Base64.NO_WRAP)
  }

  fun fromBytes(bytes: ByteArray): String? {
    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
    if (b.outWidth <= 0 || b.outHeight <= 0) return null
    val o = BitmapFactory.Options().apply { inSampleSize = sample(b.outWidth, b.outHeight) }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)?.let { encode(it) }
  }

  private fun sample(w: Int, h: Int): Int {
    var k = 1
    while (minOf(w, h) / (k * 2) >= SIZE * 2) k *= 2
    return k
  }

  /** A photo from the Photo Picker. Runs off the main thread. */
  suspend fun fromUri(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
      val cr = ctx.contentResolver
      val bmp = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, uri)) { dec, info, _ ->
          dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
          dec.setTargetSampleSize(sample(info.size.width, info.size.height))
        }
      } else {
        val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
        return@runCatching fromBytes(bytes)
      }
      encode(bmp)
    }.getOrNull()
  }
}

data class SiteHit(val label: String, val domain: String)

/** 不知道网址: official website (Wikidata P856) for a name. Free, no key; unreachable in some regions -> empty. */
object SiteLookup {
  private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
  private fun get(params: String): kotlinx.serialization.json.JsonObject? = runCatching {
    http.newCall(Request.Builder().url("https://www.wikidata.org/w/api.php?format=json&$params")
      .header("User-Agent", "AccountAtlas/2 (https://github.com/Ckxkx/account-atlas)").build()).execute().use { r ->
      if (!r.isSuccessful) null else JSON.parseToJsonElement(r.body?.string().orEmpty()) as? kotlinx.serialization.json.JsonObject
    }
  }.getOrNull()

  suspend fun find(q: String): List<SiteHit> = withContext(Dispatchers.IO) {
    val enc = java.net.URLEncoder.encode(q.trim(), "UTF-8")
    val hits = (get("action=wbsearchentities&type=item&language=zh&uselang=zh&limit=6&search=$enc")?.get("search") as? kotlinx.serialization.json.JsonArray) ?: return@withContext emptyList()
    val out = ArrayList<SiteHit>()
    for (h in hits.take(3)) {
      val o = h as? kotlinx.serialization.json.JsonObject ?: continue
      val id = (o["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: continue
      val cl = ((get("action=wbgetclaims&property=P856&entity=$id")?.get("claims") as? kotlinx.serialization.json.JsonObject)?.get("P856") as? kotlinx.serialization.json.JsonArray)
        ?.mapNotNull { it as? kotlinx.serialization.json.JsonObject } ?: continue
      val best = cl.firstOrNull { (it["rank"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "preferred" } ?: cl.firstOrNull()
      val v = (((best?.get("mainsnak") as? kotlinx.serialization.json.JsonObject)?.get("datavalue") as? kotlinx.serialization.json.JsonObject)?.get("value") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
      val d = cleanDomain(v)
      if (d.isNotEmpty() && out.none { it.domain == d }) out += SiteHit((o["label"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: q, d)
    }
    out
  }
}

/** Free iTunes Search API, no key. Fails silently (offline -> empty). */
object AppStore {
  private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()

  suspend fun search(term: String): List<AppHit> {
    if (term.isBlank()) return emptyList()
    return withContext(Dispatchers.IO) {
      runCatching {
        http.newCall(Request.Builder().url(appStoreURL(term)).build()).execute().use { r ->
          if (!r.isSuccessful) emptyList() else parseAppStore(r.body?.string().orEmpty())
        }
      }.getOrDefault(emptyList())
    }
  }

  /** Download the artwork and turn it into a 128 px data URL. */
  suspend fun icon(hit: AppHit): String? {
    val url = hit.artwork ?: return null
    return withContext(Dispatchers.IO) {
      runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
          if (!r.isSuccessful) null else r.body?.bytes()?.let { CustomIcon.fromBytes(it) }
        }
      }.getOrNull()
    }
  }

  /** Small thumbnails for the suggestion lists (artworkUrl100). */
  private val thumbs = ConcurrentHashMap<String, ImageBitmap>()
  fun thumbCached(url: String): ImageBitmap? = thumbs[url]
  suspend fun thumb(url: String): ImageBitmap? = thumbs[url] ?: withContext(Dispatchers.IO) {
    runCatching {
      http.newCall(Request.Builder().url(url).build()).execute().use { r ->
        if (!r.isSuccessful) null else r.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
      }
    }.getOrNull()?.also { thumbs[url] = it }
  }
}
