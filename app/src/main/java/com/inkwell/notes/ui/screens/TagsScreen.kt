package com.inkwell.notes.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.inkwell.notes.model.Note
import com.inkwell.notes.ui.components.EmptyState
import com.inkwell.notes.ui.components.InkFab
import com.inkwell.notes.ui.components.InkIconButton
import com.inkwell.notes.ui.components.InkTopBar
import com.inkwell.notes.ui.components.NoteListItem
import com.inkwell.notes.ui.components.SectionHeader
import com.inkwell.notes.ui.components.TagChip
import com.inkwell.notes.ui.components.pressScale
import com.inkwell.notes.ui.components.staggeredEntrance
import com.inkwell.notes.ui.theme.Ink

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsScreen(
    allNotes: List<Note>,
    tags: List<Pair<String, Int>>,
    onOpenNote: (String) -> Unit,
    onSearch: () -> Unit,
    onNewNote: () -> Unit,
    onNewNotebook: () -> Unit,
    onCreateTag: (String) -> Unit,
) {
    val c = Ink.colors
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var activeTag by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = activeTag?.let { t -> allNotes.filter { t in it.tags } } ?: allNotes

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            InkTopBar("Tags", Modifier.padding(horizontal = 18.dp).staggeredEntrance(0)) {
                InkIconButton(Icons.Rounded.Search, "Search", onSearch)
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = bottom + 170.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "tagsHeader") { SectionHeader("All tags", tags.size, Modifier.staggeredEntrance(1)) }
                item(key = "cloud") {
                    if (tags.isEmpty()) {
                        HelpCard("No tags yet. Add tags while editing a note.", Modifier.staggeredEntrance(2))
                    } else {
                        FlowRow(
                            Modifier.fillMaxWidth().staggeredEntrance(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            tags.forEach { (name, count) ->
                                TagChip(name, count, active = activeTag == name, onClick = {
                                    activeTag = if (activeTag == name) null else name
                                })
                            }
                        }
                    }
                }
                item(key = "activeLabel") {
                    AnimatedVisibility(
                        visible = activeTag != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        ActiveTagLabel(activeTag ?: "", onRemove = { activeTag = null })
                    }
                }
                item(key = "notesHeader") {
                    SectionHeader(if (activeTag != null) "Notes" else "All notes", shown.size)
                }
                if (shown.isEmpty()) {
                    item(key = "empty") {
                        if (activeTag != null) {
                            EmptyState("No notes with this tag", "Add the tag \"$activeTag\" to a note in the editor.", Modifier.fillMaxWidth())
                        } else {
                            EmptyState("No notes yet", "Tap + to write your first note.", Modifier.fillMaxWidth())
                        }
                    }
                }
                itemsIndexed(shown, key = { _, n -> n.id }) { i, n ->
                    NoteListItem(n, { onOpenNote(n.id) }, Modifier.animateItem().staggeredEntrance(i + 3))
                }
            }
        }

        InkFab(
            onNewNote = onNewNote,
            onNewNotebook = onNewNotebook,
            onCreateTag = onCreateTag,
            onSearch = onSearch,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = bottom + 106.dp),
        )
    }
}

@Composable
private fun HelpCard(text: String, modifier: Modifier = Modifier) {
    val c = Ink.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Info, null, tint = c.subText, modifier = Modifier.size(20.dp))
        Text(text, color = c.subText, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActiveTagLabel(tag: String, onRemove: () -> Unit) {
    val c = Ink.colors
    Row(
        Modifier.clip(CircleShape).background(c.accent).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.Sell, null, tint = c.onAccent, modifier = Modifier.size(14.dp))
        Text(tag, color = c.onAccent, style = MaterialTheme.typography.labelLarge)
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(c.onAccent.copy(alpha = 0.15f)).pressScale(onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, "Clear tag filter", tint = c.onAccent, modifier = Modifier.size(14.dp))
        }
    }
}
