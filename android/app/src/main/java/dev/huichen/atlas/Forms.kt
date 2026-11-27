package dev.huichen.atlas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// MARK: - form kit (grouped, like an iOS Form)

@Composable
fun SheetBar(left: String?, title: String, right: String?, rightEnabled: Boolean = true, onLeft: () -> Unit = { Store.pop() }, leftIcon: Boolean = false, rightAnchor: String? = null, onRight: () -> Unit = {}) {
  Box(Modifier.fillMaxWidth().padding(top = 18.dp).height(44.dp).padding(horizontal = 8.dp)) {
    if (left != null) Row(Modifier.align(Alignment.CenterStart).press(0.94f, onClick = onLeft).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      if (leftIcon) Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = pal.accent)
      T(left, 17f, color = pal.accent)
    }
    T(title, 17f, FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
    if (right != null) T(right, 17f, FontWeight.SemiBold, if (rightEnabled) pal.accent else pal.tertiary,
      Modifier.align(Alignment.CenterEnd).then(if (rightAnchor != null) Modifier.tourAnchor(rightAnchor) else Modifier)
        .press(0.94f, enabled = rightEnabled, onClick = onRight).padding(horizontal = 8.dp, vertical = 6.dp))
  }
}

@Composable
fun FormGroup(header: String? = null, footer: String? = null, clear: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp)) {
    if (header != null) T(header, 13f, color = pal.secondary, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (clear) Color.Transparent else pal.field), content = content)
    if (footer != null) T(footer, 13f, color = pal.secondary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
  }
}

@Composable
fun Divider() { Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(pal.stroke)) }

@Composable
fun Field(value: String, onChange: (String) -> Unit, hint: String, kb: KeyboardType = KeyboardType.Text, lines: Int = 1, size: Float = 17f, weight: FontWeight = FontWeight.Normal, color: Color? = null, raw: Boolean = false) {
  Box(Modifier.fillMaxWidth().padding(horizontal = if (raw) 0.dp else 16.dp, vertical = if (raw) 2.dp else 12.dp)) {
    if (value.isEmpty()) T(hint, size, weight, pal.tertiary, lines = 1)
    BasicTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = lines == 1, minLines = 1, maxLines = if (lines == 1) 1 else 6,
      textStyle = TextStyle(color = color ?: pal.primary, fontSize = size.sp, fontWeight = weight), cursorBrush = SolidColor(pal.accent),
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = kb == KeyboardType.Text && lines > 1, keyboardType = kb))
  }
}

@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
  val hap = haptics()
  BoxWithConstraints(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(9.dp)).background(pal.glass).padding(2.dp)) {
    val w = maxWidth / options.size
    val x by animateDpAsState(w * options.indexOfFirst { it.first == selected }.coerceAtLeast(0), calm(), label = "seg")
    Box(Modifier.offset(x = x).width(w).fillMaxSize().clip(RoundedCornerShape(7.dp)).background(if (pal.dark) hex("#636366") else Color.White))
    Row(Modifier.fillMaxSize()) {
      options.forEach { (k, name) ->
        Box(Modifier.weight(1f).fillMaxSize().press(1f) { if (k != selected) { hap(TICK); onPick(k) } }, contentAlignment = Alignment.Center) {
          T(name, 14f, FontWeight.SemiBold)
        }
      }
    }
  }
}

/** 怎么登录 / 用哪个身份: every email, phone, 微信 and QQ in one list (Apple IDs badged), and 新建 only when
 *  none fits. A value that already exists is never typed in again: 新建 picks the existing card. */
