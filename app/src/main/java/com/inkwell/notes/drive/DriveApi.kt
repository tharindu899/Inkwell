package com.inkwell.notes.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** `{ id, modifiedTime, size }` of the sync file. */
data class DriveFile(val id: String, val modifiedTime: String, val size: Long)

class DriveHttpException(val code: Int, label: String, body: String) :
    Exception("[Drive] $label → $code: $body")

/**
 * Port of `src/auth/googleDrive.js` — Drive REST v3 over plain HttpURLConnection.
 * Uses the hidden `appDataFolder`, so the backup never shows up in My Drive.
 */
object DriveApi {
    private const val DRIVE = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    private const val FILENAME = "inkwell-sync.json"
    private const val BOUNDARY = "inkwell_boundary_7f3a"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun open(url: String, token: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            // HttpURLConnection has no PATCH → Google honours the override header on POST.
            if (method == "PATCH") {
                requestMethod = "POST"
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                requestMethod = method
            }
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15_000
            readTimeout = 30_000
        }

    private fun HttpURLConnection.send(contentType: String, body: ByteArray) {
        doOutput = true
        setRequestProperty("Content-Type", contentType)
        outputStream.use { it.write(body) }
    }

    /** Reads the body, throws [DriveHttpException] on non-2xx (`expectOk`). */
    private fun HttpURLConnection.readOk(label: String): String {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        disconnect()
        if (code !in 200..299) throw DriveHttpException(code, label, text)
        return text
    }

    suspend fun findSyncFile(token: String): DriveFile? = withContext(Dispatchers.IO) {
        val q = enc("name='$FILENAME' and trashed=false")
        val url = "$DRIVE/files?spaces=appDataFolder&q=$q&fields=${enc("files(id,name,modifiedTime,size)")}&pageSize=1"
        val json = JSONObject(open(url, token, "GET").readOk("list"))
        json.optJSONArray("files")?.optJSONObject(0)?.let {
            DriveFile(it.getString("id"), it.optString("modifiedTime", ""), it.optString("size", "0").toLongOrNull() ?: 0L)
        }
    }

    /** Creates or updates `inkwell-sync.json` with [payload]. */
    suspend fun backup(token: String, payload: JSONObject) {
        val existing = findSyncFile(token)
        val body = payload.toString(2).toByteArray()
        withContext(Dispatchers.IO) {
            if (existing != null) {
                open("$UPLOAD/files/${existing.id}?uploadType=media", token, "PATCH")
                    .apply { send("application/json", body) }
                    .readOk("update")
            } else {
                val meta = JSONObject().put("name", FILENAME).put("parents", org.json.JSONArray().put("appDataFolder"))
                val multipart = buildString {
                    append("--$BOUNDARY\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
                    append(meta.toString())
                    append("\r\n--$BOUNDARY\r\nContent-Type: application/json\r\n\r\n")
                    append(String(body, Charsets.UTF_8))
                    append("\r\n--$BOUNDARY--")
                }.toByteArray()
                open("$UPLOAD/files?uploadType=multipart", token, "POST")
                    .apply { send("multipart/related; boundary=$BOUNDARY", multipart) }
                    .readOk("create")
            }
        }
    }

    /** The backup JSON, or null when no backup exists yet. */
    suspend fun restore(token: String): JSONObject? {
        val file = findSyncFile(token) ?: return null
        return withContext(Dispatchers.IO) {
            JSONObject(open("$DRIVE/files/${file.id}?alt=media", token, "GET").readOk("download"))
        }
    }
}
