package dev.huichen.atlas

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

// 2.5 分享我的星图: a card image of which apps the user has, grouped by identity. By default no email,
// number, QQ or 微信号 appears on it at all; the user picks which identities go in.

@Composable
fun ShareSheet() {
  val ctx = LocalContext.current
  val scope = rememberCoroutineScope()
  val layer = rememberGraphicsLayer()
  val withApps = Store.data.identities.filter { i -> Store.data.accounts.any { i.id in it.identityIds } }
  var hideValues by remember { mutableStateOf(true) }
  var chosen by remember { mutableStateOf(withApps.map { it.id }.toSet()) }
  var busy by remember { mutableStateOf(false) }
  val hap = haptics()
  fun share() {
    if (busy) return
    busy = true
    scope.launch {
      runCatching {
        val bmp = layer.toImageBitmap().asAndroidBitmap()
        val file = withContext(Dispatchers.IO) {
          val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
          File(dir, "账号星图-${LocalDate.now()}.png").also { f -> f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".share", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        AppLock.expectReturn(); ctx.startActivity(Intent.createChooser(send, "分享我的星图"))
      }.onFailure { Store.show("图片没生成成功") }
      busy = false
    }
  }
  androidx.compose.foundation.layout.Column {
    SheetBar("取消", "分享我的星图", if (busy) "生成中…" else "分享", chosen.isNotEmpty() && !busy) { hap(CONFIRM); share() }
    FormScroll {
      Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) {
          ShareCard(withApps.filter { it.id in chosen }, hideValues)
        }
      }
      FormGroup(null, "打开时卡片上只有身份的种类和数量，不出现任何邮箱、手机号、QQ 号或微信号。") {
        SettingRow(null, "隐藏所有账号", trailing = {
          Switch(hideValues, { hideValues = it }, colors = SwitchDefaults.colors(checkedTrackColor = hex("#34c759"), checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent))
        }) { hideValues = !hideValues }
      }
      FormGroup("包含哪些身份") {
        withApps.forEachIndexed { k, i ->
          if (k > 0) Divider()
          val on = i.id in chosen
          Row(Modifier.fillMaxWidth().press(1f) { hap(TICK); chosen = if (on) chosen - i.id else chosen + i.id }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            SmallCard(i, kindIcon(i), 36.dp, 24.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
              T(Store.title(i), 16f, lines = 1)
              T(kindName(i) + " · ${Store.accountsOf(i.id).size} 个 App", 12f, color = pal.secondary)
            }
            Icon(if (on) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null, Modifier.size(24.dp), tint = if (on) hex(i.color) else pal.secondary.copy(alpha = 0.5f))
          }
        }
        if (withApps.isEmpty()) T("还没有关联了 App 的身份。", 15f, color = pal.secondary, modifier = Modifier.padding(16.dp))
      }
    }
  }
}

/** The image itself: fixed width, drawn with plain fills (no blur / shadow) so it records cleanly. */
@Composable
fun ShareCard(ids: List<Identity>, hideValues: Boolean) {
  val dark = pal.dark
  val fg = if (dark) Color.White else hex("#1c1c1e")
  val sub = if (dark) Color.White.copy(alpha = 0.62f) else hex("#3c3c43").copy(alpha = 0.7f)
  val accent = hex(ids.firstOrNull()?.color ?: "#0a84ff")
  val accs = Store.data.accounts.filter { a -> a.identityIds.any { x -> ids.any { it.id == x } } }
  val order = Store.cats
  val cats = accs.groupingBy { it.category }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { order.indexOf(it.key).let { k -> if (k < 0) Int.MAX_VALUE else k } })
  Column(Modifier.width(360.dp).clip(RoundedCornerShape(28.dp))
    .drawBehind {
      drawRect(Brush.verticalGradient(if (dark) listOf(hex("#15151b"), hex("#07070a")) else listOf(hex("#ffffff"), hex("#eef0f6"))))
      drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = if (dark) 0.45f else 0.25f), Color.Transparent), Offset(size.width * 0.15f, 0f), size.width * 0.9f), size.width * 0.9f, Offset(size.width * 0.15f, 0f))
    }
    .padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Image(painterResource(R.mipmap.ic_launcher_flat), null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        T("我的账号星图", 22f, FontWeight.Bold, fg)
        val d = LocalDate.now()
        T("${d.year}年${d.monthValue}月${d.dayOfMonth}日", 12f, color = sub)
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf("${ids.size}" to "个身份", "${accs.size}" to "个 App", "${cats.size}" to "个分类").forEach { (n, l) ->
        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(fg.copy(alpha = 0.07f)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          T(n, 22f, FontWeight.Bold, fg); T(l, 11f, color = sub)
        }
      }
    }
    if (cats.isNotEmpty()) T(cats.joinToString("  ·  ") { "${it.key} ${it.value}" }, 12f, FontWeight.Medium, sub)
    ids.forEach { i ->
      val list = Store.accountsOf(i.id)
      val head = buildString {
        append(kindName(i))
        if (!hideValues) append(" " + maskValue(i))
        else if (i.label.isNotEmpty() && !Store.hideNames) append(" · " + i.label)
      }
      Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).drawBehind { drawRect(skinBrush(i.color, size)) }
        .border(0.6.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(20.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (i.isLogin) LoginGlyph(i, 18.dp) else Icon(kindIcon(i), null, Modifier.size(17.dp), tint = Color.White)
          Spacer(Modifier.width(8.dp))
          T(head, 15f, FontWeight.SemiBold, Color.White, Modifier.weight(1f), lines = 1)
          T("→ ${list.size} 个 App", 13f, FontWeight.SemiBold, Color.White.copy(alpha = 0.9f))
        }
        val shown = list.take(14)
        shown.chunked(7).forEach { row ->
          Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEachIndexed { k, a ->
              val last = a == shown.last() && list.size > shown.size
              if (last) Box(Modifier.size(34.dp).clip(squircle(34.dp)).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                T("+${list.size - shown.size + 1}", 12f, FontWeight.Bold, Color.White)
              } else AppIcon(a, 34.dp)
            }
          }
        }
      }
    }
    if (ids.isEmpty()) T("选几个身份放进卡片里。", 14f, color = sub)
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
      T("账号星图", 13f, FontWeight.SemiBold, fg)
      Spacer(Modifier.weight(1f))
      T("huichen.dev/account-atlas", 12f, color = sub)
    }
  }
}
