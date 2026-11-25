package dev.huichen.atlas

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 2.6 应用锁 (off by default). Unlocks with BiometricPrompt: fingerprint / face (strong or weak) or the
 * screen-lock PIN / pattern / password. BIOMETRIC_STRONG | BIOMETRIC_WEAK | DEVICE_CREDENTIAL is the same
 * bit set as BIOMETRIC_WEAK | DEVICE_CREDENTIAL, a combination the library supports on every API level
 * (STRONG | DEVICE_CREDENTIAL alone is not supported on API 28-29). No negative button: with
 * DEVICE_CREDENTIAL allowed the library forbids one.
 *
 * - launch: locked; back from the background after the chosen delay (立即 / 1 分钟 / 5 分钟): locked again
 * - onPause with the lock on: the screen is covered (privacy in the app switcher). No FLAG_SECURE, so
 *   screenshots keep working; on API 33+ the recents thumbnail is turned off while the lock is on.
 * - with the lock on, the eye (and turning 默认隐藏账号 off) needs one authentication per foreground session
 */
object AppLock {
  const val AUTHENTICATORS = BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL
  private lateinit var prefs: SharedPreferences

  var enabled by mutableStateOf(false); private set
  /** seconds in the background before it locks again: 0 (立即), 60, 300 */
  var relockSec by mutableIntStateOf(0); private set
  var locked by mutableStateOf(false); private set
  var covered by mutableStateOf(false); private set
  var resumed by mutableStateOf(false); private set
  /** bumped on every resume, so availability (a screen lock added in system settings) is read again */
  var resumeTick by mutableIntStateOf(0); private set
  /** the eye asked for authentication already in this foreground session */
  var revealOK = false; private set
  private var authing = false
  /** our own file picker / share sheet is opening: coming back from it is not "returning to the app" */
  private var keepUnlocked = false
  private var autoAsked = false
  private var bgAt = 0L

  val blocking: Boolean get() = enabled && (locked || covered)
  val needsAuthToReveal: Boolean get() = enabled && !revealOK

  fun init(ctx: Context) {
    prefs = ctx.getSharedPreferences("atlas", Context.MODE_PRIVATE)
    enabled = prefs.getBoolean("atlas.lock", false)
    relockSec = prefs.getInt("atlas.lockAfter", 0).takeIf { it in RELOCK.map { r -> r.first } } ?: 0
    if (enabled) lock()
  }

  val RELOCK = listOf(0 to "立即", 60 to "1 分钟", 300 to "5 分钟")
  fun relockLabel() = RELOCK.firstOrNull { it.first == relockSec }?.second ?: "立即"

  /** a screen lock (PIN / pattern / password, maybe with fingerprint or face) is set up */
  fun available(ctx: Context): Boolean =
    runCatching { BiometricManager.from(ctx).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS }.getOrDefault(false)

  private fun lock() { locked = true; autoAsked = false; revealOK = false }

  // MARK: activity lifecycle (MainActivity)
  fun onPause() { resumed = false; if (enabled) covered = true }
  fun onStop() {
    // the PIN / pattern screen of our own prompt is another activity: that is not leaving the app
    if (authing || keepUnlocked) return
    bgAt = System.currentTimeMillis(); revealOK = false
  }
  fun onResume() {
    if (enabled && bgAt > 0 && System.currentTimeMillis() - bgAt >= relockSec * 1000L) lock()
    bgAt = 0L; keepUnlocked = false; covered = false; resumed = true; resumeTick++
  }
  fun expectReturn() { keepUnlocked = true }
  fun applyRecents(act: FragmentActivity) {
    if (Build.VERSION.SDK_INT >= 33) runCatching { act.setRecentsScreenshotEnabled(!enabled) }
  }

