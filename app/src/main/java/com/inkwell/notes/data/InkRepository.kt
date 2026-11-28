package com.inkwell.notes.data

import android.content.Context
import com.inkwell.notes.model.FontSize
import com.inkwell.notes.model.Note
import com.inkwell.notes.model.Notebook
import com.inkwell.notes.model.NotebookSort
import com.inkwell.notes.model.SortOrder
import com.inkwell.notes.model.ThemeMode
import com.inkwell.notes.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AppSettings(
    val theme: ThemeMode = ThemeMode.Dark,
    val fontSize: FontSize = FontSize.Medium,
    val sort: SortOrder = SortOrder.Modified,
    val notebookSort: NotebookSort = NotebookSort.Name,
    val spellcheck: Boolean = true,
    val autosave: Boolean = true,
    val markdownMode: Boolean = true,
    val serifBody: Boolean = false,
    val searches: List<String> = emptyList(),
    val user: UserProfile? = null,
)

/**
 * Local storage. Notes and notebooks are JSON files in the app's private folder (same names/shape as the web
 * localStorage keys). Small settings live in SharedPreferences.
 */
class InkRepository(context: Context) {
    private val dir: File = context.filesDir
    private val notesFile = File(dir, "iw_notes.json")
    private val notebooksFile = File(dir, "iw_notebooks.json")
    private val prefs = context.getSharedPreferences("inkwell", Context.MODE_PRIVATE)

    fun hasNotesFile(): Boolean = notesFile.exists()

    fun loadNotes(): List<Note> = try {
        if (notesFile.exists()) InkJson.notesFromArray(JSONArray(notesFile.readText())) else emptyList()
    } catch (e: Exception) { emptyList() }

    fun loadNotebooks(): List<Notebook> = try {
        if (notebooksFile.exists()) InkJson.notebooksFromArray(JSONArray(notebooksFile.readText())) else emptyList()
    } catch (e: Exception) { emptyList() }

    fun saveNotes(notes: List<Note>, notebooks: List<Notebook>) =
        writeAtomic(notesFile, InkJson.notesToArray(notes, notebooks).toString())

    fun saveNotebooks(notebooks: List<Notebook>) =
        writeAtomic(notebooksFile, InkJson.notebooksToArray(notebooks).toString())

    fun loadSettings(): AppSettings {
        val user = prefs.getString("iw_profile", null)?.let {
            try {
                val o = JSONObject(it)
                UserProfile(o.optString("name"), o.optString("email"), o.optString("picture").ifBlank { null })
            } catch (e: Exception) { null }
        }?.takeIf { it.email.isNotBlank() }
        val searches = prefs.getString("iw_searches", null)?.let {
            try { JSONArray(it).let { a -> List(a.length()) { i -> a.optString(i) } } } catch (e: Exception) { emptyList() }
        } ?: emptyList()
        return AppSettings(
            theme = ThemeMode.entries.firstOrNull { it.name.equals(prefs.getString("iw_theme", ""), true) } ?: ThemeMode.Dark,
            fontSize = FontSize.entries.firstOrNull { it.name.equals(prefs.getString("iw_fontSize", ""), true) } ?: FontSize.Medium,
            sort = SortOrder.entries.firstOrNull { it.name.equals(prefs.getString("iw_sort", ""), true) } ?: SortOrder.Modified,
            notebookSort = NotebookSort.entries.firstOrNull { it.name.equals(prefs.getString("iw_nbSort", ""), true) } ?: NotebookSort.Name,
            spellcheck = prefs.getBoolean("spellcheck", true),
            autosave = prefs.getBoolean("autosave", true),
            markdownMode = prefs.getBoolean("markdownMode", true),
            serifBody = prefs.getBoolean("serifBody", false),
            searches = searches,
            user = user,
        )
    }

    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putString("iw_theme", s.theme.name.lowercase())
            .putString("iw_fontSize", s.fontSize.name.lowercase())
            .putString("iw_sort", s.sort.name.lowercase())
            .putString("iw_nbSort", s.notebookSort.name.lowercase())
            .putBoolean("spellcheck", s.spellcheck)
            .putBoolean("autosave", s.autosave)
            .putBoolean("markdownMode", s.markdownMode)
            .putBoolean("serifBody", s.serifBody)
            .putString("iw_searches", JSONArray(s.searches).toString())
            .apply {
                if (s.user == null) remove("iw_profile")
                else putString(
                    "iw_profile",
                    JSONObject().put("name", s.user.name).put("email", s.user.email).put("picture", s.user.picture ?: "").toString(),
                )
            }
            .apply()
    }

    fun clearAll() {
        notesFile.delete()
        notebooksFile.delete()
        prefs.edit().clear().apply()
    }

    private fun writeAtomic(file: File, text: String) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }
}
