package com.inkwell.notes.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Same 8 colors and 18 icon keys as the web notebook form, so synced data matches. */
val NotebookColors = listOf("#c9a96e", "#6090e0", "#60ad82", "#e06060", "#9b7de8", "#e07860", "#60c8d8", "#d4b0ff")

val NotebookIconKeys = listOf(
    "user", "briefcase", "lightbulb", "heart", "star", "book",
    "music", "camera", "globe", "code", "plane", "coffee",
    "rocket", "leaf", "fire", "flask", "palette", "graduation-cap",
)

fun notebookIcon(key: String): ImageVector = when (key) {
    "user" -> Icons.Rounded.Person
    "briefcase" -> Icons.Rounded.Work
    "lightbulb" -> Icons.Rounded.Lightbulb
    "heart" -> Icons.Rounded.Favorite
    "star" -> Icons.Rounded.Star
    "music" -> Icons.Rounded.MusicNote
    "camera" -> Icons.Rounded.PhotoCamera
    "globe" -> Icons.Rounded.Public
    "code" -> Icons.Rounded.Code
    "plane" -> Icons.Rounded.Flight
    "coffee" -> Icons.Rounded.Coffee
    "rocket" -> Icons.Rounded.RocketLaunch
    "leaf" -> Icons.Rounded.Eco
    "fire" -> Icons.Rounded.LocalFireDepartment
    "flask" -> Icons.Rounded.Science
    "palette" -> Icons.Rounded.Palette
    "graduation-cap" -> Icons.Rounded.School
    else -> Icons.Rounded.MenuBook
}
