package dev.huichen.atlas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

// Card backgrounds (docs/card-backgrounds.md): a wallet card can wear a picture instead of its colour.
// identity.bg = an official picture (ships in the APK), identity.pic = the user's own picture by content id.
// The colour stays everywhere else (map, glow, small cards); only the big wallet card face changes.

/** An official background: the repo root backgrounds/<id>.jpg, packaged as assets/backgrounds/<id>.jpg (build.gradle.kts). */
data class OfficialBg(val id: String, val name: String) { val asset get() = "backgrounds/$id.jpg" }

object OfficialBackgrounds {
  /** Same ids, order and names as BGS in app.js and Backgrounds.swift. Ids are never reused. Empty until the pictures come. */
  val all: List<OfficialBg> = emptyList()
  fun byId(id: String?): OfficialBg? = id?.let { k -> all.firstOrNull { it.id == k } }
}

/** The user's own pictures: filesDir/pics/<id>.jpg, one file per picture however many cards wear it. */
object CardPics {
  const val RATIO = 1.586f
  const val WIDTH = 1080
  const val QUALITY = 82
  private var dir: File? = null
  private var app: Context? = null
  /** ids whose file is on this device. Compose state, so a card redraws when sync brings its picture down. */
  private val here = mutableStateMapOf<String, Boolean>()
  /** decoded pictures by key ("pic:<id>" / "bg:<id>"), so swiping the deck never decodes a JPEG twice */
  private val cache = ConcurrentHashMap<String, ImageBitmap>()

  /** At launch: learn which pictures are here and delete the ones no card points at any more. Not at delete time,
   *  so 撤销 still finds its picture; a picked-then-cancelled picture goes the same way. */
  fun init(ctx: Context, live: Set<String>) {
    if (dir != null) return   // once per process: an activity re-created later must not prune what 撤销 may bring back
    app = ctx.applicationContext
    val d = File(ctx.filesDir, "pics").also { dir = it }
    d.listFiles()?.forEach { f ->
      val id = f.name.removeSuffix(".jpg")
      if (!f.name.endsWith(".jpg") || id !in live) runCatching { f.delete() } else here[id] = true
    }
  }

  fun has(id: String?): Boolean = id != null && here[id] == true
  private fun file(id: String) = File(dir ?: File(app!!.filesDir, "pics"), "$id.jpg")
  /** the stored JPEG (for the WebDAV upload); null when it isn't here. Blocking: call on IO. */
  fun bytes(id: String): ByteArray? = if (!validPicId(id)) null else runCatching { file(id).takeIf { it.exists() }?.readBytes() }.getOrNull()

  /** Stores JPEG bytes under their content id and returns it. Bytes from the server are only kept when they decode. */
  suspend fun store(jpeg: ByteArray): String? {
    val id = picId(jpeg)
    val ok = withContext(Dispatchers.IO) {
      runCatching {
        val f = file(id)
        if (!f.exists()) {
          val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, o)
          if (o.outWidth <= 0) return@runCatching false
          f.parentFile?.mkdirs()
          val tmp = File(f.parentFile, "$id.tmp"); tmp.writeBytes(jpeg); tmp.renameTo(f)
        }
        true
      }.getOrDefault(false)
    }
    if (!ok) return null
    here[id] = true
    return id
  }

  /** What a card shows: its own picture when the file is here, else an official one this build knows, else
   *  null (the colour). Unknown / missing never errors. Reads Compose state. */
  fun keyFor(i: Identity): String? = when {
    has(i.pic) -> "pic:${i.pic}"
    OfficialBackgrounds.byId(i.bg) != null -> "bg:${i.bg}"
    else -> null
  }
  fun cached(key: String): ImageBitmap? = cache[key]
  suspend fun load(key: String): ImageBitmap? = cache[key] ?: withContext(Dispatchers.IO) {
    runCatching {
      val (kind, id) = key.split(':', limit = 2)
      val bmp = if (kind == "pic") file(id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        else OfficialBackgrounds.byId(id)?.let { b -> app?.assets?.open(b.asset)?.use { BitmapFactory.decodeStream(it) } }
      bmp?.asImageBitmap()?.also { it.prepareToDraw(); cache[key] = it }
    }.getOrNull()
  }

  /** A photo from the Photo Picker → center-cropped to the card ratio, 1080 px wide, JPEG 82, stored. Returns its id,
   *  null when the picture can't be opened. EXIF rotation is applied (ImageDecoder does it itself on 28+). */
  suspend fun fromUri(ctx: Context, uri: Uri): String? {
    val jpeg = withContext(Dispatchers.IO) {
      runCatching {
        val cr = ctx.contentResolver
        val src: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
          ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, uri)) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            dec.setTargetSampleSize(sample(info.size.width, info.size.height))
          }
        } else {
          val raw = cr.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
          val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          BitmapFactory.decodeByteArray(raw, 0, raw.size, b)
          if (b.outWidth <= 0 || b.outHeight <= 0) return@runCatching null
          val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample(b.outWidth, b.outHeight) }) ?: return@runCatching null
          val m = exifMatrix(runCatching { ExifInterface(raw.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL))
          if (m == null) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        encode(src)
      }.getOrNull()
    } ?: return null
    return store(jpeg)
  }

  /** the framework ExifInterface has no rotationDegrees: the 8 orientations as a matrix, null = upright */
  private fun exifMatrix(o: Int): Matrix? = when (o) {
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { setScale(-1f, 1f) }
    ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { setRotate(180f) }
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { setScale(1f, -1f) }
    ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply { setRotate(90f); postScale(-1f, 1f) }
    ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { setRotate(90f) }
    ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply { setRotate(-90f); postScale(-1f, 1f) }
    ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { setRotate(-90f) }
    else -> null
  }

  /** keep decoding cheap: halve while the picture stays at least twice the card size */
  private fun sample(w: Int, h: Int): Int {
    val th = (WIDTH / RATIO).roundToInt()
    var k = 1
    while (w / (k * 2) >= WIDTH && h / (k * 2) >= th) k *= 2
    return k
  }

  private fun encode(src: Bitmap): ByteArray {
    val w = src.width; val h = src.height
    require(w > 0 && h > 0)
    var sw = w.toFloat(); var sh = w / RATIO
    if (sh > h) { sh = h.toFloat(); sw = h * RATIO }
    val x = ((w - sw) / 2).roundToInt().coerceIn(0, w - 1); val y = ((h - sh) / 2).roundToInt().coerceIn(0, h - 1)
    val crop = Bitmap.createBitmap(src, x, y, sw.roundToInt().coerceIn(1, w - x), sh.roundToInt().coerceIn(1, h - y))
    val out = Bitmap.createScaledBitmap(crop, WIDTH, (WIDTH / RATIO).roundToInt(), true)
    return ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }.toByteArray()
  }
}
