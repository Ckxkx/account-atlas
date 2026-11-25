package dev.huichen.atlas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// 2.5 操作演示: a hands-on tour on the real screens. A spotlight sits on the control to use and says what
// to tap; the step moves on when the user does it. It runs on sandbox data (one 示例邮箱), so nothing is
// saved or synced, and the real data is back the moment it ends. 「跳过」 is always there.

object Tour {
  /** add -> pick -> next -> who -> save -> map; null = off */
  var step by mutableStateOf<String?>(null)
  var offerDemo = false
  val rects = mutableStateMapOf<String, Rect>()
  var requestTab: ((Int) -> Unit)? = null
  val on get() = step != null
  val ORDER = listOf("add", "pick", "next", "who", "save", "map")

  fun start(firstRun: Boolean) {
    if (step != null) return
    offerDemo = firstRun
    Store.popAll()
    Store.beginSandbox()
    requestTab?.invoke(0)
    step = "add"
  }
  fun advance(from: String, to: String) { if (step == from) step = to }
  /** the sandbox app was saved: show it on the map */
  fun saved() { if (step == "save") { step = "map"; requestTab?.invoke(2) } }
  fun finish(loadDemo: Boolean = false) {
    if (step == null) return
    step = null
    Store.endSandbox()
    Store.prefs().edit().putBoolean("atlas.tourDone", true).apply()
    requestTab?.invoke(0)
    if (loadDemo) Store.loadDemo()
  }
}

/** marks a control the tour can point at */
fun Modifier.tourAnchor(id: String) = this.onGloballyPositioned { val r = it.boundsInRoot(); if (Tour.rects[id] != r) Tour.rects[id] = r }

private data class TourStep(val target: String?, val title: String, val body: String)
private fun stepOf(s: String) = when (s) {
  "add" -> TourStep("add", "点右上角的 +", "先添加一个 App 试试。这是演示，不会保存。")
  "pick" -> TourStep("tile", "点一个 App 选上它", "也可以在上面搜索，搜不到的会从 App Store 找。")
  "next" -> TourStep("next", "点「下一步」", "接下来选它怎么登录。")
  "who" -> TourStep("who", "点「示例邮箱」", "选这个 App 用哪个邮箱、手机号、微信或 QQ 登录，可以多选。")
  "save" -> TourStep("save", "点「保存」", "就记下来了。")
  else -> TourStep(null, "它已经连到关系图上了", "以后换手机号、注销邮箱前，来这里看一眼牵连了哪些 App。演示数据会在结束时清掉。")
}

/** first launch: start the 操作演示 once */
@Composable
fun FirstRunTour() {
  LaunchedEffect(Unit) {
    val prefs = Store.prefs()
    if (Store.firstRun && !prefs.getBoolean("atlas.tourDone", false) && !prefs.getBoolean("atlas.demoAsked", false)) {
      prefs.edit().putBoolean("atlas.demoAsked", true).apply()
      kotlinx.coroutines.delay(500)
      Tour.start(firstRun = true)
    }
  }
  TourOverlay()
}

@Composable
fun TourOverlay() {
  val step = Tour.step
  // the picker was closed half way: start again from the + button
  LaunchedEffect(step, Store.routes) {
    if (step in listOf("pick", "next", "who", "save") && Store.routes.none { it.route is Route.Picker }) Tour.step = "add"
  }
  AnimatedVisibility(step != null, enter = fadeIn(calm()), exit = fadeOut(calm())) {
    val s = stepOf(step ?: "map")
    val hole = s.target?.let { Tour.rects[it] }
    val pulse by rememberInfiniteTransition(label = "tour").animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse")
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val h = constraints.maxHeight.toFloat()
      // the dim layer has no pointer input: taps go straight through to the real control under the hole
      Canvas(Modifier.fillMaxSize()) {
        val pad = 8.dp.toPx()
        if (hole != null) {
          val r = Rect(hole.left - pad, hole.top - pad, hole.right + pad, hole.bottom + pad)
          val rr = RoundRect(r, CornerRadius(16.dp.toPx()))
          clipPath(Path().apply { addRoundRect(rr) }, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.58f)) }
          val g = (2 + 4 * pulse).dp.toPx()
          drawRoundRect(Color.White.copy(alpha = 0.9f - 0.5f * pulse), androidx.compose.ui.geometry.Offset(r.left - g / 2, r.top - g / 2),
            androidx.compose.ui.geometry.Size(r.width + g, r.height + g), CornerRadius(16.dp.toPx() + g / 2), style = Stroke(2.dp.toPx()))
        } else drawRect(Color.Black.copy(alpha = if (s.target == null) 0.45f else 0.58f))
      }
      val top = hole == null || hole.center.y > h * 0.5f
      val n = Tour.ORDER.indexOf(step).coerceAtLeast(0) + 1
      Column(Modifier.align(if (top) Alignment.TopCenter else Alignment.BottomCenter).fillMaxWidth()
        .then(if (top) Modifier.statusBarsPadding().padding(top = 56.dp) else Modifier.navigationBarsPadding().padding(bottom = 86.dp))
        .padding(horizontal = 18.dp).clip(RoundedCornerShape(18.dp)).background(if (pal.dark) hex("#2c2c2e") else Color.White)
        .clickable(remember { MutableInteractionSource() }, null) {}.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(if (s.target == null) Icons.Filled.Info else Icons.Filled.TouchApp, null, Modifier.size(20.dp), tint = pal.accent)
          Spacer(Modifier.width(8.dp))
          T(s.title, 17f, FontWeight.SemiBold, modifier = Modifier.weight(1f))
          T("$n / ${Tour.ORDER.size}", 13f, color = pal.secondary)
        }
        T(s.body, 14f, color = pal.secondary, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
          if (step == "map") {
            if (Tour.offerDemo && !Demo.hasReal(Store.realData()) && !Sync.on)
              T("载入示例看看", 15f, FontWeight.Medium, pal.accent, Modifier.press(0.95f) { Tour.finish(loadDemo = true) }.padding(horizontal = 12.dp, vertical = 8.dp))
            T("完成", 15f, FontWeight.SemiBold, Color.White, Modifier.press(0.95f) { Tour.finish() }.clip(CircleShape).background(pal.accent).padding(horizontal = 18.dp, vertical = 8.dp))
          } else {
            T("跳过演示", 15f, FontWeight.Medium, pal.secondary, Modifier.press(0.95f) { Tour.finish() }.padding(horizontal = 12.dp, vertical = 8.dp))
          }
        }
      }
    }
  }
}

/** over the wallet while sample data is on the device, so nobody wonders where it came from */
@Composable
fun DemoBanner(modifier: Modifier = Modifier) {
  AnimatedVisibility(Store.hasDemo && !Store.sandbox, modifier, enter = fadeIn(calm()), exit = fadeOut(calm())) {
    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Filled.Info, null, tint = pal.accent, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(10.dp))
      T("现在显示的有示例数据", 14f, FontWeight.Medium, modifier = Modifier.weight(1f))
      T("清除示例", 14f, FontWeight.SemiBold, pal.accent, Modifier.press(0.94f) { Store.clearDemo() }.padding(4.dp))
    }
  }
}
