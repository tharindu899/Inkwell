package com.inkwell.notes.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** What the update sheet needs from a GitHub release. */
data class ReleaseInfo(
    val tag: String,
    val htmlUrl: String,
    val body: String,
    val apkName: String?,
    val apkUrl: String?,
    val apkSize: Long,
    val downloadCount: Int,
) {
    /** `tag_name` without the leading "v" (web: `remoteVersion`). */
    val version: String get() = tag.replace(Regex("^[vV]"), "").ifBlank { "latest" }
}

class GithubHttpException(val status: Int) : Exception("GitHub $status")

/** Port of the GitHub half of `UpdateChecker.jsx`. */
object UpdateApi {
    /** `/releases/latest`, falling back to the newest published release (web `fetchLatestRelease`). */
    suspend fun fetchLatest(repo: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        val base = "https://api.github.com/repos/$repo"
        try {
            parseRelease(JSONObject(get("$base/releases/latest")))
        } catch (first: Exception) {
            try {
                val arr = JSONArray(get("$base/releases?per_page=10"))
                val list = (0 until arr.length()).map { arr.getJSONObject(it) }.filter { !it.optBoolean("draft") }
                val pick = list.firstOrNull { findApk(it) != null } ?: list.firstOrNull()
                pick?.let(::parseRelease)
            } catch (second: Exception) {
                throw if (second is GithubHttpException || first !is GithubHttpException) second else first
            }
        }
    }

    private fun findApk(release: JSONObject): JSONObject? {
        val assets = release.optJSONArray("assets") ?: return null
        return (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").lowercase().endsWith(".apk") }
    }

    private fun parseRelease(o: JSONObject): ReleaseInfo {
        val apk = findApk(o)
        return ReleaseInfo(
            tag = o.optString("tag_name").ifBlank { o.optString("name") },
            htmlUrl = o.optString("html_url"),
            body = o.optString("body"),
            apkName = apk?.optString("name"),
            apkUrl = apk?.optString("browser_download_url"),
            apkSize = apk?.optLong("size") ?: 0L,
            downloadCount = apk?.optInt("download_count") ?: 0,
        )
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        try {
            val code = c.responseCode
            if (code !in 200..299) throw GithubHttpException(code)
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }

    // ─── semver (web parseSemver / isNewer) ───

    fun parseSemver(tag: String): List<Int> {
        val nums = Regex("\\d+").findAll(tag.replace(Regex("^[vV]"), "")).map { it.value.toIntOrNull() ?: 0 }.toList()
        return List(3) { nums.getOrElse(it) { 0 } }
    }

    fun isNewer(remote: String, local: String): Boolean {
        val r = parseSemver(remote)
        val l = parseSemver(local)
        for (i in 0 until 3) {
            if (r[i] > l[i]) return true
            if (r[i] < l[i]) return false
        }
        return false
    }
}
