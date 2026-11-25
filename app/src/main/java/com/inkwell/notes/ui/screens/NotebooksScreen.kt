package com.inkwell.notes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inkwell.notes.R
import com.inkwell.notes.data.model.Notebook
import com.inkwell.notes.ui.components.BottomNav
import com.inkwell.notes.ui.components.ContextSheet
import com.inkwell.notes.ui.components.EmptyState
import com.inkwell.notes.ui.components.Fab
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.IconBtn
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.components.InkToast
import com.inkwell.notes.ui.components.InnerTopBar
import com.inkwell.notes.ui.components.NavTab
import com.inkwell.notes.ui.components.SheetDivider
import com.inkwell.notes.ui.components.SheetItem
import com.inkwell.notes.ui.components.notebookColor
import com.inkwell.notes.ui.components.notebookIconRes
import com.inkwell.notes.ui.components.onAccent
import com.inkwell.notes.ui.components.tap
import com.inkwell.notes.ui.theme.Ink

/**
 * Port of `src/pages/Notebooks.jsx`: total bar, sort chips, 2-column notebook grid,
 * create / edit modal, ⋮ context sheet and delete confirm. Tap a card → NotebookDetail.
 */
@Composable
fun NotebooksScreen(
    onBack: () -> Unit,
    onNavTab: (NavTab) -> Unit,
    onOpenNotebook: (String) -> Unit,
    vm: NotebooksViewModel = viewModel(),
) {
    val c = Ink.colors
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.reload() }

    // Back closes the topmost layer first.
    BackHandler(enabled = state.formOpen || state.ctxId != null || state.deleteId != null) {
        when {
            state.formOpen -> vm.closeForm()
            state.deleteId != null -> vm.dismissDelete()
            else -> vm.closeCtx()
        }
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            InnerTopBar(
                title = "Notebooks",
                onBack = onBack,
                actions = { IconBtn(R.drawable.ic_fa_plus) { vm.openForm() } },
            )

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 62.dp + navInset + 14.dp),
            ) {
                item(key = "total") { TotalBar(state.notebooks.size) }
                item(key = "sort") {
                    SortChips(NbSort.entries, state.sort, { it.label }, vm::setNbSort, Modifier.padding(bottom = 8.dp))
                }

                if (state.notebooks.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = R.drawable.ic_fa_book_open,
                            title = "No notebooks yet",
                            sub = "Create a notebook to organise your notes by topic, project, or mood.",
                            action = { PrimaryButton("Create Notebook") { vm.openForm() } },
                        )
                    }
                } else {
                    items(state.notebooks.chunked(2), key = { it.first().id }) { pair ->
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            pair.forEach { nb ->
                                NotebookCard(
                                    nb,
                                    count = state.counts[nb.id] ?: 0,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onOpenNotebook(nb.id) },
                                    onMenu = { vm.openCtx(nb.id) },
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        BottomNav(
            active = NavTab.Notebooks,
            onSelect = onNavTab,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // FAB — plain "+" that opens the New Notebook modal
        PlainFab(onClick = { vm.openForm() })

        InkToast(
            toast = state.toast,
            onAction = {},
            onDismiss = vm::dismissToast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 17.dp, end = 17.dp, bottom = 62.dp + navInset + 12.dp),
        )

        // ── Context sheet ──
        state.ctxId?.let { id ->
            val nb = state.notebooks.firstOrNull { it.id == id }
            ContextSheet(title = nb?.name?.ifEmpty { null } ?: "Notebook", onDismiss = vm::closeCtx) {
                SheetItem(R.drawable.ic_fa_r_pen_to_square, "Edit notebook") {
                    vm.closeCtx()
                    vm.openForm(id)
                }
                SheetItem(R.drawable.ic_fa_r_folder_open, "View notes") {
                    vm.closeCtx()
                    onOpenNotebook(id)
                }
                SheetDivider()
                SheetItem(R.drawable.ic_fa_r_trash_can, "Delete notebook", danger = true) { vm.requestDelete(id) }
            }
        }

        if (state.formOpen) {
            NotebookFormModal(
                editing = state.editing,
                onDismiss = vm::closeForm,
                onSave = vm::saveForm,
            )
        }

        if (state.deleteId != null) {
            DeleteNotebookModal(
                sub = "Notes in this notebook will not be deleted — they'll just become unorganised.",
                onCancel = vm::dismissDelete,
                onConfirm = vm::confirmDelete,
            )
        }
    }
}

/** `.nb-total-bar` */
@Composable
private fun TotalBar(total: Int) {
    val c = Ink.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .heightIn(min = 58.dp)
            .clip(shape)
            .background(c.bgCard)
            .border(1.dp, c.border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkText("TOTAL NOTEBOOKS", size = 12.sp, weight = FontWeight.ExtraBold, color = c.text3, letterSpacing = 0.6.sp)
        InkText(total.toString(), size = 22.sp, weight = FontWeight.ExtraBold, color = c.accent)
    }
}

/** `.nb-card` — gradient tile in the notebook's colour, white text, ⋮ button top-right. */
@Composable
private fun NotebookCard(nb: Notebook, count: Int, modifier: Modifier, onClick: () -> Unit, onMenu: () -> Unit) {
    val c = Ink.colors
    val base = notebookColor(nb.color, c.accent)
    val shape = RoundedCornerShape(18.dp)
    val gradient = Brush.linearGradient(
        listOf(lerp(base, Color.White, 0.08f), lerp(base, Color(0xFF101010), 0.32f)),
    )
    Box(
        modifier
            .heightIn(min = 118.dp)
            .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f))
            .clip(shape)
            .background(gradient)
            .border(1.dp, base.copy(alpha = 0.70f), shape)
            .tap(onClick)
            .padding(13.dp),
    ) {
        Column {
            // .nb-icon — 42dp glass tile
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.20f))
                    .border(1.dp, Color.White.copy(alpha = 0.38f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                FaIcon(notebookIconRes(nb.icon), tint = Color.White, size = 18.dp)
            }
            Spacer(Modifier.height(9.dp))
            InkText(nb.name, size = 15.sp, weight = FontWeight.Black, color = Color.White)
            Spacer(Modifier.height(2.dp))
            InkText(
                "$count note${if (count != 1) "s" else ""}",
                size = 12.sp,
                weight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = 0.82f),
            )
        }
        // .nb-menu — 30dp, top/right 8dp (relative to the card, not its padding)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 5.dp, y = (-5).dp) // card padding is 13dp, the button sits 8dp from the edge
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .tap(onMenu),
            contentAlignment = Alignment.Center,
        ) {
            FaIcon(R.drawable.ic_fa_ellipsis_vertical, tint = Color.White, size = 14.dp)
        }
    }
}

