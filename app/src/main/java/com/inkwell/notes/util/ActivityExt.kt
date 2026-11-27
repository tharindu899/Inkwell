package com.inkwell.notes.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Sets status/navigation bar icon colours for the current screen.
 * lightNavIcons = true  -> screen has a DARK bottom (white nav icons)
 * lightNavIcons = false -> screen has a LIGHT bottom (dark nav icons)
 */
@Composable
fun SystemBars(lightNavIcons: Boolean) {
    val activity = LocalContext.current.findActivity() as? ComponentActivity
    DisposableEffect(activity, lightNavIcons) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (lightNavIcons) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            },
        )
        onDispose { }
    }
}