@Composable
fun IdentityToggles(selected: List<String>, onChange: (List<String>) -> Unit) {
  val hap = haptics()
  val all = Store.data.identities.filter { it.type in KINDS }
  val rank = { i: Identity -> when { i.type == "email" -> 0; i.type == "phone" -> 1; i.isQQ -> 3; else -> 2 } }
  val sorted = all.withIndex().sortedWith(compareBy({ rank(it.value) }, { it.index })).map { it.value }
  // 2.5: a 微信 / QQ sits right under the (first) phone or email it is bound to; unbound ones come last
  val under = sorted.filter { it.isLogin }.groupBy { l -> Store.bindsOf(l).firstOrNull()?.id }
  val list = sorted.filter { !it.isLogin }.flatMap { listOf(it) + (under[it.id] ?: emptyList()) } + (under[null] ?: emptyList())
  var creating by remember { mutableStateOf(false) }
  var nKind by remember { mutableStateOf("email") }
  var nValue by remember { mutableStateOf("") }
  var nVia by remember { mutableStateOf<String?>(null) }
  @Composable fun row(i: Identity, first: Boolean) {
    val on = i.id in selected
    val sc by animateFloatAsState(if (on) 1f else 0.85f, calm(), label = "chk")
    val nested = i.isLogin && Store.bindsOf(i).isNotEmpty()
    Row(Modifier.fillMaxWidth().then(if (first) Modifier.tourAnchor("who") else Modifier).press(1f) { hap(TICK); onChange(if (on) selected - i.id else selected + i.id) }
      .padding(start = if (nested) 34.dp else 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
      verticalAlignment = Alignment.CenterVertically) {
      SmallCard(i, kindIcon(i), 36.dp, 24.dp)
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        if (i.isLogin) {
          val tag = bindTags(Store.bindsOf(i))
          T(providerName(i.provider) + " " + Store.display(i) + (if (tag.isNotEmpty()) " · 绑定 $tag" else ""), 16f, lines = 1)
          if (i.label.isNotEmpty()) T(Store.labelText(i.label), 12f, color = pal.secondary)
        } else {
          Row(verticalAlignment = Alignment.CenterVertically) {
            T(Store.title(i), 16f)
            if (i.isAppleID) AppleBadge()
          }
          if (i.label.isNotEmpty()) T(Store.display(i), 12f, color = pal.secondary)
        }
      }
      Icon(if (on) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null, Modifier.size(24.dp).graphicsLayer { scaleX = sc; scaleY = sc },
        tint = if (on) hex(i.color) else pal.secondary.copy(alpha = 0.5f))
    }
  }
  list.forEachIndexed { k, i -> if (k > 0) Divider(); row(i, k == 0) }
  if (list.isNotEmpty()) Divider()
  Row(Modifier.fillMaxWidth().press(1f) { hap(TICK); creating = !creating }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
    T(if (list.isEmpty()) "新建身份（邮箱、手机号、微信或 QQ）" else "新建身份", 16f, FontWeight.Medium, pal.accent)
  }
  if (creating) {
    val login = nKind == "wechat" || nKind == "qq"
    val type = if (login) "login" else nKind
    val hit = Store.existing(type, if (login) nKind else null, nValue)
    val err = if (nValue.isBlank()) null else idValueError(nKind, nValue)
    Divider()
    Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
      Segmented(listOf("email" to "邮箱", "phone" to "手机号", "wechat" to "微信", "qq" to "QQ"), nKind) { nKind = it; nVia = null }
    }
    Field(nValue, { nValue = it }, when (nKind) { "phone" -> "+86 138 0000 0000"; "wechat" -> "wxid 或自定义微信号"; "qq" -> "QQ 号或 QQ 邮箱"; else -> "name@example.com" },
      when (nKind) { "phone" -> KeyboardType.Phone; "email" -> KeyboardType.Email; "qq" -> KeyboardType.Ascii; else -> KeyboardType.Text })
    if (hit != null) T("已经有这个身份了（${Store.title(hit)}），会直接选上它。", 13f, color = pal.secondary, modifier = Modifier.padding(horizontal = 16.dp))
    else if (err != null && nValue.length > 2) T(err, 13f, color = pal.secondary, modifier = Modifier.padding(horizontal = 16.dp))
    if (login && hit == null) {
      T("绑定的手机号/邮箱（从已有的里选）", 13f, color = pal.secondary, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
      (listOf<Identity?>(null) + Store.data.identities.filter { it.type == "email" || it.type == "phone" }).forEach { b ->
        val on = nVia == b?.id
        Row(Modifier.fillMaxWidth().press(1f) { hap(TICK); nVia = b?.id }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
          T(b?.let { (if (it.isPhone) "手机号 · " else "邮箱 · ") + Store.title(it) } ?: "不绑定", 15f, modifier = Modifier.weight(1f))
          Icon(if (on) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null, Modifier.size(22.dp), tint = if (on) pal.accent else pal.secondary.copy(alpha = 0.5f))
        }
      }
    }
    val ok = nValue.isNotBlank() && (hit != null || err == null)
    Box(Modifier.fillMaxWidth().padding(16.dp).clip(CircleShape).background(if (ok) pal.accent else pal.glass)
      .press(0.97f, enabled = ok) {
        hap(TICK)
        val id = hit?.id ?: Store.ensureIdentity(type, nValue, if (login) nKind else null, if (login) nVia else null).first
        if (id !in selected) onChange(selected + id)
        Store.show(if (hit != null) "已经有这个身份，已直接选上" else "已添加并选上")
        creating = false; nValue = ""; nVia = null
      }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
      T(if (hit != null) "选上它" else "添加并选上", 16f, FontWeight.SemiBold, if (ok) Color.White else pal.secondary)
    }
  }
}

@Composable
fun FormScroll(content: @Composable ColumnScope.() -> Unit) {
  Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 30.dp), content = content)
}

// MARK: - identity form

