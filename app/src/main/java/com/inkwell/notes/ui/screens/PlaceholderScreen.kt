package com.inkwell.notes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.inkwell.notes.ui.components.EmptyState
import com.inkwell.notes.ui.theme.Ink

/** Temporary screen for pages that come in the next parts. */
@Composable
fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize().background(Ink.colors.bg), contentAlignment = Alignment.Center) {
        EmptyState(title, "This screen is built in the next part.")
    }
}
