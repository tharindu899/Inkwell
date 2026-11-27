package com.inkwell.notes.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inkwell.notes.R
import com.inkwell.notes.auth.TokenProvider
import com.inkwell.notes.data.AppPrefs
import com.inkwell.notes.data.EditorFontSize
import com.inkwell.notes.data.NotesStore
import com.inkwell.notes.data.model.Note
import com.inkwell.notes.data.model.Notebook
import com.inkwell.notes.drive.DriveApi
import com.inkwell.notes.drive.DriveHttpException
import com.inkwell.notes.ui.components.ToastState
import com.inkwell.notes.util.Iso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Confirm dialogs on the Settings page (`confirm` state in Settings.jsx). */
enum class SettingsConfirm { RestoreDrive, ClearNotes, ResetAll }

/** `calcStorage()` */
data class StorageInfo(val kb: String = "0.0", val mb: String = "0.00", val percent: Float = 2f)

data class SettingsState(
    // Editor toggles (`iw_prefs`)
    val spellcheck: Boolean = true,
    val autosave: Boolean = true,
    val markdownMode: Boolean = true,
    val serifBody: Boolean = false,
    val fontSize: EditorFontSize = EditorFontSize.Medium,

    // Stats / storage
    val noteCount: Int = 0,
    val notebookCount: Int = 0,
    val tagCount: Int = 0,
    val storage: StorageInfo = StorageInfo(),

    // Drive sync — UI state only; the real calls arrive with the Drive-sync step.
    val backupBusy: Boolean = false,
    val restoreBusy: Boolean = false,
    /** Already formatted ("Nov 25, 2026, 12:26 PM"), null = "No backup yet". */
    val lastBackup: String? = null,
    val syncError: String? = null,

    val confirm: SettingsConfirm? = null,
    val toast: ToastState? = null,
)