@Composable
fun IdentityForm(existing: Identity?, type0: String, defaultColor: String, presetVia: String? = null) {
  var type by remember { mutableStateOf(existing?.type ?: type0) }
  var value by remember { mutableStateOf(existing?.value ?: "") }
  var label by remember { mutableStateOf(existing?.label ?: "") }
  // 2.4: 微信 and QQ are the only login cards; an Apple ID is a flag on its email / phone
  var provider by remember { mutableStateOf(if (existing?.provider == "qq") "qq" else "wechat") }
  // 2.5: a 微信 / QQ can be bound to several (a phone and the QQ mailbox); from a phone's 「添加绑定的 QQ / 微信」 it starts bound to it
  var binds by remember { mutableStateOf(existing?.bindIds?.filter { v -> Store.identity(v)?.let { !it.isLogin } == true } ?: listOfNotNull(presetVia)) }
  var addNew by remember { mutableStateOf(false) }   // a number / address not in the list yet
  var viaNew by remember { mutableStateOf("") }
  // a new login card starts in its brand colour, until the user picks one
  var colorTouched by remember { mutableStateOf(existing != null) }
  var color by remember { mutableStateOf(existing?.color ?: if (type0 == "login") providerColor("wechat") else defaultColor) }
  var notes by remember { mutableStateOf(existing?.notes ?: "") }
  var esim by remember { mutableStateOf(existing?.esim == true) }
  var apple by remember { mutableStateOf(existing?.apple == true) }
  val hap = haptics()
  val login = type == "login"
  val esimOut: Boolean? = if (type == "phone" && esim) true else null
  val appleOut: Boolean? = if (!login && apple) true else null
  val providerOut = if (login) provider else null
  val bindsOut = if (login) binds else emptyList()
  val sample = when {
    login && provider == "qq" -> "10001"
    login -> "wxid_atlas"
    type == "email" -> "name@example.com"
    else -> "+86 138 0000 0000"
  }
  val preview = Identity(existing?.id ?: "_new", type, value.ifEmpty { sample }, label, color, notes, order = existing?.order, esim = esimOut, provider = providerOut, apple = appleOut).withBinds(bindsOut)
  val dup = Store.existing(type, providerOut, value, existing?.id)
  val viaNewErr = if (login && addNew && viaNew.isNotBlank()) idValueError(if (viaNew.contains('@')) "email" else "phone", viaNew) else null
  val err = if (value.isBlank()) null else idValueError(if (login) provider else type, value)
  val valid = value.isNotBlank() && err == null && dup == null && viaNewErr == null
  fun autoColor() {
    if (colorTouched) return
    color = if (type == "login") providerColor(provider) else if (color in BRAND_SKINS.map { it.first }) defaultColor else color
  }

  fun save() {
    val v = value.trim()
    val bound = bindsOut.toMutableList()
    if (login && addNew && viaNew.isNotBlank()) Store.ensureIdentity(if (viaNew.contains('@')) "email" else "phone", viaNew).first.let { if (it !in bound) bound += it }
    // a QQ whose QQ is an email you already have: bind it to that email instead of typing it twice
    if (login && provider == "qq" && v.contains('@'))
      Store.data.identities.firstOrNull { it.type == "email" && normEmail(it.value) == normEmail(v) }?.id?.let { if (it !in bound) bound += it }
    if (existing != null) {
      Store.mutate("已保存") { s -> s.copy(identities = s.identities.map {
        // edited by the user: a sample card becomes theirs
        if (it.id == existing.id) it.copy(type = type, value = v, label = label.trim(), color = color, notes = notes.trim(), esim = esimOut, apple = appleOut, provider = providerOut, demo = null)
          .let { x -> if (login) x.withBinds(bound) else x }
        // a card that stops being an email / phone can no longer be what a login is bound to
        else if (type == "login" && existing.id in it.bindIds) it.withBinds(it.bindIds - existing.id) else it
      }) }
      Store.setKind(type, existing.id)
    } else {
      val i = Identity(newID(), type, v, label.trim(), color, notes.trim(), esim = esimOut, provider = providerOut, apple = appleOut).withBinds(if (login) bound else emptyList())
      Store.mutate("已添加") { s -> s.copy(identities = s.identities + i) }
      Store.setKind(type, i.id)
    }
    Store.pop()
  }

  @Composable fun switchRow(title: String, on: Boolean, flip: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().press(1f) { hap(TICK); flip(!on) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      T(title, 16f, modifier = Modifier.weight(1f))
      androidx.compose.material3.Switch(on, { hap(TICK); flip(it) }, colors = androidx.compose.material3.SwitchDefaults.colors(
        checkedTrackColor = hex("#34c759"), checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent))
    }
  }

  Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(hex(color).copy(alpha = 0.18f), Color.Transparent)))) {
    Column {
      SheetBar("取消", if (existing == null) "新身份" else "编辑", if (existing == null) "添加" else "保存", valid, onRight = ::save)
      FormScroll {
        Box(Modifier.padding(horizontal = 26.dp).padding(top = 10.dp)) {
          AnimatedContent(type, transitionSpec = { fadeIn(calm()) togetherWith fadeOut(calm()) }, label = "pv") { WalletCard(preview.copy(type = it), reveal = true) }
        }
        Box(Modifier.padding(horizontal = 16.dp).padding(top = 18.dp)) {
          Segmented(listOf("email" to "邮箱", "phone" to "手机号", "login" to "微信 / QQ"), type) { type = it; autoColor() }
        }
        if (login) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          listOf("wechat", "qq").forEach { p ->
            val on = provider == p
            val c = hex(providerColor(p))
            Row(Modifier.weight(1f).height(40.dp).press(0.95f) { if (!on) { hap(TICK); provider = p; autoColor() } }.clip(CircleShape)
              .background(if (on) c.copy(alpha = 0.18f) else pal.glass).border(if (on) 1.5.dp else 0.dp, if (on) c else Color.Transparent, CircleShape),
              horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
              LoginGlyph(Identity("_", "login", provider = p), 18.dp, if (on) c else pal.secondary)
              Spacer(Modifier.width(6.dp))
              T(if (p == "qq") "QQ" else "微信", 15f, FontWeight.SemiBold, if (on) pal.primary else pal.secondary)
            }
          }
        }
        FormGroup(when { login && provider == "qq" -> "QQ 号"; login -> "微信号"; type == "email" -> "地址"; else -> "号码" },
          when { dup != null -> "已经有这个身份了（${Store.title(dup)}），不用重复添加。"; err != null && value.length > 2 -> err; else -> null }) {
          Field(value, { value = it }, when {
            login && provider == "qq" -> "QQ 号或 QQ 邮箱"
            login -> "wxid 或自定义微信号"
            type == "email" -> "name@example.com"
            else -> "+86 138 0000 0000"
          }, if (login && provider == "qq") KeyboardType.Ascii else if (login) KeyboardType.Text else if (type == "email") KeyboardType.Email else KeyboardType.Phone)
          Divider()
          Field(label, { label = it }, "标签：主力、小号、工作…")
        }
        if (!login) FormGroup(null, "Apple ID 就是某个邮箱或手机号，打开这个开关就行，不用单独建卡。") { switchRow("也是 Apple ID", apple) { apple = it } }
        if (login) {
          val choices = Store.data.identities.filter { !it.isLogin && it.id != existing?.id }
          FormGroup("绑定的手机号/邮箱（可多选）", viaNewErr ?: "从已有的卡里选，不用再填一遍。比如 QQ 同时绑了手机号和 QQ 邮箱，两个都选上。") {
            @Composable fun opt(i: Identity?, key: String?, title: String) {
              val on = when (key) { null -> binds.isEmpty() && !addNew; "+" -> addNew; else -> key in binds }
              Row(Modifier.fillMaxWidth().press(1f) {
                hap(TICK)
                when (key) { null -> { binds = emptyList(); addNew = false }; "+" -> addNew = !addNew; else -> binds = if (key in binds) binds - key else binds + key }
              }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (i != null) { SmallCard(i, kindIcon(i), 36.dp, 24.dp); Spacer(Modifier.width(12.dp)) }
                Column(Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    T(title, 16f)
                    if (i?.isAppleID == true) AppleBadge()
                  }
                  if (i != null && i.label.isNotEmpty()) T(Store.display(i), 12f, color = pal.secondary)
                }
                Icon(if (on) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null, Modifier.size(24.dp),
                  tint = if (on) (i?.let { hex(it.color) } ?: pal.accent) else pal.secondary.copy(alpha = 0.5f))
              }
            }
            opt(null, null, "不绑定")
            choices.forEach { Divider(); opt(it, it.id, Store.title(it)) }
            Divider(); opt(null, "+", "新的手机号或邮箱")
            if (addNew) { Divider(); Field(viaNew, { viaNew = it }, "手机号或邮箱", KeyboardType.Email) }
          }
        }
        if (type == "phone") FormGroup(null, "eSIM 没有实体卡，换手机时记得转移。") { switchRow("eSIM", esim) { esim = it } }
        FormGroup("卡面") {
          // login cards also offer their brand skins (微信绿 / QQ 蓝)
          val swatches = (if (login || color in BRAND_SKINS.map { it.first }) BRAND_SKINS else emptyList()) + SKINS
          Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            swatches.chunked(5).forEach { row ->
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (c, s) ->
                  val on = color == c
                  Column(Modifier.weight(1f).press(0.92f) { if (!on) hap(TICK); color = c; colorTouched = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(40.dp).border(if (on) 2.5.dp else 0.dp, if (on) pal.primary else Color.Transparent, RoundedCornerShape(11.dp)).padding(3.dp)
                      .skin(c, RoundedCornerShape(8.dp)))
                    T(s.name, 11f, color = if (on) pal.primary else pal.secondary, modifier = Modifier.padding(top = 4.dp))
                  }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
              }
            }
          }
        }
        FormGroup("备注", "不要在这里填写密码。") {
          Field(notes, { notes = it }, if (login) "实名、绑定的银行卡、找回方式" else "运营商、是否实名、找回方式", lines = 4)
        }
      }
    }
  }
}

