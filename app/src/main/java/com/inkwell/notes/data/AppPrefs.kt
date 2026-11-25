package com.inkwell.notes.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Native replacement for the web app's localStorage.
 * Key names are kept identical to the React app (`iw_theme`, `iw_gauth`)
 * so later screens/ports can follow the same storage layout.
 */
object AppPrefs {
    private const val FILE = "inkwell_prefs"

    const val KEY_THEME = "iw_theme"
    const val KEY_AUTH = "iw_gauth"

    fun of(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getTheme(context: Context): String? = of(context).getString(KEY_THEME, null)

    fun setTheme(context: Context, key: String) {
        of(context).edit().putString(KEY_THEME, key).apply()
    }
}
