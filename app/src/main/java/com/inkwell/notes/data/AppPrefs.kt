package com.inkwell.notes.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Native replacement for the web app's localStorage.
 * Key names are kept identical to the React app (`iw_theme`, `iw_gauth`)
 * so later screens/ports can follow the same storage layout.
 */
object AppPrefs {
    private const val FILE = "inkwell_prefs"

    const val KEY_THEME = "iw_theme"
    const val KEY_AUTH = "iw_gauth"
    const val KEY_NOTES = "iw_notes"
    const val KEY_NOTEBOOKS = "iw_notebooks"
    const val KEY_SORT = "iw_sort"
    const val KEY_SEED_VERSION = "iw_seed_version"
    const val KEY_SEARCHES = "iw_searches"
    const val KEY_PROFILE = "iw_profile"
    /** Settings → Editor toggles, one JSON object like the web `iw_prefs`. */
    const val KEY_PREFS = "iw_prefs"
    const val KEY_FONT_SIZE = "iw_fontSize"

    fun of(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getTheme(context: Context): String? = of(context).getString(KEY_THEME, null)

    fun setTheme(context: Context, key: String) {
        of(context).edit().putString(KEY_THEME, key).apply()
    }

    // ─── Editor font size (web `useFontSize`) ───
    fun getFontSize(context: Context): EditorFontSize =
        EditorFontSize.fromKey(of(context).getString(KEY_FONT_SIZE, null))

    fun setFontSize(context: Context, size: EditorFontSize) {
        of(context).edit().putString(KEY_FONT_SIZE, size.key).apply()
    }

    // ─── Settings toggles (web `getPrefs` / `savePref`, key `iw_prefs`) ───
    private fun readPrefsJson(context: Context): JSONObject = try {
        JSONObject(of(context).getString(KEY_PREFS, null) ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    /** Missing key → [default] (same as `prefs[id] !== undefined ? !!prefs[id] : def`). */
    fun getPref(context: Context, id: String, default: Boolean): Boolean =
        readPrefsJson(context).let { if (it.has(id)) it.optBoolean(id, default) else default }

    fun setPref(context: Context, id: String, value: Boolean) {
        val json = readPrefsJson(context).put(id, value)
        of(context).edit().putString(KEY_PREFS, json.toString()).apply()
    }
}

/** `FONT_SIZES` in useTheme.js — editor body text size. */
enum class EditorFontSize(val key: String, val label: String, val sp: Int) {
    Small("small", "Small", 13),
    Medium("medium", "Medium", 15),
    Large("large", "Large", 17);

    companion object {
        fun fromKey(key: String?): EditorFontSize = entries.firstOrNull { it.key == key } ?: Medium
    }
}
