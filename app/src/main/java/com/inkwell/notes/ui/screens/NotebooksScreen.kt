package com.inkwell.notes.ui.screens

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.inkwell.notes.model.Notebook
import com.inkwell.notes.model.NotebookSort
import com.inkwell.notes.ui.components.ConfirmDialog
import com.inkwell.notes.ui.components.EmptyState
import com.inkwell.notes.ui.components.InkButton
import com.inkwell.notes.ui.components.InkChip
import com.inkwell.notes.ui.components.InkFabButton
import com.inkwell.notes.ui.components.InkIconButton
import com.inkwell.notes.ui.components.InkSheet
import com.inkwell.notes.ui.components.InkTopBar
import com.inkwell.notes.ui.components.NotebookFormDialog
import com.inkwell.notes.ui.components.SheetItem
import com.inkwell.notes.ui.components.notebookIcon
import com.inkwell.notes.ui.components.pressScale
import com.inkwell.notes.ui.components.staggeredEntrance
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.util.parseHex

@Composable
fun NotebooksScreen(
    notebooks: List<Notebook>,
    totalCount: Int,
    noteCount: (String) -> Int,
    sort: NotebookSort,
    onSort: (NotebookSort) -> Unit,
    onOpen: (String) -> Unit,
    onSave: (id: String?, name: String, color: String, icon: String) -> Unit,
    onDelete: (String) -> Unit,
    openFormRequest: Boolean,
    onFormRequestHandled: () -> Unit,
) {
    val c = Ink.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var formOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Notebook?>(null) }
    var menuFor by remember { mutableStateOf<Notebook?>(null) }
    var deleting by remember { mutableStateOf<Notebook?>(null) }

    // Home's FAB "New notebook" lands here and asks us to open the form.
    LaunchedEffect(openFormRequest) {
        if (openFormRequest) {
            editing = null
            formOpen = true
            onFormRequestHandled()
        }
    }

    fun newNotebook() {
        editing = null
        formOpen = true
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top + 8.dp, bottom = bottom + 170.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "top", span = { GridItemSpan(maxLineSpan) }) {
                InkTopBar("Notebooks", Modifier.staggeredEntrance(0)) {
                    InkIconButton(Icons.Rounded.Add, "New notebook", ::newNotebook)
                }
            }
            item(key = "total", span = { GridItemSpan(maxLineSpan) }) {
                TotalBar(totalCount, Modifier.staggeredEntrance(1))
            }
            item(key = "sort", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().staggeredEntrance(2), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotebookSort.entries.forEach { s -> InkChip(s.label, selected = s == sort, onClick = { onSort(s) }) }
                }
            }
            if (notebooks.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState("No notebooks yet", "Create one to keep related notes together.")
                        InkButton("New notebook", ::newNotebook)
                    }
                }
            }
            itemsIndexed(notebooks, key = { _, nb -> nb.id }) { i, nb ->
                NotebookCard(
                    nb = nb,
                    count = noteCount(nb.id),
                    onClick = { onOpen(nb.id) },
                    onMenu = { menuFor = nb },
                    modifier = Modifier.animateItem().staggeredEntrance(i + 2),
                )
            }
        }

        InkFabButton(
            Icons.Rounded.Add, "New notebook", ::newNotebook,
            Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = bottom + 106.dp),
        )
    }

    if (formOpen) {
        NotebookFormDialog(
            initial = editing,
            onSave = { name, color, icon ->
                onSave(editing?.id, name, color, icon)
                formOpen = false
            },
            onDismiss = { formOpen = false },
        )
    }

    menuFor?.let { nb ->
        InkSheet(nb.name, onDismiss = { menuFor = null }) { closeThen ->
            SheetItem(Icons.Rounded.Edit, "Edit notebook", { closeThen { editing = nb; formOpen = true } })
            SheetItem(Icons.Rounded.FolderOpen, "View notes", { closeThen { onOpen(nb.id) } })
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = c.border)
            SheetItem(Icons.Rounded.Delete, "Delete notebook", { closeThen { deleting = nb } }, danger = true)
        }
    }

    deleting?.let { nb ->
        ConfirmDialog(
            title = "Delete notebook?",
            text = "Notes in this notebook will not be deleted — they'll just become unorganised.",
            confirmLabel = "Delete",
            onConfirm = { onDelete(nb.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun TotalBar(count: Int, modifier: Modifier = Modifier) {
    val c = Ink.colors
    val shown by animateIntAsState(count, tween(700), label = "nbTotal")
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Total notebooks", color = c.subText, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(shown.toString(), color = c.text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun NotebookCard(nb: Notebook, count: Int, onClick: () -> Unit, onMenu: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    val tint = parseHex(nb.color)
    val bg = lerp(c.surface, tint, 0.2f)
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .fillMaxWidth()
            .pressScale(onClick)
            .clip(shape)
            .background(bg)
            .border(1.dp, tint.copy(alpha = 0.35f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(tint), contentAlignment = Alignment.Center) {
                Icon(notebookIcon(nb.icon), null, tint = Color(0xFF1F1B16), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(36.dp).pressScale(onMenu), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MoreVert, "Notebook options", tint = c.subText)
            }
        }
        Column {
            Text(nb.name, color = c.text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                "$count note${if (count != 1) "s" else ""}",
                color = c.subText,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