@Composable
fun AppleBadge() {
  Row(Modifier.padding(start = 6.dp).clip(RoundedCornerShape(5.dp)).background(pal.secondary.copy(alpha = 0.15f)).padding(horizontal = 5.dp, vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically) {
    AppleGlyph(10.dp, pal.secondary)
    Spacer(Modifier.width(2.dp))
    T("Apple ID", 10f, FontWeight.SemiBold, pal.secondary)
  }
}

// MARK: - account form

/** App Store artwork thumbnail (artworkUrl100) for suggestion lists, letter/favicon until it loads. */
@Composable
fun HitThumb(hit: AppHit, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
  val url = hit.artworkUrl100
  var bmp by remember(url) { mutableStateOf(url?.let { AppStore.thumbCached(it) }) }
  if (url != null && bmp == null) LaunchedEffect(url) { bmp = AppStore.thumb(url) }
  val b = bmp
  if (b != null) {
    val shape = squircle(size)
    Box(modifier.size(size).clip(shape).border(0.5.dp, Color.White.copy(alpha = 0.12f), shape)) {
      androidx.compose.foundation.Image(b, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    }
  } else AppIcon(hit.name, hit.domain, size, modifier)
}

@Composable
private fun SuggestRow(title: String, sub: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
  Row(Modifier.fillMaxWidth().press(1f, onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
    icon(); Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      T(title, 16f, lines = 1)
      if (sub.isNotEmpty()) T(sub, 12f, color = pal.secondary, lines = 1)
    }
  }
}

@Composable
fun AccountForm(existing: Account) {
  var name by remember { mutableStateOf(existing.name) }
  var domain by remember { mutableStateOf(existing.domain) }
  var category by remember { mutableStateOf(existing.category) }
  var handle by remember { mutableStateOf(existing.handle ?: "") }
  var notes by remember { mutableStateOf(existing.notes) }
  var ids by remember { mutableStateOf(existing.identityIds) }
  var icon by remember { mutableStateOf(existing.icon) }
  var catMenu by remember { mutableStateOf(false) }
  var iconMenu by remember { mutableStateOf(false) }
  var storeSearch by remember { mutableStateOf(false) }   // 更换图标 › 从 App Store 搜索
  // suggestions show only while the user is typing a name
  var typed by remember { mutableStateOf(false) }
  var hits by remember { mutableStateOf(listOf<AppHit>()) }
  var pending by remember { mutableStateOf<AppHit?>(null) }   // App Store pick whose icon is downloading
  var token by remember { mutableIntStateOf(0) }
  val scope = rememberCoroutineScope()
  val ctx = androidx.compose.ui.platform.LocalContext.current
  val hap = haptics()

  val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) { val t = ++token; pending = null; scope.launch { CustomIcon.fromUri(ctx, uri)?.let { if (t == token) { icon = it; hap(TICK) } } } }
  }

  val q = name.trim().lowercase()
  LaunchedEffect(q, typed) {
    if (!typed || q.isEmpty()) { hits = emptyList(); return@LaunchedEffect }
    delay(350)
    hits = AppStore.search(q)   // a newer query cancels this one, so late results are dropped
  }
  val presetHits = if (!typed || q.isEmpty()) emptyList() else
    (Store.customApps + Presets.all + Presets.directory).filter { siteHit(it, q) }.distinctBy { it.name.lowercase() }.take(4)
  val shownNames = presetHits.map { it.name.lowercase() }.toSet()
  val storeHits = if (!typed || q.isEmpty()) emptyList() else hits.filter { it.name.lowercase() !in shownNames }.distinctBy { it.name.lowercase() }

  fun pickPreset(p: Preset) {
    hap(TICK); token++; pending = null
    name = p.name; if (p.domain.isNotEmpty()) domain = p.domain; category = Store.catOr(p.category); icon = null; typed = false
  }
  fun pickHit(h: AppHit) {
    hap(TICK); val t = ++token
    name = h.name; if (h.domain.isNotEmpty()) domain = h.domain; category = Store.catOr(h.category); typed = false; pending = h
    scope.launch { val ic = AppStore.icon(h); if (t == token) { icon = ic; pending = null } }
  }
  /** 从 App Store 搜索: only the icon changes; the artwork shows at once and the stored copy is the same
   *  128 px data URL the add flow saves. A failed download keeps the icon the form had. */
  fun pickIcon(h: AppHit) {
    hap(TICK); val t = ++token; val prev = icon; pending = h
    scope.launch {
      val ic = AppStore.icon(h)
      if (t == token) { pending = null; if (ic != null) icon = ic else { icon = prev; Store.show("图标下载失败，换一个试试") } }
    }
  }

  // back from 编辑分类…: a renamed category follows to its new name (a deleted one becomes 其他)
  val allCats = Store.cats
  LaunchedEffect(allCats) { category = Store.catFollow(category) }

  Column {
    // pending: an App Store icon still downloading, saving now would drop it
    SheetBar("取消", "编辑", "保存", name.isNotBlank() && pending == null) {
      Store.mutate("已保存") { s -> s.copy(accounts = s.accounts.map {
        if (it.id == existing.id) it.copy(name = name.trim(), domain = cleanDomain(domain), category = category, notes = notes.trim(), handle = handle.trim().ifEmpty { null }, identityIds = ids,
          icon = icon?.ifBlank { null }) else it
      }) }
      Store.pop()
    }
    FormScroll {
      FormGroup {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
          Box {
            Column(Modifier.press(0.92f) { iconMenu = true }, horizontalAlignment = Alignment.CenterHorizontally) {
              val pd = pending
              if (pd != null) HitThumb(pd, 54.dp) else AppIcon(name, domain, 54.dp, icon = icon, iconKey = existing.id)
              T("更换图标", 11f, FontWeight.Medium, pal.accent, Modifier.padding(top = 4.dp))
            }
            DropdownMenu(iconMenu, { iconMenu = false }) {
              DropdownMenuItem({ Text("从 App Store 搜索") }, { iconMenu = false; storeSearch = true })
              DropdownMenuItem({ Text("从相册选择") }, {
                iconMenu = false
                photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
              })
              if (icon != null || pending != null) DropdownMenuItem({ Text("使用默认图标") }, { iconMenu = false; token++; pending = null; icon = null; hap(TICK) })
            }
            if (storeSearch) AppStoreIconDialog(name.trim(), { storeSearch = false }) { h -> storeSearch = false; pickIcon(h) }
          }
          Spacer(Modifier.width(14.dp))
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(name, { name = it; typed = true }, "名称", size = 18f, weight = FontWeight.SemiBold, raw = true)
            Field(domain, { domain = it }, "域名（可选）", KeyboardType.Uri, color = pal.secondary, raw = true)
          }
        }
        AnimatedVisibility(presetHits.isNotEmpty() || storeHits.isNotEmpty()) {
          Column {
            presetHits.forEach { p ->
              Divider()
              SuggestRow(p.name, listOf(p.category, p.domain).filter { it.isNotEmpty() }.joinToString(" · "), { pickPreset(p) }) { AppIcon(p.name, p.domain, 32.dp) }
            }
            if (storeHits.isNotEmpty()) {
              Divider()
              T("App Store", 13f, FontWeight.Medium, pal.secondary, Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp))
              storeHits.forEach { h ->
                SuggestRow(h.name, listOf(h.category, h.domain).filter { it.isNotEmpty() }.joinToString(" · "), { pickHit(h) }) { HitThumb(h, 32.dp) }
              }
            }
          }
        }
        Divider()
        Field(handle, { handle = it }, "账号名（可选）")
        Divider()
        Box {
          Row(Modifier.fillMaxWidth().press(1f) { catMenu = true }.padding(horizontal = 16.dp, vertical = 12.dp)) {
            T("分类", 17f, modifier = Modifier.weight(1f)); T(category, 17f, color = pal.secondary)
          }
          DropdownMenu(catMenu, { catMenu = false }) {
            (if (category in allCats) allCats else allCats.dropLast(1) + category + OTHER_CAT).forEach { c -> DropdownMenuItem({ Text(c) }, { category = c; catMenu = false }) }
            DropdownMenuItem({ Text("编辑分类…", color = pal.accent) }, { catMenu = false; Store.push(Route.Categories) })
          }
        }
      }
      FormGroup("怎么登录 / 用哪个身份", "可以多选：比如用邮箱注册、再绑了手机号，或者用微信、QQ 登录。") { IdentityToggles(ids) { ids = it } }
      FormGroup("备注") { Field(notes, { notes = it }, "两步验证、找回方式…", lines = 4) }
    }
  }
}

