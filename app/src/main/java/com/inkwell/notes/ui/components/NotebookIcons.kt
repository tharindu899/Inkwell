package com.inkwell.notes.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.inkwell.notes.R

/** `COLORS` in Notebooks.jsx */
val NOTEBOOK_COLOR_CHOICES: List<String> = listOf(
    "#c9a96e", "#6090e0", "#60ad82", "#e06060", "#9b7de8", "#e07860", "#60c8d8", "#d4b0ff",
)

/** `ICONS` in Notebooks.jsx — notebooks store the Font Awesome class name, e.g. "fa-user". */
val NOTEBOOK_ICON_CHOICES: List<String> = listOf(
    "fa-user", "fa-briefcase", "fa-lightbulb", "fa-heart", "fa-star", "fa-book",
    "fa-music", "fa-camera", "fa-globe", "fa-code", "fa-plane", "fa-coffee",
    "fa-rocket", "fa-leaf", "fa-fire", "fa-flask", "fa-palette", "fa-graduation-cap",
)

/** Font Awesome class name (`nb.icon`) → vector drawable. Unknown names fall back to the book. */
@DrawableRes
fun notebookIconRes(name: String): Int = when (name) {
    "fa-user" -> R.drawable.ic_fa_user
    "fa-briefcase" -> R.drawable.ic_fa_briefcase
    "fa-lightbulb" -> R.drawable.ic_fa_lightbulb
    "fa-heart" -> R.drawable.ic_fa_heart
    "fa-star" -> R.drawable.ic_fa_star
    "fa-book" -> R.drawable.ic_fa_book
    "fa-music" -> R.drawable.ic_fa_music
    "fa-camera" -> R.drawable.ic_fa_camera
    "fa-globe" -> R.drawable.ic_fa_globe
    "fa-code" -> R.drawable.ic_fa_code
    "fa-plane" -> R.drawable.ic_fa_plane
    "fa-coffee" -> R.drawable.ic_fa_coffee
    "fa-rocket" -> R.drawable.ic_fa_rocket
    "fa-leaf" -> R.drawable.ic_fa_leaf
    "fa-fire" -> R.drawable.ic_fa_fire
    "fa-flask" -> R.drawable.ic_fa_flask
    "fa-palette" -> R.drawable.ic_fa_palette
    "fa-graduation-cap" -> R.drawable.ic_fa_graduation_cap
    else -> R.drawable.ic_fa_book_open
}

/** Notebook colour string → Compose colour. */
fun notebookColor(hex: String, fallback: Color): Color = parseColor(hex, fallback)