  /** shows the system prompt; onResult(true) only after a successful authentication */
  fun authenticate(act: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
    if (authing) return
    val info = runCatching {
      BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle("验证指纹、面部或锁屏密码")
        .setAllowedAuthenticators(AUTHENTICATORS).build()
    }.getOrNull() ?: return onResult(false)
    val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(act), object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { authing = false; onResult(true) }
      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { authing = false; onResult(false) }
      // onAuthenticationFailed (one wrong finger): the prompt stays up and lets them try again
    })
    authing = true
    runCatching { prompt.authenticate(info) }.onFailure { authing = false; onResult(false) }
  }

  /** for actions that always ask (导出备份). With no screen lock on the device there is nothing to ask. */
  fun require(act: FragmentActivity, title: String, ok: () -> Unit) {
    if (!available(act)) { ok(); return }
    authenticate(act, title) { if (it) ok() }
  }

  fun unlock(act: FragmentActivity) {
    if (!locked) return
    if (!available(act)) {
      // the screen lock was removed in system settings: never shut the owner out of their own data
      locked = false; revealOK = true
      Store.show("系统锁屏密码已关闭，应用锁暂时不起作用")
      return
    }
    authenticate(act, "解锁账号星图") { ok -> if (ok) { locked = false; revealOK = true } }
  }

  /** the lock screen asks by itself once each time it appears (not again after a cancel: there's the button) */
  fun autoUnlock(act: FragmentActivity) { if (locked && resumed && !autoAsked) { autoAsked = true; unlock(act) } }

  /** the eye / 默认隐藏账号 off: once per foreground session while the lock is on */
  fun reveal(act: FragmentActivity?, ok: () -> Unit) {
    if (!needsAuthToReveal) { ok(); return }
    if (act == null) return
    if (!available(act)) { ok(); return }
    authenticate(act, "查看完整账号") { if (it) { revealOK = true; ok() } }
  }

  /** turning it on or off asks for the screen lock first */
  fun setEnabled(act: FragmentActivity, on: Boolean) {
    if (on == enabled) return
    if (on && !available(act)) { Store.show("请先在系统设置中设置锁屏密码"); return }
    val apply = {
      enabled = on; revealOK = true
      prefs.edit().putBoolean("atlas.lock", on).apply()
      applyRecents(act)
      Store.show(if (on) "应用锁已开启" else "应用锁已关闭")
    }
    if (!available(act)) { apply(); return }
    authenticate(act, if (on) "开启应用锁" else "关闭应用锁") { if (it) apply() }
  }
  fun setRelock(sec: Int) { relockSec = sec; prefs.edit().putInt("atlas.lockAfter", sec).apply() }

  /** One plain line, once ever: with 3+ of their own identities / apps and the lock off, suggest it.
   *  Tapping it opens 设置. No address, number or name in it. Returns true when it was shown. */
  fun maybeTip(ctx: Context): Boolean {
    if (enabled || prefs.getBoolean("atlas.lockTip", false) || Store.firstRun || Store.recovered || Store.sandbox || Tour.on) return false
    val d = Store.data
    val demo = Demo.ids(d)
    val n = d.identities.count { it.id !in demo } + d.accounts.count { it.id !in demo }
    if (n < 3 || !available(ctx)) return false
    prefs.edit().putBoolean("atlas.lockTip", true).apply()
    Store.tip("建议开启应用锁，保护你的账号信息") { Store.popAll(); Store.push(Route.Settings) }
    return true
  }
}

fun Context.fragmentActivity(): FragmentActivity? {
  var c: Context? = this
  while (c is ContextWrapper) { if (c is FragmentActivity) return c; c = c.baseContext }
  return null
}

/** The lock screen (locked) or the plain cover (app switcher). Drawn over everything in the app. */
@Composable
fun LockOverlay() {
  if (!AppLock.blocking) return
  val act = LocalContext.current.fragmentActivity()
  val locked = AppLock.locked
  LaunchedEffect(locked, AppLock.resumed) { if (locked && AppLock.resumed && act != null) AppLock.autoUnlock(act) }
  Box(Modifier.fillMaxSize().background(pal.bg).clickable(remember { MutableInteractionSource() }, null) {}, contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
      Image(painterResource(R.mipmap.ic_launcher_flat), null, Modifier.size(76.dp).clip(RoundedCornerShape(17.dp)))
      Spacer(Modifier.height(16.dp))
      T("账号星图", 20f, FontWeight.SemiBold)
      if (locked) {
        Spacer(Modifier.height(6.dp))
        T("已锁定", 14f, color = pal.secondary)
        Spacer(Modifier.height(28.dp))
        Box(Modifier.clip(RoundedCornerShape(14.dp)).background(pal.accent).press(0.96f) { act?.let { AppLock.unlock(it) } }
          .padding(horizontal = 36.dp, vertical = 13.dp), contentAlignment = Alignment.Center) {
          androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = androidx.compose.ui.graphics.Color.White)
            Spacer(Modifier.size(8.dp))
            T("解锁", 17f, FontWeight.SemiBold, androidx.compose.ui.graphics.Color.White)
          }
        }
      }
    }
  }
}
