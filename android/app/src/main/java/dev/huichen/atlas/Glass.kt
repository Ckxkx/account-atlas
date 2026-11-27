package dev.huichen.atlas

import android.graphics.ColorMatrixColorFilter
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

// Liquid Glass for the tab bar, the sheets and the alert dialogs.
//
//  - Android 13+ (API 33): the content behind is blurred, saturated, then run through an AGSL lens shader
//    that bends it inward near the edge (with a little colour split) and lights the rim, like iOS 26.
//  - Android 12 (API 31-32): the same blur + saturation, no lens.
//  - Older: the surfaces keep their previous translucent / solid look.
//
// How the backdrop works: the content behind (pages, or the whole shell for sheets) is recorded into a
// GraphicsLayer that is drawn as usual; the glass draws that same layer again, mapped into its own
// coordinates, into a second layer carrying the RenderEffect. Both are RenderNodes, so scrolling content
// behind the glass updates it without recomposition, and the blurred result is cached by the renderer.

object Glass {
  /** Real backdrop blur (RenderEffect). */
  val blur: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  /** AGSL lens edge (RuntimeShader). */
  val lens: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !lensBroken
  @Volatile internal var lensBroken = false
}

/** Something glass can sit on: content recorded into a layer once per draw. */
@Stable
class Backdrop internal constructor(internal val layer: GraphicsLayer) {
  internal var coords: LayoutCoordinates? = null
}

@Composable
fun rememberBackdrop(): Backdrop { val l = rememberGraphicsLayer(); return remember(l) { Backdrop(l) } }

/** Records this content as a [Backdrop] (and still draws it normally). */
fun Modifier.backdropSource(b: Backdrop): Modifier = this
  .onPlaced { b.coords = it }
  .drawWithContent { b.layer.record { this@drawWithContent.drawContent() }; drawLayer(b.layer) }

data class GlassStyle(
  val blur: Dp,
  val tint: Color,
  val edge: Dp,          // width of the bending band along the rim
  val refraction: Dp,    // how far content is pulled in at the very rim
  val aberration: Float, // colour split at the rim (0 = none)
  val highlight: Float,  // rim light strength
  val saturation: Float = 1.6f,
)

fun tabGlass(dark: Boolean) = GlassStyle(
  blur = 12.dp, tint = if (dark) Color(0x80202024) else Color(0x8CFFFFFF),
  edge = 18.dp, refraction = 12.dp, aberration = 0.14f, highlight = if (dark) 0.22f else 0.45f)

fun sheetGlass(dark: Boolean) = GlassStyle(
  blur = 34.dp, tint = if (dark) Color(0xD90B0B0D) else Color(0xD6F2F2F7),
  edge = 22.dp, refraction = 14.dp, aberration = 0.08f, highlight = if (dark) 0.16f else 0.35f)

/** The rim: a bright top-left edge fading out, then a fainter bottom-right one (light from above). */
fun glassRim(dark: Boolean, size: Size): Brush = Brush.linearGradient(
  0f to Color.White.copy(alpha = if (dark) 0.42f else 0.95f),
  0.35f to Color.White.copy(alpha = if (dark) 0.06f else 0.25f),
  0.65f to Color.White.copy(alpha = if (dark) 0.04f else 0.2f),
  1f to Color.White.copy(alpha = if (dark) 0.22f else 0.7f),
  start = Offset.Zero, end = Offset(size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f)),
)

/**
 * The glass itself, as a background filling [modifier]'s bounds and clipped to [shape].
 * [sources] are drawn in order as what is behind it; [over] draws on top of them before the blur
 * (e.g. a sheet's own dimming scrim). [track] reads whatever state moves the glass relative to its
 * sources, so it redraws while that animates.
 */
