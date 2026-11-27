package dev.huichen.atlas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 分类管理: add, rename, delete, long-press to reorder. Every change is saved right away (with 撤销);
 *  其他 is always last and can't be renamed or deleted. back = null: opened as its own sheet (编辑分类…). */
@Composable
fun CategoriesPage(back: (() -> Unit)?) {
  var adding by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf<String?>(null) }
  var deleting by remember { mutableStateOf<String?>(null) }
  val all = Store.cats
  val counts = Store.data.accounts.groupingBy { it.category }.eachCount()
  Column {
    if (back != null) SheetBar("设置", "分类管理", null, onLeft = back, leftIcon = true)
    else SheetBar(null, "分类管理", "完成") { Store.pop() }
    FormScroll {
      CategoryList(all.dropLast(1), counts, { renaming = it }, { deleting = it })
      FormGroup(footer = "长按拖动排序。排在前面的分类，在「全部 App」里也排在前面。「其他」不能改名或删除，总在最后。") {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
          T(OTHER_CAT, 16f, modifier = Modifier.weight(1f))
          T("${counts[OTHER_CAT] ?: 0} 个 App", 14f, color = pal.secondary)
        }
      }
      FormGroup {
        SettingRow(Icons.Filled.Add, "添加分类") { adding = true }
      }
    }
  }
  if (adding) CategoryNameDialog("添加分类", "新分类会排在「其他」前面。", "", "添加", { adding = false }) { Store.addCategory(it) }
  renaming?.let { c -> CategoryNameDialog("重命名分类", "用这个分类的 App 会一起改过去。", c, "保存", { renaming = null }) { Store.renameCategory(c, it) } }
  deleting?.let { c ->
    val n = counts[c] ?: 0
    Confirm("删除分类「$c」？", if (n > 0) "其中的 $n 个 App 会移到「其他」。" else "这个分类里还没有 App。", "删除", { deleting = null }) { Store.deleteCategory(c) }
  }
}

/** add / rename: a refused name stays in the dialog with the reason under it */
@Composable
fun CategoryNameDialog(title: String, hint: String, initial: String, action: String, onDismiss: () -> Unit, submit: (String) -> String?) {
  var text by remember { mutableStateOf(initial) }
  var error by remember { mutableStateOf("") }
  if (AppLock.blocking) return
  GlassAlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
    text = {
      Column {
        Text(if (error.isEmpty()) hint else error, color = if (error.isEmpty()) Color.Unspecified else pal.red)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(text, { text = it; error = "" }, label = { Text("分类名") }, placeholder = { Text("最多 $CAT_MAX 个字") }, singleLine = true)
      }
    },
    confirmButton = { TextButton({ val e = submit(text); if (e == null) onDismiss() else error = e }) { Text(action) } },
    dismissButton = { TextButton(onDismiss) { Text("取消") } })
}

/** Long-press a row and drag it (the same feel as 卡片顺序); 其他 is not in this list. */
@Composable
private fun CategoryList(list: List<String>, counts: Map<String, Int>, rename: (String) -> Unit, delete: (String) -> Unit) {
  val hap = haptics()
  val scope = rememberCoroutineScope()
  val rowH = 52.dp
  val slot = with(LocalDensity.current) { rowH.toPx() }
  // Local order; rows never move in the layout while a finger is down. Neighbours slide out of the
  // way with a translation, the lifted row follows the finger, and the new order is committed once,
  // after the lifted row has settled into its slot.
  var order by remember { mutableStateOf(list) }
  var dragId by remember { mutableStateOf<String?>(null) }
  var settling by remember { mutableStateOf(false) }
  var from by remember { mutableIntStateOf(0) }
  var to by remember { mutableIntStateOf(0) }
  var dy by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(list) { if (dragId == null) order = list }
  FormGroup {
    order.forEachIndexed { k, c ->
      key(c) {
        val dragging = dragId == c
        val target = when {
          dragId == null || dragging -> 0f
          k in (from + 1)..to -> -slot
          k in to until from -> slot
          else -> 0f
        }
        val shift = remember { Animatable(0f) }
        LaunchedEffect(target, dragId) { if (dragId == null) shift.snapTo(0f) else shift.animateTo(target, calm(Spring.StiffnessMedium)) }
        val lift by animateFloatAsState(if (dragging && !settling) 1.03f else 1f, calm(), label = "lift")
        val shadow by animateFloatAsState(if (dragging && !settling) 14f else 0f, calm(), label = "shadow")
        val sep = pal.stroke
        Row(Modifier.fillMaxWidth().height(rowH).zIndex(if (dragging) 1f else 0f)
          .graphicsLayer {
            translationY = if (dragId == null) 0f else if (dragging) dy else shift.value
            scaleX = lift; scaleY = lift
            shadowElevation = shadow; shape = RoundedCornerShape(12.dp); clip = false
          }
          .background(if (dragging) pal.field else Color.Transparent, RoundedCornerShape(12.dp))
          .drawBehind { if (k > 0 && dragId == null) drawRect(sep, Offset(16.dp.toPx(), 0f), Size(size.width - 16.dp.toPx(), 0.5.dp.toPx())) }
          .pointerInput(c) {
            detectDragGesturesAfterLongPress(
              onDragStart = {
                if (dragId == null && !settling) {
                  from = order.indexOf(c); to = from; dy = 0f; dragId = c
                  hap(android.view.HapticFeedbackConstants.LONG_PRESS)
                }
              },
              onDragEnd = {
                if (dragId == c && !settling) {
                  settling = true
                  scope.launch {
                    animate(dy, (to - from) * slot, animationSpec = calm(Spring.StiffnessMedium)) { v, _ -> dy = v }
                    val next = order.toMutableList().apply { removeAt(from); add(to, c) }
                    val moved = from != to
                    order = next; dragId = null; dy = 0f; settling = false
                    if (moved) Store.reorderCategories(next)
                  }
                }
              },
              onDragCancel = {
                if (dragId == c && !settling) {
                  settling = true
                  scope.launch { animate(dy, 0f, animationSpec = calm(Spring.StiffnessMedium)) { v, _ -> dy = v }; to = from; dragId = null; dy = 0f; settling = false }
                }
              },
            ) { ch, d ->
              if (dragId != c || settling) return@detectDragGesturesAfterLongPress
              ch.consume()
              dy = (dy + d.y).coerceIn(-from * slot, (order.size - 1 - from) * slot)
              val n = (from + (dy / slot).roundToInt()).coerceIn(0, order.size - 1)
              if (n != to) { to = n; hap(TICK) }
            }
          }
          .padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
          T(c, 16f, lines = 1, modifier = Modifier.weight(1f))
          T("${counts[c] ?: 0} 个 App", 14f, color = pal.secondary)
          Spacer(Modifier.width(4.dp))
          Box(Modifier.size(40.dp).press(0.9f) { rename(c) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Edit, "重命名", Modifier.size(20.dp), tint = pal.accent)
          }
          Box(Modifier.size(40.dp).press(0.9f) { delete(c) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.DeleteOutline, "删除", Modifier.size(20.dp), tint = pal.red)
          }
          Icon(Icons.Filled.DragHandle, "拖动", Modifier.padding(start = 4.dp), tint = pal.tertiary)
        }
      }
    }
  }
}
