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
            connectTimeout = 20_000
            readTimeout = 120_000
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

    /** All `inkwell-sync.json` files in appData (normally one), newest first. */
    private suspend fun listSyncFiles(token: String): List<DriveFile> = withContext(Dispatchers.IO) {
        val q = enc("name='$FILENAME' and trashed=false")
        val url = "$DRIVE/files?spaces=appDataFolder&q=$q&orderBy=modifiedTime%20desc" +
            "&fields=${enc("files(id,name,modifiedTime,size)")}&pageSize=20"
        val arr = JSONObject(open(url, token, "GET").readOk("list")).optJSONArray("files")
        buildList {
            if (arr != null) for (i in 0 until arr.length()) {
                val it = arr.getJSONObject(i)
                add(DriveFile(it.getString("id"), it.optString("modifiedTime", ""), it.optString("size", "0").toLongOrNull() ?: 0L))
            }
        }
    }

    /**
     * Saves [payload] as `inkwell-sync.json`. Always creates a fresh file and then deletes the old ones:
     * `HttpURLConnection` has no PATCH, and the override-header trick is unreliable on the upload endpoint.
     * (Compact JSON — photos are base64 inside the notes, so pretty-printing would only bloat it.)
     */
    suspend fun backup(token: String, payload: JSONObject) {
        val old = listSyncFiles(token)
        val body = payload.toString().toByteArray(Charsets.UTF_8)
        val meta = JSONObject().put("name", FILENAME).put("parents", org.json.JSONArray().put("appDataFolder"))
        val head = ("--$BOUNDARY\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + meta.toString() +
            "\r\n--$BOUNDARY\r\nContent-Type: application/json\r\n\r\n").toByteArray(Charsets.UTF_8)
        val tail = "\r\n--$BOUNDARY--".toByteArray(Charsets.UTF_8)
        val multipart = head + body + tail
        withContext(Dispatchers.IO) {
            val conn = open("$UPLOAD/files?uploadType=multipart&fields=id", token, "POST")
            conn.setFixedLengthStreamingMode(multipart.size)
            conn.send("multipart/related; boundary=$BOUNDARY", multipart)
            val newId = JSONObject(conn.readOk("create")).optString("id", "")
            // Only after the new copy is safely stored do the old ones go.
            for (f in old) {
                if (f.id == newId) continue
                runCatching { open("$DRIVE/files/${f.id}", token, "DELETE").readOk("delete") }
            }
        }
    }

    /** The backup JSON, or null when no backup exists yet. */
    suspend fun restore(token: String): JSONObject? {
        val file = listSyncFiles(token).firstOrNull() ?: return null
        return withContext(Dispatchers.IO) {
            JSONObject(open("$DRIVE/files/${file.id}?alt=media", token, "GET").readOk("download"))
        }
    }
}
