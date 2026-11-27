package dev.huichen.atlas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * Keeps the stack trace of the last crash on this device (no network, nothing sent anywhere) and offers it
 * once on the next launch, so a tester without adb can copy it and send it over.
 */
object CrashLog {
  private var file: File? = null
  var last by mutableStateOf<String?>(null)
    private set

  fun init(ctx: Context) {
    val f = File(ctx.filesDir, "last_crash.txt"); file = f
    last = runCatching { f.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } }.getOrNull()
    val prev = Thread.getDefaultUncaughtExceptionHandler()
    if (prev is Handler) return
    Thread.setDefaultUncaughtExceptionHandler(Handler(ctx.applicationContext, f, prev))
  }

  fun dismiss() { last = null; runCatching { file?.delete() } }

  private class Handler(val ctx: Context, val f: File, val prev: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
      runCatching {
        val ver = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
        f.writeText("账号星图 $ver · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
          "线程 ${t.name}\n" + e.stackTraceToString().take(12000))
      }
      prev?.uncaughtException(t, e)
    }
  }
}

@Composable
fun CrashLogDialog() {
  val text = CrashLog.last ?: return
  val ctx = LocalContext.current
  AlertDialog(onDismissRequest = { CrashLog.dismiss() },
    title = { Text("上次意外退出了") },
    text = {
      androidx.compose.foundation.layout.Column {
        Text("可以复制下面的错误信息发给开发者，帮助修复。信息里没有你的账号数据。")
        Text(text, Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()), fontSize = 11.sp, color = pal.secondary)
      }
    },
    confirmButton = { TextButton({
      (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("账号星图错误信息", text))
      Store.show("已复制"); CrashLog.dismiss()
    }) { Text("复制") } },
    dismissButton = { TextButton({ CrashLog.dismiss() }) { Text("关闭") } })
}
