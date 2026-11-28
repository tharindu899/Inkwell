package com.inkwell.notes.util

import androidx.compose.ui.graphics.Color

/** Parses "#c9a96e" style colors saved by the web app. Falls back to the first notebook color. */
fun parseHex(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: IllegalArgumentException) {
    Color(0xFFC9A96E)
}
