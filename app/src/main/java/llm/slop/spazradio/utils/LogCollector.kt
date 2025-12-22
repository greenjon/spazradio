package llm.slop.spazradio.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

object LogCollector {

    /**
     * Collects recent logcat output and returns a URI to the log file.
     */
    fun collectLogs(context: Context): Uri? {
        val logFile = File(context.cacheDir, "spaz_radio_debug_logs.txt")
        if (logFile.exists()) logFile.delete()

        try {
            // Run logcat command to get the last 500 lines of logs
            val process = Runtime.getRuntime().exec("logcat -d -v time")
            val output = process.inputStream.bufferedReader().readText()
            
            // Filter logs to only include our package if needed, 
            // but usually raw logcat is better for system errors like ECONNREFUSED.
            logFile.writeText(
                "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "--------------------------------------------------\n\n" +
                output
            )

            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                logFile
            )
        } catch (e: IOException) {
            Log.e("LogCollector", "Failed to collect logs", e)
            return null
        }
    }

    fun shareLogs(context: Context) {
        val logUri = collectLogs(context) ?: return

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SpazRadio Debug Logs")
            putExtra(Intent.EXTRA_STREAM, logUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Send Logs via..."))
    }
}
