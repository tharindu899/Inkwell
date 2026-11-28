package com.inkwell.notes.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class UpdateInfo(val version: String, val notes: String, val url: String)

/** Looks for a newer GitHub Release of this repo (the APK your workflow publishes). */
object UpdateChecker {
    private val client = OkHttpClient()

    suspend fun latest(repo: String): UpdateInfo = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("GitHub ${res.code}")
            val o = JSONObject(res.body?.string().orEmpty())
            val assets = o.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
                }
            }
            UpdateInfo(o.optString("tag_name").removePrefix("v"), o.optString("body"), apk ?: o.optString("html_url"))
        }
    }

    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
