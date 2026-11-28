package com.inkwell.notes.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Google Drive REST v3, appDataFolder only (hidden from the user's My Drive).
 * Same file name and flow as the web app: inkwell-sync.json.
 */
object DriveApi {
    private const val DRIVE = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    private const val FILENAME = "inkwell-sync.json"
    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    data class SyncFile(val id: String, val modifiedTime: String?)

    private fun Request.Builder.bearer(token: String) = header("Authorization", "Bearer $token")

    private fun run(req: Request, label: String): String =
        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IOException("[Drive] $label failed (${res.code})")
            body
        }

    suspend fun find(token: String): SyncFile? = withContext(Dispatchers.IO) {
        val url = "$DRIVE/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name='$FILENAME' and trashed=false")
            .addQueryParameter("fields", "files(id,name,modifiedTime,size)")
            .addQueryParameter("pageSize", "1")
            .build()
        val files = JSONObject(run(Request.Builder().url(url).bearer(token).build(), "list")).optJSONArray("files")
        if (files == null || files.length() == 0) null
        else files.getJSONObject(0).let { SyncFile(it.getString("id"), it.optString("modifiedTime").ifBlank { null }) }
    }

    /** Creates or updates the backup. Returns the file's modifiedTime (ISO) if Drive sent it. */
    suspend fun backup(token: String, payload: String): String? = withContext(Dispatchers.IO) {
        val existing = find(token)
        val media = payload.toRequestBody(JSON)
        val req = if (existing != null) {
            Request.Builder()
                .url("$UPLOAD/files/${existing.id}?uploadType=media&fields=id,modifiedTime")
                .bearer(token).patch(media).build()
        } else {
            val meta = JSONObject().put("name", FILENAME).put("parents", JSONArray().put("appDataFolder"))
                .toString().toRequestBody(JSON)
            val multi = MultipartBody.Builder().setType("multipart/related".toMediaType()).addPart(meta).addPart(media).build()
            Request.Builder().url("$UPLOAD/files?uploadType=multipart&fields=id,modifiedTime")
                .bearer(token).post(multi).build()
        }
        JSONObject(run(req, if (existing != null) "update" else "create")).optString("modifiedTime").ifBlank { null }
    }

    /** Returns (json text, modifiedTime) or null if there is no backup yet. */
    suspend fun restore(token: String): Pair<String, String?>? = withContext(Dispatchers.IO) {
        val file = find(token) ?: return@withContext null
        val text = run(Request.Builder().url("$DRIVE/files/${file.id}?alt=media").bearer(token).build(), "download")
        text to file.modifiedTime
    }
}