@Composable
fun GlassBackdrop(
  sources: List<Backdrop>, shape: Shape, style: GlassStyle, modifier: Modifier = Modifier,
  over: (DrawScope.() -> Unit)? = null, track: () -> Unit = {},
  /** 1 = draw the tint opaque and skip the live blur; between 0 and 1 the opaque tint fades over the glass.
   *  A sheet uses it while it slides: re-blurring a full-screen backdrop on every frame of the slide is
   *  what made sheets stutter on the way up. */
  solid: () -> Float = { 0f },
) {
  val glassLayer = rememberGraphicsLayer()
  val me = remember { arrayOfNulls<LayoutCoordinates>(1) }
  val dark = pal.dark
  Box(modifier
    .onPlaced { me[0] = it }
    .graphicsLayer { this.shape = shape; clip = true }
    .drawWithCache {
      val effect = if (Glass.blur) glassEffect(style, shape, size, layoutDirection, this) else null
      val outline = shape.createOutline(size, layoutDirection, this)
      val rim = glassRim(dark, size)
      val rimW = 1.5.dp.toPx()
      onDrawBehind {
        track()
        val mine = me[0]
        val sol = solid()
        if (sol >= 1f) {
          drawRect(style.tint.copy(alpha = 1f))
          drawOutline(outline, rim, style = Stroke(rimW))
          return@onDrawBehind
        }
        if (effect != null && mine != null && mine.isAttached) {
          glassLayer.renderEffect = effect
          glassLayer.record {
            for (s in sources) {
              val sc = s.coords ?: continue
              if (!sc.isAttached) continue
              val m = Matrix()
              mine.transformFrom(sc, m)
              withTransform({ transform(m) }) { drawLayer(s.layer) }
            }
            over?.invoke(this)
          }
          drawLayer(glassLayer)
        }
        drawRect(style.tint)
        if (sol > 0f) drawRect(style.tint.copy(alpha = sol))
        drawOutline(outline, rim, style = Stroke(rimW))
      }
    })
}

private fun corners(shape: Shape, size: Size, ld: LayoutDirection, d: Density): FloatArray {
  val s = shape as? CornerBasedShape ?: return floatArrayOf(0f, 0f, 0f, 0f)
  val ts = s.topStart.toPx(size, d); val te = s.topEnd.toPx(size, d)
  val be = s.bottomEnd.toPx(size, d); val bs = s.bottomStart.toPx(size, d)
  // tl, tr, br, bl
  return if (ld == LayoutDirection.Ltr) floatArrayOf(ts, te, be, bs) else floatArrayOf(te, ts, bs, be)
}

@RequiresApi(31)
private fun glassEffect(style: GlassStyle, shape: Shape, size: Size, ld: LayoutDirection, d: Density): androidx.compose.ui.graphics.RenderEffect {
  val r = with(d) { style.blur.toPx() }.coerceAtLeast(1f)
  var e = android.graphics.RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
  if (style.saturation != 1f) {
    val cm = android.graphics.ColorMatrix().apply { setSaturation(style.saturation) }
    e = android.graphics.RenderEffect.createChainEffect(android.graphics.RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(cm)), e)
  }
  if (Glass.lens && size.width > 1f && size.height > 1f) lensEffect(style, corners(shape, size, ld, d), size, d)?.let {
    e = android.graphics.RenderEffect.createChainEffect(it, e)
  }
  return e.asComposeRenderEffect()
}

@RequiresApi(33)
private fun lensEffect(style: GlassStyle, radii: FloatArray, size: Size, d: Density): android.graphics.RenderEffect? = try {
  val sh = RuntimeShader(LENS_AGSL)
  sh.setFloatUniform("uSize", size.width, size.height)
  sh.setFloatUniform("uRadii", radii[0], radii[1], radii[2], radii[3])
  sh.setFloatUniform("uEdge", with(d) { style.edge.toPx() }.coerceAtLeast(1f))
  sh.setFloatUniform("uStrength", with(d) { style.refraction.toPx() })
  sh.setFloatUniform("uAberr", style.aberration)
  sh.setFloatUniform("uRim", style.highlight)
  sh.setFloatUniform("uPx", d.density)
  android.graphics.RenderEffect.createRuntimeShaderEffect(sh, "content")
} catch (t: Throwable) {
  // a GPU / driver that rejects the shader just gets the plain frosted glass
  android.util.Log.w("AtlasGlass", "lens shader unavailable", t)
  Glass.lensBroken = true
  null
}

