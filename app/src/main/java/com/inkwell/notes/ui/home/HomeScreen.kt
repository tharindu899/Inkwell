package com.inkwell.notes.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkwell.notes.data.SortOrder
import com.inkwell.notes.ui.components.InkIcon
import com.inkwell.notes.ui.components.InkIconView
import com.inkwell.notes.ui.theme.ErrorTint
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkType
import com.inkwell.notes.ui.theme.Lime
import com.inkwell.notes.ui.theme.Paper
import com.inkwell.notes.util.SystemBars
import com.inkwell.notes.util.fmtNum
import com.inkwell.notes.util.greetingWord

@Composable
fun HomeScreen(
    userName: String,
    vm: HomeViewModel,
    onOpenNote: (String) -> Unit,
    onSearch: () -> Unit,
) {
    SystemBars(lightNavIcons = false)
    val ui by vm.ui.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Paper),
        contentPadding = PaddingValues(bottom = 150.dp),
    ) {
        item(key = "header") { HomeHeader(userName, ui.stats, onSearch) }
        item(key = "sort") {
            SortRow(
                current = ui.sort,
                selectMode = ui.selectMode,
                onSort = vm::setSort,
                onSelect = vm::startSelecting,
            )
        }

        if (ui.stats.notes == 0) {
            item(key = "empty") { EmptyState() }
        }

        if (ui.pinned.isNotEmpty()) {
            item(key = "pinnedTitle") { SectionTitle("Pinned", ui.pinned.size) }
            itemsIndexed(ui.pinned, key = { _, n -> n.id }) { i, n ->
                NoteCard(
                    note = n,
                    notebook = n.notebookId?.let { ui.notebooks[it] },
                    index = i,
                    wide = true,
                    selectMode = ui.selectMode,
                    selected = n.id in ui.selected,
                    onClick = { if (ui.selectMode) vm.toggleSelect(n.id) else onOpenNote(n.id) },
                    onLongClick = { if (ui.selectMode) vm.toggleSelect(n.id) else vm.enterSelectMode(n.id) },
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 12.dp)
                        .fillMaxWidth(),
                )
            }
        }

        if (ui.recent.isNotEmpty()) {
            item(key = "recentTitle") { SectionTitle("Recent", ui.recent.size) }
            val rows = ui.recent.chunked(2)
            itemsIndexed(rows, key = { _, r -> r.first().id }) { rowIndex, row ->
                Row(
                    Modifier
                        .animateItem()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 12.dp)
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEachIndexed { col, n ->
                        NoteCard(
                            note = n,
                            notebook = n.notebookId?.let { ui.notebooks[it] },
                            index = ui.pinned.size + rowIndex * 2 + col,
                            wide = false,
                            selectMode = ui.selectMode,
                            selected = n.id in ui.selected,
                            onClick = { if (ui.selectMode) vm.toggleSelect(n.id) else onOpenNote(n.id) },
                            onLongClick = { if (ui.selectMode) vm.toggleSelect(n.id) else vm.enterSelectMode(n.id) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    if (ui.confirmDelete) {
        DeleteDialog(count = ui.selected.size, onCancel = vm::dismissDelete, onConfirm = vm::confirmDelete)
    }
}

// ───────────────────────── header ─────────────────────────

@Composable
private fun HomeHeader(userName: String, stats: HomeStats, onSearch: () -> Unit) {
    var target by remember { mutableIntStateOf(0) }
    LaunchedEffect(stats.notes) { target = stats.notes }
    val shown by animateIntAsState(target, tween(900, easing = FastOutSlowInEasing), label = "count")

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
            .background(Lime)
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 28.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("inkwell", style = InkType.Title, color = Ink)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(2.dp, Ink, CircleShape)
                    .clickable(role = Role.Button, onClick = onSearch)
                    .semantics { contentDescription = "Search notes" },
                contentAlignment = Alignment.Center,
            ) { InkIconView(InkIcon.Search, Ink, Modifier.size(20.dp), strokeWidth = 2.4f) }
        }
        Spacer(Modifier.height(18.dp))
        Text("Good ${greetingWord()},", style = InkType.Body, color = Ink)
        Text(userName, style = InkType.Headline, color = Ink, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$shown",
                style = InkType.Display.copy(fontSize = 104.sp, lineHeight = 90.sp, letterSpacing = (-5).sp),
                color = Ink,
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.padding(bottom = 6.dp)) {
                Text(if (stats.notes == 1) "note" else "notes", style = InkType.Title, color = Ink)
                Text(
                    "${stats.books} ${if (stats.books == 1) "book" else "books"} · ${fmtNum(stats.words)} words",
                    style = InkType.Label,
                    color = Ink,
                )
            }
        }
    }
}

// ───────────────────────── sort row ─────────────────────────

@Composable
private fun SortRow(current: SortOrder, selectMode: Boolean, onSort: (SortOrder) -> Unit, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip("Select", on = selectMode, icon = InkIcon.Check, onClick = onSelect)
        SortOrder.entries.forEach { s ->
            Chip(s.label, on = s == current, onClick = { onSort(s) })
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, icon: InkIcon? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .then(if (on) Modifier.background(Ink) else Modifier.border(2.dp, Ink, CircleShape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            InkIconView(icon, if (on) Lime else Ink, Modifier.size(14.dp), strokeWidth = 2.8f)
            Spacer(Modifier.size(6.dp))
        }
        Text(label, style = InkType.Label.copy(fontSize = 14.sp), color = if (on) Lime else Ink)
    }
}

@Composable
private fun SectionTitle(text: String, count: Int) {
    Row(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = InkType.Title, color = Ink)
        Spacer(Modifier.size(8.dp))
        Box(Modifier.clip(CircleShape).background(Ink).padding(horizontal = 9.dp, vertical = 2.dp)) {
            Text("$count", style = InkType.Label, color = Lime)
        }
    }
}

// ───────────────────────── empty + dialog ─────────────────────────

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
            InkIconView(InkIcon.Pen, Ink, Modifier.size(36.dp), strokeWidth = 2f)
        }
        Spacer(Modifier.height(18.dp))
        Text("No notes yet", style = InkType.Headline, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("Tap the + button to write your first note.", style = InkType.Body, color = Ink)
    }
}

@Composable
private fun DeleteDialog(count: Int, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(Paper)
                .padding(24.dp),
        ) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(ErrorTint), contentAlignment = Alignment.Center) {
                InkIconView(InkIcon.Trash, Ink, Modifier.size(24.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Delete selected notes?", style = InkType.Headline.copy(fontSize = 22.sp), color = Ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "$count note${if (count != 1) "s" else ""} will be deleted. You can undo for 5 seconds after deleting.",
                style = InkType.Body,
                color = Ink,
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogButton("Cancel", filled = false, modifier = Modifier.weight(1f), onClick = onCancel)
                DialogButton("Delete", filled = true, modifier = Modifier.weight(1f), onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(52.dp)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(Ink) else Modifier.border(2.dp, Ink, CircleShape))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = InkType.Button.copy(fontSize = 16.sp), color = if (filled) ErrorTint else Ink)
    }
}
