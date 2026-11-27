package com.inkwell.notes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inkwell.notes.model.Note
import com.inkwell.notes.model.SortOrder
import com.inkwell.notes.ui.components.EmptyState
import com.inkwell.notes.ui.components.InkChip
import com.inkwell.notes.ui.components.InkFab
import com.inkwell.notes.ui.components.InkIconButton
import com.inkwell.notes.ui.components.PastelCard
import com.inkwell.notes.ui.components.SectionLabel
import com.inkwell.notes.ui.components.StatCard
import com.inkwell.notes.ui.components.pressScale
import com.inkwell.notes.ui.components.staggeredEntrance
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.Pastel
import com.inkwell.notes.util.formatRelative
import com.inkwell.notes.util.greetingText
import com.inkwell.notes.util.stripHtml
import kotlin.math.abs

@Composable
fun HomeScreen(
    userName: String,
    notes: List<Note>,
    notebookCount: Int,
    sort: SortOrder,
    onSort: (SortOrder) -> Unit,
    onOpenNote: (String) -> Unit,
    onProfile: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onNewNote: () -> Unit,
    onNewNotebook: () -> Unit,
    onNewTag: () -> Unit,
) {
    val c = Ink.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pinned = notes.filter { it.pinned }.sortedBy { it.pinOrder ?: Int.MAX_VALUE }
    val others = notes.filter { !it.pinned }
    val words = notes.sumOf { it.wordCount }
    val full = StaggeredGridItemSpan.FullLine

    Box(Modifier.fillMaxSize().background(c.bg)) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top + 8.dp, bottom = bottom + 170.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "top", span = full) { HomeTopBar(userName, onProfile, onSearch, onSettings) }
            item(key = "stats", span = full) {
                Row(Modifier.fillMaxWidth().staggeredEntrance(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(notes.size, "Notes", Modifier.weight(1f))
                    StatCard(notebookCount, "Books", Modifier.weight(1f))
                    StatCard(words, "Words", Modifier.weight(1f))
                }
            }
            item(key = "sort", span = full) {
                Row(Modifier.fillMaxWidth().staggeredEntrance(2), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortOrder.entries.forEach { s -> InkChip(s.label, selected = s == sort, onClick = { onSort(s) }) }
                }
            }
            if (notes.isEmpty()) {
                item(key = "empty", span = full) {
                    EmptyState("No notes yet", "Tap + to write your first note.", Modifier.fillMaxWidth())
                }
            }
            if (pinned.isNotEmpty()) {
                item(key = "pinnedLabel", span = full) { SectionLabel("Pinned", Modifier.padding(top = 6.dp)) }
                pinned.firstOrNull()?.let { first ->
                    item(key = "wide-${first.id}", span = full) {
                        WideNoteCard(first, pastelFor(first), { onOpenNote(first.id) }, Modifier.staggeredEntrance(3))
                    }
                }
                itemsIndexed(pinned.drop(1), key = { _, n -> n.id }) { i, n ->
                    NoteCard(n, pastelFor(n), 3 + (i % 2) * 2, { onOpenNote(n.id) }, Modifier.staggeredEntrance(4 + i))
                }
            }
            if (others.isNotEmpty()) {
                item(key = "recentLabel", span = full) { SectionLabel("Recent", Modifier.padding(top = 6.dp)) }
                itemsIndexed(others, key = { _, n -> n.id }) { i, n ->
                    NoteCard(n, pastelFor(n), 2 + (i % 3) * 2, { onOpenNote(n.id) }, Modifier.staggeredEntrance(5 + i))
                }
            }
        }
        InkFab(
            onNewNote = onNewNote,
            onNewNotebook = onNewNotebook,
            onNewTag = onNewTag,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = bottom + 106.dp),
        )
    }
}

/** Stable pastel per note, so colors don't jump around between recompositions. */
@Composable
private fun pastelFor(note: Note): Pastel {
    val list = Ink.colors.pastels
    return list[abs(note.id.hashCode()) % list.size]
}

@Composable
private fun HomeTopBar(name: String, onProfile: () -> Unit, onSearch: () -> Unit, onSettings: () -> Unit) {
    val c = Ink.colors
    Row(Modifier.fillMaxWidth().staggeredEntrance(0), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.pressScale(onProfile).size(44.dp).clip(CircleShape).background(c.inverse),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1).uppercase(), color = c.onInverse, style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("${greetingText()}, $name", color = c.subText, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("My Notes", color = c.text, style = MaterialTheme.typography.headlineMedium)
        }
        InkIconButton(Icons.Rounded.Search, "Search", onSearch)
        Box(Modifier.size(8.dp))
        InkIconButton(Icons.Rounded.Settings, "Settings", onSettings)
    }
}

@Composable
private fun WideNoteCard(note: Note, pastel: Pastel, onClick: () -> Unit, modifier: Modifier) {
    PastelCard(pastel, modifier.fillMaxWidth(), onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.PushPin, null, tint = pastel.body, modifier = Modifier.size(14.dp))
            Text("PINNED", color = pastel.body, style = MaterialTheme.typography.labelSmall)
        }
        Text(note.title.ifBlank { "Untitled" }, color = pastel.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(note.content.stripHtml(), color = pastel.body, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(formatRelative(note.updatedAt), color = pastel.body, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun NoteCard(note: Note, pastel: Pastel, lines: Int, onClick: () -> Unit, modifier: Modifier) {
    PastelCard(pastel, modifier.fillMaxWidth(), onClick) {
        Text(note.title.ifBlank { "Untitled" }, color = pastel.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(note.content.stripHtml(), color = pastel.body, style = MaterialTheme.typography.bodyMedium, maxLines = lines, overflow = TextOverflow.Ellipsis)
        Text(formatRelative(note.updatedAt), color = pastel.body, style = MaterialTheme.typography.labelMedium)
    }
}