/** 更换图标 › 从 App Store 搜索: prefilled with the App's name and searching at once, later edits debounced
 *  like the add flow. Tapping a result hands it back; 取消 changes nothing. */
@Composable
private fun AppStoreIconDialog(initial: String, onDismiss: () -> Unit, onPick: (AppHit) -> Unit) {
  var q by remember { mutableStateOf(initial) }
  var hits by remember { mutableStateOf(listOf<AppHit>()) }
  var phase by remember { mutableStateOf("idle") }   // idle / loading / done / offline
  var first by remember { mutableStateOf(true) }
  val term = q.trim()
  LaunchedEffect(term) {
    if (term.isEmpty()) { hits = emptyList(); phase = "idle"; return@LaunchedEffect }
    phase = "loading"
    if (first) first = false else delay(350)   // a newer query cancels this one, so late results are dropped
    val r = AppStore.searchOrNull(term)
    if (r == null) { hits = emptyList(); phase = "offline" } else { hits = r.distinctBy { it.trackName }; phase = "done" }
  }
  if (AppLock.blocking) return
  GlassAlertDialog(onDismissRequest = onDismiss, title = { Text("从 App Store 搜索") },
    text = {
      Column {
        OutlinedTextField(q, { q = it }, label = { Text("App 名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        val msg = when {
          term.isEmpty() -> "输入 App 名称搜索"
          phase == "offline" -> "网络不可用"
          phase == "done" && hits.isEmpty() -> "没有找到"
          hits.isEmpty() -> "正在搜索…"
          else -> null
        }
        if (msg != null) T(msg, 14f, color = pal.secondary, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
        else Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
          hits.forEach { h ->
            Row(Modifier.fillMaxWidth().press(0.97f) { onPick(h) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
              HitThumb(h, 36.dp)
              Spacer(Modifier.width(12.dp))
              Column(Modifier.weight(1f)) {
                T(h.name, 16f, lines = 1)
                val sub = h.domain.ifEmpty { h.category }
                if (sub.isNotEmpty()) T(sub, 12f, color = pal.secondary, lines = 1)
              }
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onDismiss) { Text("取消") } })
}

// MARK: - add apps: pick sites, then pick which email / phone each uses

/** "更换图标" badge over an icon: tap opens 从相册选择 / 使用默认图标. */
@Composable
private fun IconBadge(modifier: Modifier = Modifier) {
  Box(modifier.size(22.dp).clip(CircleShape).background(pal.accent).border(2.dp, if (pal.dark) hex("#2c2c2e") else Color.White, CircleShape), contentAlignment = Alignment.Center) {
    Icon(Icons.Filled.PhotoCamera, null, Modifier.size(12.dp), tint = Color.White)
  }
}

@Composable
fun PickerSheet(presetIdentity: String?) {
  var query by remember { mutableStateOf("") }
  var cat by remember { mutableStateOf("常用") }
  val picked = remember { mutableStateListOf<Preset>() }
  val handles = remember { mutableStateMapOf<String, String>() }
  // during the 操作演示 the identity is picked by hand, as a step of its own
  var ids by remember { mutableStateOf(listOfNotNull(presetIdentity?.takeIf { !Tour.on })) }
  var step2 by remember { mutableStateOf(false) }
  var manual by remember { mutableStateOf(false) }
  val hap = haptics()
  fun key(p: Preset) = p.name.lowercase()
  val customList = Store.customAppList
  val custom = customList.map { it.preset }
  val customIcons = customList.mapNotNull { c -> c.icon?.let { c.preset.name.lowercase() to it } }.toMap()
  // App Store results for the search box, and the icons of the ones picked
  var hits by remember { mutableStateOf(listOf<AppHit>()) }
  val hitOf = remember { mutableStateMapOf<String, AppHit>() }
  val icons = remember { mutableStateMapOf<String, String>() }
  // icons the user chose on the handles step ("" = 使用默认图标)
  val photos = remember { mutableStateMapOf<String, String>() }
  var menuFor by remember { mutableStateOf<String?>(null) }      // long-pressed tile in 我添加的
  var confirmRemove by remember { mutableStateOf<Preset?>(null) }
  var iconMenuFor by remember { mutableStateOf<String?>(null) }  // handles step: 更换图标 menu
  var photoFor by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  val ctx = androidx.compose.ui.platform.LocalContext.current
  val accIcons = remember(Store.data.accounts) { Store.data.accounts.filter { it.icon != null }.associateBy { it.name.lowercase() } }
  fun ownIcon(p: Preset): String? = if (Presets.all.any { key(it) == key(p) }) null else accIcons[key(p)]?.icon ?: customIcons[key(p)]
  fun has(p: Preset) = Store.data.accounts.any { it.name.lowercase() == key(p) }
  LaunchedEffect(query.trim()) {
    val q = query.trim()
    if (q.isEmpty()) { hits = emptyList(); return@LaunchedEffect }
    delay(350)
    hits = AppStore.search(q)
  }
  LaunchedEffect(Unit) { Tour.advance("add", "pick") }
  LaunchedEffect(picked.size) { if (picked.isNotEmpty()) Tour.advance("pick", "next") else Tour.advance("next", "pick") }
  LaunchedEffect(step2) { if (step2) Tour.advance("next", "who") else if (Tour.step == "who" || Tour.step == "save") Tour.step = "next" }
  LaunchedEffect(ids) { if (ids.isNotEmpty()) Tour.advance("who", "save") else Tour.advance("save", "who") }
  // 我添加的 emptied: the tab goes away, back to 常用
  LaunchedEffect(custom.isEmpty()) { if (custom.isEmpty() && cat == "我添加的") cat = "常用" }
  val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    val k = photoFor; photoFor = null
    if (uri != null && k != null) scope.launch { CustomIcon.fromUri(ctx, uri)?.let { photos[k] = it; hap(TICK) } }
  }
  @Composable fun PickedIcon(p: Preset, size: androidx.compose.ui.unit.Dp) {
    val k = key(p); val h = hitOf[k]; val ph = photos[k]
    if (ph != null) AppIcon(p.name, p.domain, size, icon = ph.ifEmpty { null }, iconKey = "photo-$k")
    else if (icons[k] == null && h != null) HitThumb(h, size)
    else AppIcon(p.name, p.domain, size, icon = icons[k] ?: ownIcon(p), iconKey = if (icons[k] != null) "pick-$k" else "own-$k")
  }
  fun removeCustom(p: Preset) {
    hap(CONFIRM)
    picked.removeAll { key(it) == key(p) }
    Store.removeApp(p.name)
  }

  fun save() {
    val now = nowMS()
    val made = picked.mapIndexed { k, p ->
      val ph = photos[key(p)]
      Account(newID(), p.name, p.domain, Store.catOr(p.category), ids, handle = handles[key(p)]?.trim()?.ifEmpty { null }, createdAt = now - k,
        icon = if (ph != null) ph.ifEmpty { null } else icons[key(p)] ?: ownIcon(p))
    }
    Store.unhideApps(made.map { it.name })
    Store.mutate(if (made.size == 1) "已添加 ${made[0].name}" else "已添加 ${made.size} 个网站与 App") { s -> s.copy(accounts = s.accounts + made) }
    // App Store icons still downloading: attach them when they arrive
    made.filter { it.icon == null && photos[it.name.lowercase()] == null }.forEach { a ->
      val h = hitOf[a.name.lowercase()] ?: return@forEach
      Store.scope.launch {
        val ic = AppStore.icon(h) ?: return@launch
        if (Store.account(a.id)?.icon == null) { Store.data = Store.data.copy(accounts = Store.data.accounts.map { if (it.id == a.id && it.icon == null) it.copy(icon = ic) else it }); Store.save() }
      }
    }
    Store.pop()
    Tour.saved()
  }

  AnimatedContent(step2, transitionSpec = {
    if (targetState) (slideInHorizontally(calm()) { it } + fadeIn()) togetherWith (slideOutHorizontally(calm()) { -it / 3 } + fadeOut())
    else (slideInHorizontally(calm()) { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally(calm()) { it } + fadeOut())
  }, label = "step") { s2 ->
    if (!s2) {
      val cats = (if (custom.isEmpty()) emptyList() else listOf("我添加的")) + "常用" + Store.cats.filter { c -> Presets.all.any { Store.catOr(it.category) == c } }
      val q = query.trim().lowercase()
      val all = custom + Presets.all
      val mine = q.isEmpty() && cat == "我添加的"
      val list = if (q.isNotEmpty()) (all + Presets.directory).filter { siteHit(it, q) }.distinctBy { key(it) }.take(60) else when (cat) {
        "我添加的" -> custom
        // the first 12 most-used apps (3 rows of 4), custom apps only under 我添加的
        "常用" -> Presets.popular.take(12).mapNotNull { n -> Presets.all.firstOrNull { it.name == n } }
        else -> Presets.all.filter { Store.catOr(it.category) == cat }
      }
      Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
          SheetBar("取消", "添加网站或 App", if (picked.isEmpty()) "下一步" else "下一步 (${picked.size})", picked.isNotEmpty(), rightAnchor = "next") { step2 = true }
          SearchField(query, "搜索 App 或网站", Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp)) { query = it }
          Chips(cats.map { it to null }, if (q.isEmpty()) cat else null) { cat = it; query = "" }
          LazyVerticalGrid(GridCells.Fixed(4), Modifier.weight(1f).imePadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 14.dp, 12.dp, 110.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list, key = { "t-" + key(it) }) { p ->
              val k = key(p)
              val on = picked.any { key(it) == k }
              val recorded = has(p)
              // no icon of its own and none bundled (王者荣耀 …): borrow the App Store artwork of the same name
              val match = if (q.isEmpty() || ownIcon(p) != null || IconCache.preset(p.name, p.domain) != null) null else hits.firstOrNull { it.name.lowercase() == k }
              val sc by animateFloatAsState(if (on) 0.92f else 1f, calm(), label = "pk")
              Box(Modifier.animateItem(fadeInSpec = calm(), placementSpec = calm(), fadeOutSpec = calm()).then(if (p == list.firstOrNull()) Modifier.tourAnchor("tile") else Modifier), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.fillMaxWidth().press(1f, onLongClick = if (mine) ({ hap(TICK); menuFor = k }) else null) {
                  hap(TICK)
                  if (on) { picked.removeAll { key(it) == k }; icons.remove(k); hitOf.remove(k) }
                  else {
                    picked.add(p)
                    if (match != null) { hitOf[k] = match; scope.launch { AppStore.icon(match)?.let { if (picked.any { key(it) == k }) icons[k] = it } } }
                  }
                }, horizontalAlignment = Alignment.CenterHorizontally) {
                  Box {
                    if (match != null) HitThumb(match, 60.dp, Modifier.graphicsLayer { scaleX = sc; scaleY = sc })
                    else AppIcon(p.name, p.domain, 60.dp, Modifier.graphicsLayer { scaleX = sc; scaleY = sc }, ownIcon(p), "own-$k")
                    androidx.compose.animation.AnimatedVisibility(on, Modifier.align(Alignment.TopEnd).offset(7.dp, (-7).dp), enter = scaleIn(calm()) + fadeIn(), exit = scaleOut(calm()) + fadeOut()) {
                      Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White).padding(1.dp)) { Icon(Icons.Filled.CheckCircle, null, tint = pal.accent) }
                    }
                  }
                  T(p.name, 12f, lines = 1, modifier = Modifier.padding(top = 6.dp))
                  if (recorded) T("已记录", 9.5f, FontWeight.Medium, pal.secondary)
                }
                DropdownMenu(menuFor == k && mine, { menuFor = null }) {
                  DropdownMenuItem({ Text("从列表移除", color = Color(0xFFFF453A)) }, {
                    menuFor = null
                    if (recorded) confirmRemove = p else removeCustom(p)
                  }, leadingIcon = { Icon(Icons.Filled.RemoveCircleOutline, null, tint = Color(0xFFFF453A)) })
                }
              }
            }
            val names = list.map { key(it) }.toSet() + Presets.all.map { key(it) }
            val store = if (q.isEmpty()) emptyList() else hits.filter { it.name.lowercase() !in names }.distinctBy { it.name.lowercase() }
            if (store.isNotEmpty()) item(key = "as-head", span = { GridItemSpan(maxLineSpan) }) {
              T("App Store", 13f, FontWeight.Medium, pal.secondary, Modifier.padding(start = 6.dp, top = 4.dp))
            }
            items(store, key = { "as-" + it.name.lowercase() }) { h ->
              val p = Preset(h.name, h.domain, h.category, "")
              val k = key(p)
              val on = picked.any { key(it) == k }
              val sc by animateFloatAsState(if (on) 0.92f else 1f, calm(), label = "pk")
              Column(Modifier.press(1f) {
                hap(TICK)
                if (on) { picked.removeAll { key(it) == k }; icons.remove(k) }
                else {
                  picked.add(p); hitOf[k] = h
                  scope.launch { AppStore.icon(h)?.let { if (picked.any { key(it) == k }) icons[k] = it } }
                }
              }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                  HitThumb(h, 60.dp, Modifier.graphicsLayer { scaleX = sc; scaleY = sc })
                  androidx.compose.animation.AnimatedVisibility(on, Modifier.align(Alignment.TopEnd).offset(7.dp, (-7).dp), enter = scaleIn(calm()) + fadeIn(), exit = scaleOut(calm()) + fadeOut()) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White).padding(1.dp)) { Icon(Icons.Filled.CheckCircle, null, tint = pal.accent) }
                  }
                }
                T(h.name, 12f, lines = 1, modifier = Modifier.padding(top = 6.dp))
                T("App Store", 9.5f, FontWeight.Medium, pal.secondary)
              }
            }
            item(key = "manual") {
              Column(Modifier.animateItem(fadeInSpec = calm(), placementSpec = calm(), fadeOutSpec = calm()).press(0.9f) { manual = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(60.dp).glass(RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, null, tint = pal.primary) }
                T(if (query.isEmpty()) "手动添加" else "添加“$query”", 12f, color = pal.secondary, lines = 1, modifier = Modifier.padding(top = 6.dp))
              }
            }
          }
        }
        AnimatedVisibility(picked.isNotEmpty(), Modifier.align(Alignment.BottomCenter), enter = slideInVertically(calm()) { it } + fadeIn(), exit = slideOutVertically(calm()) { it } + fadeOut()) {
          Row(Modifier.fillMaxWidth().background(if (pal.dark) hex("#1c1c1e").copy(alpha = 0.96f) else Color.White.copy(alpha = 0.96f))
            .navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            picked.forEach { p -> PickedIcon(p, 38.dp) }
          }
        }
      }
      confirmRemove?.takeIf { !AppLock.blocking }?.let { p ->
        GlassAlertDialog(onDismissRequest = { confirmRemove = null }, title = { Text("从列表移除“${p.name}”？") },
          text = { Text("只会从这个列表移除，已记录的账号不受影响。") },
          confirmButton = { TextButton({ confirmRemove = null; removeCustom(p) }) { Text("移除", color = Color(0xFFFF453A)) } },
          dismissButton = { TextButton({ confirmRemove = null }) { Text("取消") } })
      }
      if (manual) ManualAdd(query, { manual = false }) { n, d, ic ->
        val p = Preset(n, cleanDomain(d), categoryFor(n, d), "")
        Store.rememberApp(p, ic)
        if (ic != null) icons[key(p)] = ic
        if (picked.none { key(it) == key(p) }) picked.add(p)
        query = ""
      }
    } else {
      Column {
        SheetBar("返回", "选择身份", "保存", onLeft = { step2 = false }, leftIcon = true, rightAnchor = "save") { save() }
        FormScroll {
          FormGroup("${picked.size} 个网站与 App", "同一个网站有几个账号，可以分别记一条，用账号名区分。点图标可以更换。") {
            picked.forEachIndexed { n, p ->
              if (n > 0) Divider()
              val k = key(p)
              Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                  Box(Modifier.press(0.92f) { iconMenuFor = k }) {
                    PickedIcon(p, 40.dp)
                    IconBadge(Modifier.align(Alignment.BottomEnd).offset(5.dp, 5.dp).size(18.dp))
                  }
                  DropdownMenu(iconMenuFor == k, { iconMenuFor = null }) {
                    DropdownMenuItem({ Text("从相册选择") }, {
                      iconMenuFor = null; photoFor = k
                      photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                    val ph = photos[k]
                    val custom0 = if (ph != null) ph.isNotEmpty() else (icons[k] != null || hitOf[k] != null || ownIcon(p) != null)
                    if (custom0) DropdownMenuItem({ Text("使用默认图标") }, { iconMenuFor = null; photos[k] = ""; hap(TICK) })
                  }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                  T(p.name, 16f, FontWeight.SemiBold)
                  Field(handles[k] ?: "", { handles[k] = it }, "账号名（可选）", size = 14f, raw = true)
                }
              }
            }
          }
          FormGroup("怎么登录 / 用哪个身份", "可以多选：比如用邮箱注册、再绑了手机号，或者用微信、QQ 登录。列表里没有的，点「新建身份」。") {
            IdentityToggles(ids) { ids = it }
          }
        }
      }
    }
  }
}

