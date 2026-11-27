package com.inkwell.notes.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Native replacement for the Capacitor `ApkInstaller` plugin: downloads the release APK into
 * the app cache and hands it to the Android Package Installer (the system confirmation
 * screen can't be skipped). Temp files are removed before the next download and on app start.
 */
class ApkInstaller(context: Context) {
    private val app = context.applicationContext
    private val dir get() = File(app.cacheDir, "apk")

    fun cleanup() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    /** "Install unknown apps" allowed for Inkwell? (always true below Android 8) */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || app.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(i) }
    }

    /** Streams [url] to cache. [onProgress] gets (loaded, total); total = 0 when unknown. */
    suspend fun download(url: String, fileName: String, fallbackTotal: Long, onProgress: (Long, Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            cleanup()
            dir.mkdirs()
            val out = File(dir, safeFileName(fileName))
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = true
            try {
                if (c.responseCode !in 200..299) error("download blocked (${c.responseCode})")
                val total = c.contentLengthLong.takeIf { it > 0 } ?: fallbackTotal
                var loaded = 0L
                c.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            loaded += n
                            onProgress(loaded, total)
                        }
                    }
                }
            } catch (e: Exception) {
                out.delete()
                throw e
            } finally {
                c.disconnect()
            }
            out
        }

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(i)
    }

    private fun safeFileName(name: String): String {
        val clean = name.replace(Regex("[^a-zA-Z0-9._-]+"), "-").replace(Regex("-+"), "-")
        return if (clean.lowercase().endsWith(".apk")) clean else "Inkwell-update.apk"
    }
}
