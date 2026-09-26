package com.respondent.pro

import android.app.Application
import android.content.Context
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltAndroidApp
class RespondentApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashHandler(this)
    }

    companion object {
        private const val TAG = "RespondentApp"
        private const val CRASH_FILE = "crash_log.txt"

        fun installCrashHandler(context: Context) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    val sw = StringWriter()
                    throwable.printStackTrace(PrintWriter(sw))
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                    val log = """
                        |=== CRASH $timestamp ===
                        |Thread: ${thread.name}
                        |Exception: ${throwable.javaClass.name}
                        |Message: ${throwable.message}
                        |
                        |Stack trace:
                        |$sw
                        |
                    """.trimMargin()

                    Log.e(TAG, log)

                    // Write to file
                    val file = File(context.filesDir, CRASH_FILE)
                    file.appendText(log + "\n\n")

                    // Also try external storage for easy adb pull
                    try {
                        val extFile = File(context.getExternalFilesDir(null), CRASH_FILE)
                        extFile.appendText(log + "\n\n")
                    } catch (_: Exception) {}
                } catch (_: Exception) {}

                // Pass to default handler (shows system crash dialog)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        fun getCrashLog(context: Context): String {
            return try {
                val file = File(context.filesDir, CRASH_FILE)
                if (file.exists()) file.readText() else "No crash log found"
            } catch (e: Exception) {
                "Error reading crash log: ${e.message}"
            }
        }

        fun clearCrashLog(context: Context) {
            try {
                File(context.filesDir, CRASH_FILE).delete()
                File(context.getExternalFilesDir(null), CRASH_FILE).delete()
            } catch (_: Exception) {}
        }
    }
}