@Composable
fun ManualAdd(initial: String, onDismiss: () -> Unit, onAdd: (String, String, String?) -> Unit) {
  var n by remember { mutableStateOf(initial) }
  var d by remember { mutableStateOf("") }
  var icon by remember { mutableStateOf<String?>(null) }
  var iconMenu by remember { mutableStateOf(false) }
  var webHits by remember { mutableStateOf(listOf<SiteHit>()) }   // official sites found online for a name we don't know
  val scope = rememberCoroutineScope()
  val ctx = androidx.compose.ui.platform.LocalContext.current
  val hap = haptics()
  val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) scope.launch { CustomIcon.fromUri(ctx, uri)?.let { icon = it; hap(TICK) } }
  }
  // sites we know that match the name, so the user never has to look the domain up
  val nq = n.trim().lowercase()
  val localHits = if (nq.isEmpty() || cleanDomain(d).isNotEmpty()) emptyList() else
    (Presets.all + Presets.directory).filter { siteHit(it, nq) }.take(3).map { SiteHit(it.name, it.domain) }
  LaunchedEffect(nq) {
    webHits = emptyList()
    if (nq.isEmpty() || localHits.isNotEmpty() || cleanDomain(d).isNotEmpty()) return@LaunchedEffect
    delay(600)   // a newer name cancels this one
    webHits = SiteLookup.find(n.trim())
  }
  val domainHits = if (cleanDomain(d).isNotEmpty()) emptyList() else localHits.ifEmpty { webHits }
  if (AppLock.blocking) return
  GlassAlertDialog(onDismissRequest = onDismiss, title = { Text("手动添加") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          Box {
            Column(Modifier.press(0.92f) { iconMenu = true }, horizontalAlignment = Alignment.CenterHorizontally) {
              Box {
                AppIcon(n.trim().ifEmpty { "?" }, cleanDomain(d), 64.dp, icon = icon, iconKey = "manual")
                IconBadge(Modifier.align(Alignment.BottomEnd).offset(6.dp, 6.dp))
              }
              T("更换图标", 11f, FontWeight.Medium, pal.accent, Modifier.padding(top = 8.dp))
            }
            DropdownMenu(iconMenu, { iconMenu = false }) {
              DropdownMenuItem({ Text("从相册选择") }, {
                iconMenu = false
                photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
              })
              if (icon != null) DropdownMenuItem({ Text("使用默认图标") }, { iconMenu = false; icon = null; hap(TICK) })
            }
          }
        }
        Text(if (domainHits.isEmpty()) "名称必填。网址用来找图标，不知道可以不填。" else "不知道网址？点下面的官网就行。")
        OutlinedTextField(n, { n = it }, label = { Text("名称") }, singleLine = true)
        OutlinedTextField(d, { d = it }, label = { Text("网址（可选）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        domainHits.forEach { h ->
          Row(Modifier.fillMaxWidth().press(0.97f) { hap(TICK); d = h.domain }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(h.label, h.domain, 28.dp, iconKey = "site-${h.domain}")
            Spacer(Modifier.width(10.dp))
            T(h.label, 14f, lines = 1, modifier = Modifier.weight(1f))
            T(h.domain, 13f, color = pal.secondary, lines = 1)
          }
        }
      }
    },
    confirmButton = { TextButton({ if (n.isNotBlank()) { onAdd(n.trim(), d, icon); onDismiss() } }, enabled = n.isNotBlank()) { Text("添加") } },
    dismissButton = { TextButton(onDismiss) { Text("取消") } })
}