/** `.sort-row` for pages other than Home (no Select chip). */
@Composable
internal fun <T> SortChips(
    options: List<T>,
    current: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ink.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkText("SORT BY", size = 10.sp, weight = FontWeight.ExtraBold, color = c.text3, letterSpacing = 0.45.sp)
        options.forEach { o ->
            val on = o == current
            Box(
                Modifier
                    .height(28.dp)
                    .clip(shape)
                    .background(if (on) c.accent else c.bgCard)
                    .border(1.5.dp, if (on) c.accent else c.border, shape)
                    .tap { onSelect(o) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                InkText(label(o), size = 10.sp, weight = FontWeight.Bold, color = if (on) onAccent() else c.text2)
            }
        }
    }
}

/** `.btn.btn-primary` */
@Composable
internal fun PrimaryButton(label: String, onClick: () -> Unit) {
    val c = Ink.colors
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(c.accent)
            .tap(onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        InkText(label, size = 14.sp, weight = FontWeight.ExtraBold, color = onAccent())
    }
}

/** The bare web `<button className="fab">` used by Notebooks / NotebookDetail. */
@Composable
internal fun BoxScope.PlainFab(onClick: () -> Unit) {
    Fab(
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(end = 18.dp, bottom = 62.dp + 12.dp),
    )
}