// Rounded-rect SDF lens: inside a band along the rim the sample point is pulled inward along the
// normal (stronger towards the rim), with red/blue pulled slightly differently, and the rim is lit
// from the top-left and (more faintly) the opposite side.
private const val LENS_AGSL = """
uniform shader content;
uniform float2 uSize;
uniform float4 uRadii;
uniform float uEdge;
uniform float uStrength;
uniform float uAberr;
uniform float uRim;
uniform float uPx;

float sdRound(float2 c) {
  float2 h = uSize * 0.5;
  float2 p = c - h;
  float r = p.x < 0.0 ? (p.y < 0.0 ? uRadii.x : uRadii.w) : (p.y < 0.0 ? uRadii.y : uRadii.z);
  r = min(r, min(h.x, h.y));
  float2 q = abs(p) - h + float2(r, r);
  return length(max(q, float2(0.0, 0.0))) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 c) {
  float d = -sdRound(c);
  if (d >= uEdge) { return content.eval(c); }
  float2 n = float2(sdRound(c + float2(1.0, 0.0)) - sdRound(c - float2(1.0, 0.0)),
                    sdRound(c + float2(0.0, 1.0)) - sdRound(c - float2(0.0, 1.0)));
  float nl = length(n);
  n = nl > 0.0001 ? n / nl : float2(0.0, 0.0);
  float t = clamp(1.0 - d / uEdge, 0.0, 1.0);
  float disp = uStrength * t * t;
  float2 lo = float2(0.5, 0.5);
  float2 hi = uSize - float2(0.5, 0.5);
  half4 col = content.eval(clamp(c - n * disp, lo, hi));
  if (uAberr > 0.0) {
    col.r = content.eval(clamp(c - n * (disp * (1.0 + uAberr)), lo, hi)).r;
    col.b = content.eval(clamp(c - n * (disp * (1.0 - uAberr)), lo, hi)).b;
  }
  float2 L = normalize(float2(-0.55, -0.85));
  float facing = dot(n, L);
  float lit = max(facing, 0.0) + 0.45 * max(-facing, 0.0);
  float band = 1.0 - smoothstep(0.0, 2.0 * uPx, d);
  float hl = uRim * (band * (0.3 + 0.7 * lit) + 0.4 * t * t * t * lit);
  half3 add = half3(hl, hl, hl);
  col.rgb = col.rgb + add * (half3(1.0, 1.0, 1.0) - col.rgb * 0.5);
  return col;
}
"""

/**
 * Material AlertDialog with a glass panel: on Android 12+ the screen behind the dialog window is
 * blurred (cross-window blur, when the device / battery saver allows it) and the panel turns
 * translucent with a lit rim; otherwise it is the usual solid dialog.
 */
@Composable
fun GlassAlertDialog(
  onDismissRequest: () -> Unit,
  confirmButton: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  dismissButton: (@Composable () -> Unit)? = null,
  title: (@Composable () -> Unit)? = null,
  text: (@Composable () -> Unit)? = null,
) {
  val dark = pal.dark
  var blurred by remember { mutableStateOf(false) }
  val base = MaterialTheme.colorScheme.surfaceContainerHigh
  val shape = RoundedCornerShape(28.dp)
  val rimMod = if (blurred) Modifier.drawWithCache {
    val o = shape.createOutline(size, layoutDirection, this); val b = glassRim(dark, size); val w = 1.dp.toPx()
    onDrawWithContent { drawContent(); drawOutline(o, b, style = Stroke(w)) }
  } else Modifier
  AlertDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = { DialogBlurBehind(dark) { blurred = it }; confirmButton() },
    modifier = modifier.then(rimMod),
    dismissButton = dismissButton,
    title = title,
    text = text,
    shape = shape,
    containerColor = if (blurred) base.copy(alpha = if (dark) 0.74f else 0.8f) else base,
  )
}

@Composable
private fun DialogBlurBehind(dark: Boolean, onBlur: (Boolean) -> Unit) {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
  val view = LocalView.current
  val radius = with(LocalDensity.current) { 26.dp.roundToPx() }
  DisposableEffect(view, dark) {
    val w = (view.parent as? DialogWindowProvider)?.window
    if (w == null) onDispose { } else {
      val dim0 = w.attributes.dimAmount
      w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
      w.attributes = w.attributes.also { it.blurBehindRadius = radius }
      val l = java.util.function.Consumer<Boolean> { on ->
        // with the blur on, less dimming is needed to lift the panel; without it keep the usual dim
        w.setDimAmount(if (on) (if (dark) 0.4f else 0.22f) else dim0)
        onBlur(on)
      }
      w.windowManager.addCrossWindowBlurEnabledListener(view.context.mainExecutor, l)
      onDispose { w.windowManager.removeCrossWindowBlurEnabledListener(l) }
    }
  }
}
