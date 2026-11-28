package com.inkwell.notes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inkwell.notes.R
import com.inkwell.notes.data.model.Notebook
import com.inkwell.notes.data.model.SortOrder
import com.inkwell.notes.ui.components.BottomNav
import com.inkwell.notes.ui.components.ContextSheet
import com.inkwell.notes.ui.components.EmptyState
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.IconBtn
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.components.InnerTopBar
import com.inkwell.notes.ui.components.NavTab
import com.inkwell.notes.ui.components.NoteCard
import com.inkwell.notes.ui.components.SheetDivider
import com.inkwell.notes.ui.components.SheetItem
import com.inkwell.notes.ui.components.notebookColor
import com.inkwell.notes.ui.components.notebookIconRes
import com.inkwell.notes.ui.theme.Ink

/**
 * Port of `src/pages/NotebookDetail.jsx`: hero card, sort chips, the notebook's notes,
 * ⋮ sheet (All notebooks / Search / Delete) and delete confirm.
 * If the notebook no longer exists (deleted, bad id) it leaves via [onMissing].
 *
 * Note tap → Editor; the FAB opens a new note pre-assigned to this notebook (`/editor?new=1&notebook=$id`).
 */
@Composable
fun NotebookDetailScreen(
    notebookId: String,
    onBack: () -> Unit,
    onNavTab: (NavTab) -> Unit,
    onAllNotebooks: () -> Unit,
    onSearch: () -> Unit,
    onMissing: () -> Unit,
    onOpenNote: (String) -> Unit = {},
    onNewNote: () -> Unit = {},
    vm: NotebooksViewModel = viewModel(),
) {
    val c = Ink.colors
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.reload() }

    val nb = state.notebooks.firstOrNull { it.id == notebookId }
    // web: `if (!nb) navigate('/notebooks', { replace: true })`
    LaunchedEffect(nb == null) { if (nb == null) onMissing() }

    BackHandler(enabled = state.detailMenuOpen || state.detailDeleteOpen) {
        if (state.detailDeleteOpen) vm.dismissDetailDelete() else vm.closeDetailMenu()
    }

    if (nb == null) {
        Box(Modifier.fillMaxSize().background(c.bg))
        return
    }

    // state.notes is already sorted (pinned first, then the chosen sort order).
    val nbNotes = remember(state.notes, notebookId) { state.notes.filter { it.notebookId == notebookId } }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            InnerTopBar(
                title = nb.name,
                onBack = onBack,
                actions = { IconBtn(R.drawable.ic_fa_ellipsis_vertical, vm::openDetailMenu) },
            )

            NotebookHero(nb, nbNotes.size)

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = Ink.navHeight + navInset + 14.dp),
            ) {
                item(key = "sort") {
                    SortChips(
                        SortOrder.entries,
                        state.sortOrder,
                        { it.label },
                        vm::setSortOrder,
                        Modifier.padding(top = 6.dp, bottom = 8.dp),
                    )
                }
                if (nbNotes.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = R.drawable.ic_fa_note_sticky,
                            title = "No notes yet",
                            sub = "Tap + to write your first note in this notebook.",
                        )
                    }
                } else {
                    items(nbNotes, key = { it.id }) { n ->
                        NoteCard(n, nb, onClick = { onOpenNote(n.id) })
                    }
                }
            }
        }

        BottomNav(
            active = NavTab.Notebooks,
            onSelect = onNavTab,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // FAB — new note pre-assigned to this notebook
        PlainFab(onClick = onNewNote)

        if (state.detailMenuOpen) {
            ContextSheet(title = nb.name, onDismiss = vm::closeDetailMenu) {
                SheetItem(R.drawable.ic_fa_table_cells_large, "All notebooks") {
                    vm.closeDetailMenu()
                    onAllNotebooks()
                }
                SheetItem(R.drawable.ic_fa_magnifying_glass, "Search in all notes") {
                    vm.closeDetailMenu()
                    onSearch()
                }
                SheetDivider()
                SheetItem(R.drawable.ic_fa_r_trash_can, "Delete notebook", danger = true) { vm.requestDetailDelete() }
            }
        }

        if (state.detailDeleteOpen) {
            DeleteNotebookModal(
                sub = "Notes in this notebook will stay but won't be in any notebook.",
                onCancel = vm::dismissDetailDelete,
                onConfirm = { vm.confirmDetailDelete(notebookId) },
            )
        }
    }
}

/** `.nb-hero` (≤430px values): tinted card with the notebook icon, serif name and note count. */
@Composable
private fun NotebookHero(nb: Notebook, count: Int) {
    val c = Ink.colors
    val color = notebookColor(nb.color, c.accent)
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .padding(start = 14.dp, end = 14.dp, top = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(c.bgCard, color, 0.10f), c.bgCard)))
            .border(1.dp, lerp(c.border, color, 0.28f), shape),
    ) {
        // ::after — soft circle in the top-right corner
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 24.dp, y = (-24).dp)
                .size(108.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
        )
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            val iconShape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .size(52.dp)
                    .clip(iconShape)
                    .background(color.copy(alpha = 0.094f)) // `${color}18`
                    .border(1.dp, color.copy(alpha = 0.267f), iconShape), // `${color}44`
                contentAlignment = Alignment.Center,
            ) {
                FaIcon(notebookIconRes(nb.icon), tint = color, size = 22.dp)
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                InkText(nb.name, family = Ink.serif, size = 20.sp, weight = FontWeight.Bold, lineHeight = 21.sp)
                Spacer(Modifier.size(2.dp))
                InkText(
                    "$count note${if (count != 1) "s" else ""}",
                    size = 12.sp,
                    weight = FontWeight.Bold,
                    color = c.text2,
                )
            }
        }
    }
}
