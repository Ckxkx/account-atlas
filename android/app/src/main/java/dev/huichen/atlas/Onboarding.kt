package dev.huichen.atlas

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// 新手教程: shown once on a fresh install (and from 设置 › 使用教程). Skippable on every page.
// Sample data is never loaded behind the user's back: the last page offers it as a labelled choice,
// and the wallet then says it's sample data with a one-tap 清除.

private data class TourPage(val icon: ImageVector, val tint: String, val title: String, val body: String, val tip: String)

private val TOUR = listOf(
  TourPage(Icons.Filled.Style, "#0a84ff", "欢迎使用账号星图",
    "记下每个网站和 App 是用哪个邮箱、哪个手机号注册的。换号、注销、找回密码时，一眼就知道要改哪些。",
    "不保存任何密码，数据只在你的手机上。"),
  TourPage(Icons.Filled.Apps, "#30d158", "第一步：添加 App",
    "点右上角「+」，选你用的网站和 App（可以多选），再选它怎么登录：哪个邮箱、手机号，还是微信、QQ。列表里没有的，当场新建就行。",
    "搜不到的 App 会从 App Store 找，带上官方图标。"),
  TourPage(Icons.Filled.Email, "#5e5ce6", "卡包：每个身份一张卡",
    "每个邮箱、手机号、微信、QQ 都是一张卡。点开一张，就能看到它关联的全部 App，包括通过绑定它的微信 / QQ 登录的。Apple ID 是邮箱或手机号上的一个标记，不用单独建卡。",
    "上方可以在 邮箱 / 手机号 / 微信/QQ 之间切换。"),
  TourPage(Icons.Filled.AccountTree, "#ff9f0a", "关系图",
    "所有邮箱、手机号和 App 会自动连成一张图。点一个节点，就能看到它牵连着哪些账号。",
    "准备换手机号？先在这里看看要改多少个。"),
  TourPage(Icons.Filled.Security, "#ff375f", "隐私与备份",
    "右上角可以一键打码，截图也安心。在「设置」里能导出备份，或加密同步到你自己的 Gist。",
    "以后更新直接覆盖安装，数据不会丢。"),
)

/** first launch: open the tour once (replaces the old 「先看看示例？」 dialog) */
@Composable
fun FirstRunTour() {
  LaunchedEffect(Unit) {
    val prefs = Store.prefs()
    if (Store.firstRun && !prefs.getBoolean("atlas.tourDone", false) && !prefs.getBoolean("atlas.demoAsked", false)) {
      prefs.edit().putBoolean("atlas.demoAsked", true).apply()
      kotlinx.coroutines.delay(400)
      Store.tourDemo = true
      Store.tour = true
    }
  }
  AnimatedVisibility(Store.tour, enter = fadeIn(calm()), exit = fadeOut(calm())) { Onboarding(Store.tourDemo) }
}

@Composable
fun Onboarding(offerDemo: Boolean) {
  val pager = rememberPagerState { TOUR.size }
  val scope = rememberCoroutineScope()
  val hap = haptics()
  val last = pager.currentPage == TOUR.size - 1
  fun finish() {
    Store.prefs().edit().putBoolean("atlas.tourDone", true).apply()
    Store.tour = false
  }
  BackHandler { finish() }
  Box(Modifier.fillMaxSize().background(pal.bg).clickable(remember { MutableInteractionSource() }, null) {}) {
    Glow()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
      Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (!last) T("跳过", 16f, FontWeight.Medium, pal.secondary, Modifier.press(0.94f) { finish() }.padding(8.dp))
      }
      HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
        val p = TOUR[i]
        Column(Modifier.fillMaxSize().padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
          Box(Modifier.size(128.dp).clip(RoundedCornerShape(34.dp)).background(Brush.verticalGradient(listOf(hex(p.tint).copy(alpha = 0.8f), hex(p.tint)))), contentAlignment = Alignment.Center) {
            Icon(p.icon, null, tint = Color.White, modifier = Modifier.size(58.dp))
          }
          Spacer(Modifier.height(30.dp))
          T(p.title, 26f, FontWeight.Bold, align = TextAlign.Center)
          Spacer(Modifier.height(16.dp))
          T(p.body, 16f, color = pal.secondary, align = TextAlign.Center)
          Spacer(Modifier.height(20.dp))
          Row(Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lightbulb, null, tint = hex("#ffd60a"), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            T(p.tip, 13f, FontWeight.Medium)
          }
        }
      }
      Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), horizontalArrangement = Arrangement.Center) {
        TOUR.indices.forEach { i ->
          val w by animateDpAsState(if (i == pager.currentPage) 18.dp else 7.dp, calm(), label = "dot")
          Box(Modifier.padding(horizontal = 3.5.dp).size(w, 7.dp).clip(CircleShape).background(if (i == pager.currentPage) pal.primary else pal.tertiary))
        }
      }
      Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 24.dp).height(150.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50)).background(pal.accent).press(0.97f) {
          hap(TICK)
          if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        }, contentAlignment = Alignment.Center) {
          T(if (last) "开始使用" else "下一步", 17f, FontWeight.SemiBold, Color.White)
        }
        if (last && offerDemo && Store.data.identities.isEmpty() && Store.data.accounts.isEmpty()) {
          T("先载入示例数据看看", 15f, FontWeight.Medium, pal.accent, Modifier.padding(top = 4.dp).press(0.96f) { Store.loadDemo(); finish() }.padding(12.dp))
          T("示例是虚构的邮箱和 App，随时可以一键清除。", 12f, color = pal.secondary, align = TextAlign.Center)
        }
      }
    }
  }
}

/** over the wallet while sample data is on the device, so nobody wonders where it came from */
@Composable
fun DemoBanner(modifier: Modifier = Modifier) {
  AnimatedVisibility(Store.hasDemo, modifier, enter = fadeIn(calm()), exit = fadeOut(calm())) {
    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Filled.Info, null, tint = pal.accent, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(10.dp))
      T("现在显示的是示例数据", 14f, FontWeight.Medium, modifier = Modifier.weight(1f))
      T("清除示例", 14f, FontWeight.SemiBold, pal.accent, Modifier.press(0.94f) { Store.clearDemo() }.padding(4.dp))
    }
  }
}
