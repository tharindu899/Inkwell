package com.inkwell.notes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.inkwell.notes.model.Note
import com.inkwell.notes.model.Notebook
import com.inkwell.notes.model.SortOrder
import com.inkwell.notes.ui.components.ConfirmDialog
import com.inkwell.notes.ui.components.EmptyState
import com.inkwell.notes.ui.components.InkChip
import com.inkwell.notes.ui.components.InkFabButton
import com.inkwell.notes.ui.components.InkIconButton
import com.inkwell.notes.ui.components.InkSheet
import com.inkwell.notes.ui.components.InkTopBar
import com.inkwell.notes.ui.components.NoteListItem
import com.inkwell.notes.ui.components.SheetItem
import com.inkwell.notes.ui.components.notebookIcon
import com.inkwell.notes.ui.components.staggeredEntrance
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.util.parseHex

@Composable
fun NotebookDetailScreen(
    notebook: Notebook,
    notes: List<Note>,
    sort: SortOrder,
    onSort: (SortOrder) -> Unit,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onNewNote: () -> Unit,
    onAllNotebooks: () -> Unit,
    onSearch: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Ink.colors
    val tint = parseHex(notebook.color)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var sheetOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            InkTopBar(notebook.name, Modifier.padding(horizontal = 18.dp), onBack = onBack) {
                InkIconButton(Icons.Rounded.MoreVert, "Options", { sheetOpen = true })
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = bottom + 170.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "hero") { Hero(notebook, notes.size, Modifier.staggeredEntrance(0)) }
                item(key = "sort") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).staggeredEntrance(1),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Sort by", color = c.subText, style = MaterialTheme.typography.labelLarge)
                        SortOrder.entries.forEach { s -> InkChip(s.label, selected = s == sort, onClick = { onSort(s) }) }
                    }
                }
                if (notes.isEmpty()) {
                    item(key = "empty") {
                        EmptyState("No notes yet", "Tap + to write your first note in this notebook.", Modifier.fillMaxWidth())
                    }
                }
                itemsIndexed(notes, key = { _, n -> n.id }) { i, n ->
                    NoteListItem(n, { onOpenNote(n.id) }, Modifier.animateItem().staggeredEntrance(i + 2))
                }
            }
        }

        InkFabButton(
            Icons.Rounded.Add, "New note in notebook", onNewNote,
            Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = bottom + 106.dp),
        )
    }

    if (sheetOpen) {
        InkSheet(notebook.name, onDismiss = { sheetOpen = false }) { closeThen ->
            SheetItem(Icons.Rounded.GridView, "All notebooks", { closeThen { onAllNotebooks() } })
            SheetItem(Icons.Rounded.Search, "Search in all notes", { closeThen { onSearch() } })
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = c.border)
            SheetItem(Icons.Rounded.Delete, "Delete notebook", { closeThen { confirmDelete = true } }, danger = true)
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete notebook?",
            text = "Notes in this notebook will stay but won't be in any notebook.",
            confirmLabel = "Delete",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Hero(nb: Notebook, count: Int, modifier: Modifier = Modifier) {
    val c = Ink.colors
    val tint = parseHex(nb.color)
    val iconShape = RoundedCornerShape(22.dp)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier.size(64.dp).clip(iconShape).background(tint.copy(alpha = 0.2f)).border(1.dp, tint.copy(alpha = 0.45f), iconShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(notebookIcon(nb.icon), null, tint = tint, modifier = Modifier.size(30.dp))
        }
        Column {
            Text(nb.name, color = c.text, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            Text("$count note${if (count != 1) "s" else ""}", color = c.subText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
