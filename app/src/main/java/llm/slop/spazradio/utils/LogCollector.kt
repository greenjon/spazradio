package llm.slop.spazradio.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import llm.slop.spazradio.R

object LogCollector {

    private const val MAX_LINES = 2000
    private val PRESENCE = Regex("""(Published presence:).*""")

    /** Removes the chat nickname, which older log lines carry after "Published presence:". */
    fun redact(output: String): String =
        output.lineSequence().joinToString("\n") { PRESENCE.replace(it, "$1 <redacted>") }

    /**
     * Collects this app's recent logcat output and returns a URI to the log file.
     * Blocking: call it off the main thread.
     */
    fun collectLogs(context: Context): Uri? {
        val logFile = File(context.cacheDir, "spaz_radio_debug_logs.txt")
        if (logFile.exists()) logFile.delete()

        try {
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-t", MAX_LINES.toString(), "-v", "time", "--pid=${Process.myPid()}")
            )
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.destroy()

            logFile.writeText(
                "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "--------------------------------------------------\n\n" +
                redact(output)
            )

            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                logFile
            )
        } catch (e: Exception) {
            Log.e("LogCollector", "Failed to collect logs", e)
            return null
        }
    }

    /** Collects the logs on the IO dispatcher, then opens the share sheet. */
    suspend fun shareLogs(context: Context) {
        val logUri = withContext(Dispatchers.IO) { collectLogs(context) } ?: return

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.send_logs_subject))
            putExtra(Intent.EXTRA_STREAM, logUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, context.getString(R.string.send_logs_chooser)))
    }
}