/** Kotlin side of the handlers in `pages/Settings.jsx`. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val store = NotesStore(application)
    private val tokens = TokenProvider(application)

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads prefs + counts + storage size (web: `setStorageInfo(calcStorage())` after every change). */
    fun refresh() {
        val notes = store.getNotes()
        _state.update {
            it.copy(
                spellcheck = AppPrefs.getPref(app, "spellcheck", true),
                autosave = AppPrefs.getPref(app, "autosave", true),
                markdownMode = AppPrefs.getPref(app, "markdownView", true),
                serifBody = AppPrefs.getPref(app, "serifBody", false),
                fontSize = AppPrefs.getFontSize(app),
                noteCount = notes.size,
                notebookCount = store.getNotebooks().size,
                tagCount = notes.flatMap { n -> n.tags }.toSet().size,
                storage = calcStorage(),
            )
        }
    }

    // ─── Appearance / Editor ─────────────────────

    fun setFontSize(size: EditorFontSize) {
        AppPrefs.setFontSize(app, size)
        refresh()
        showToast("Font size: ${size.key}", R.drawable.ic_fa_text_height)
    }

    /** Toggle id = the same key the web app saves in `iw_prefs`. */
    fun setPref(id: String, value: Boolean) {
        AppPrefs.setPref(app, id, value)
        refresh()
    }

    fun themeChanged(label: String, icon: Int) {
        showToast("$label theme on", icon)
    }

    // ─── Drive sync + update check (wired in their own steps) ───

    /** Runs [block] with a valid token; one forced refresh + retry on HTTP 401. */
    private suspend fun <T> withToken(block: suspend (String) -> T): T = try {
        block(tokens.getToken())
    } catch (e: DriveHttpException) {
        if (e.code == 401) block(tokens.getToken(forceRefresh = true)) else throw e
    }

    /** Load "Last backup" when the page opens — failures are silent (web: `catch {}`). */
    fun loadBackupInfo() {
        viewModelScope.launch {
            runCatching { withToken { DriveApi.findSyncFile(it) } }
                .onSuccess { f -> _state.update { it.copy(lastBackup = f?.let { x -> fmtDate(x.modifiedTime) }) } }
        }
    }

    /** `handleBackup` */
    fun backup() {
        if (_state.value.backupBusy || _state.value.restoreBusy) return
        _state.update { it.copy(backupBusy = true, syncError = null) }
        viewModelScope.launch {
            try {
                val profile = runCatching { JSONObject(AppPrefs.of(app).getString(AppPrefs.KEY_PROFILE, null) ?: "{}") }
                    .getOrDefault(JSONObject())
                val payload = withContext(Dispatchers.Default) {
                    JSONObject()
                        .put("version", 2)
                        .put("backedUpAt", Iso.now())
                        .put("notes", JSONArray(store.getNotes().map { it.toJson() }))
                        .put("notebooks", JSONArray(store.getNotebooks().map { it.toJson() }))
                        .put("profile", profile)
                }
                withToken { DriveApi.backup(it, payload) }
                showToast("Backed up to Google Drive", R.drawable.ic_fa_cloud_arrow_up)
                loadBackupInfo()
            } catch (e: Exception) {
                _state.update { it.copy(syncError = e.message ?: "Backup failed") }
                showToast("Backup failed", R.drawable.ic_fa_circle_exclamation)
            } finally {
                _state.update { it.copy(backupBusy = false) }
            }
        }
    }

    /** Confirmed "Restore from Drive" — replaces local notes / notebooks with the backup. */
    private fun doRestore() {
        _state.update { it.copy(restoreBusy = true, syncError = null) }
        viewModelScope.launch {
            try {
                val data = withToken { DriveApi.restore(it) }
                if (data == null) {
                    showToast("No backup found on Drive", R.drawable.ic_fa_circle_info)
                } else {
                    withContext(Dispatchers.IO) {
                        data.optJSONArray("notes")?.let { arr ->
                            store.saveNotes((0 until arr.length()).mapNotNull { Note.fromJson(arr.getJSONObject(it)) })
                        }
                        data.optJSONArray("notebooks")?.let { arr ->
                            store.saveNotebooks((0 until arr.length()).mapNotNull { Notebook.fromJson(arr.getJSONObject(it)) })
                        }
                    }
                    refresh()
                    withContext(Dispatchers.IO) { runCatching { com.inkwell.notes.reminder.Reminders.rescheduleAll(app) } }
                    val n = data.optJSONArray("notes")?.length() ?: 0
                    showToast("Restored $n notes", R.drawable.ic_fa_cloud_arrow_down)
                }
            } catch (e: Exception) {
                _state.update { it.copy(syncError = e.message ?: "Restore failed") }
                showToast("Restore failed", R.drawable.ic_fa_circle_exclamation)
            } finally {
                _state.update { it.copy(restoreBusy = false) }
            }
        }
    }

    // ─── Local export / import ───────────────────

    fun exportFileName(): String =
        "inkwell-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json"

    /** Writes the JSON backup to [uri] (picked with the system "Save as" dialog). */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val profile = runCatching { JSONObject(AppPrefs.of(app).getString(AppPrefs.KEY_PROFILE, null) ?: "{}") }
                        .getOrDefault(JSONObject())
                    val data = JSONObject()
                        .put("version", 1)
                        .put("exportedAt", Iso.now())
                        .put("notes", JSONArray(store.getNotes().map { it.toJson() }))
                        .put("notebooks", JSONArray(store.getNotebooks().map { it.toJson() }))
                        .put("profile", profile)
                    app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(data.toString(2).toByteArray()) }
                }.isSuccess
            }
            if (ok) showToast("Export saved", R.drawable.ic_fa_arrow_up_from_bracket)
            else showToast("Export failed", R.drawable.ic_fa_circle_exclamation)
        }
    }

    /** Merge import: only notes / notebooks whose id is new are added (duplicates skipped). */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = app.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                    val data = JSONObject(text)

                    var addedNotes = 0
                    var totalNotes = 0
                    data.optJSONArray("notes")?.let { arr ->
                        val incoming = (0 until arr.length()).mapNotNull { Note.fromJson(arr.getJSONObject(it)) }
                        totalNotes = arr.length()
                        val existing = store.getNotes()
                        val ids = existing.map { it.id }.toSet()
                        val fresh = incoming.filter { it.id !in ids }
                        addedNotes = fresh.size
                        store.saveNotes(existing + fresh)
                    }
                    data.optJSONArray("notebooks")?.let { arr ->
                        val incoming = (0 until arr.length()).mapNotNull { Notebook.fromJson(arr.getJSONObject(it)) }
                        val existing = store.getNotebooks()
                        val ids = existing.map { it.id }.toSet()
                        store.saveNotebooks(existing + incoming.filter { it.id !in ids })
                    }
                    addedNotes to (totalNotes - addedNotes)
                }.getOrNull()
            }
            refresh()
            if (result == null) {
                showToast("Import failed — invalid file", R.drawable.ic_fa_circle_exclamation)
            } else {
                val (added, skipped) = result
                val msg = "Imported $added note${if (added != 1) "s" else ""}" +
                    if (skipped > 0) " ($skipped duplicate${if (skipped != 1) "s" else ""} skipped)" else ""
                showToast(msg, R.drawable.ic_fa_file_import)
            }
        }
    }

    // ─── Confirm dialogs ─────────────────────────

    fun ask(kind: SettingsConfirm) {
        _state.update { it.copy(confirm = kind) }
    }

    fun dismissConfirm() {
        _state.update { it.copy(confirm = null) }
    }

    /** OK pressed. [onResetDone] runs after "Reset all data" (the screen signs out + resets the theme). */
    fun confirm(onResetDone: () -> Unit) {
        val kind = _state.value.confirm ?: return
        _state.update { it.copy(confirm = null) }
        when (kind) {
            SettingsConfirm.RestoreDrive -> doRestore()
            SettingsConfirm.ClearNotes -> {
                store.saveNotes(emptyList())
                refresh()
                showToast("All notes deleted", R.drawable.ic_fa_trash_can)
            }
            SettingsConfirm.ResetAll -> {
                // Web: remove every `iw_*` key (that includes the sign-in), then back to the start.
                val prefs = AppPrefs.of(app)
                val editor = prefs.edit()
                prefs.all.keys.filter { it.startsWith("iw_") }.forEach { editor.remove(it) }
                editor.apply()
                onResetDone()
            }
        }
    }

    // ─── toast ───────────────────────────────────

    fun dismissToast() {
        _state.update { it.copy(toast = null) }
    }

    private fun showToast(message: String, icon: Int) {
        _state.update { it.copy(toast = ToastState(System.nanoTime(), message, icon)) }
    }

    /** `fmtDate()` — "Nov 25, 2026, 12:26 PM" in the device time zone. */
    private fun fmtDate(iso: String): String {
        val ms = Iso.millis(iso)
        return if (ms == 0L) "—" else SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.ENGLISH).format(Date(ms))
    }

    private fun calcStorage(): StorageInfo {
        var total = 0L
        AppPrefs.of(app).all.forEach { (k, v) ->
            if (k.startsWith("iw_") && v is String) total += v.length * 2L
        }
        val kb = total / 1024.0
        val mb = kb / 1024.0
        val percent = ((mb / STORAGE_LIMIT_MB) * 100).coerceIn(2.0, 100.0).toFloat()
        return StorageInfo(String.format(Locale.US, "%.1f", kb), String.format(Locale.US, "%.2f", mb), percent)
    }

    private companion object {
        const val STORAGE_LIMIT_MB = 5.0
    }
}
