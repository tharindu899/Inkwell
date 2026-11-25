package com.inkwell.notes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inkwell.notes.R
import com.inkwell.notes.data.model.Notebook
import com.inkwell.notes.ui.components.BottomNav
import com.inkwell.notes.ui.components.ContextSheet
import com.inkwell.notes.ui.components.EmptyState
import com.inkwell.notes.ui.components.Fab
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.components.InkToast
import com.inkwell.notes.ui.components.NavTab
import com.inkwell.notes.ui.components.SheetDivider
import com.inkwell.notes.ui.components.SheetItem
import com.inkwell.notes.ui.components.notebookColor
import com.inkwell.notes.ui.components.notebookIconRes
import com.inkwell.notes.ui.components.onAccent
import com.inkwell.notes.ui.components.tap
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.util.Iso
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Notebooks page layouts: big cards (default), compact list, 2-column grid. */
private enum class NbView(val icon: Int) {
    Cards(R.drawable.ic_view_rows),
    Compact(R.drawable.ic_view_list),
    Grid(R.drawable.ic_view_grid),
}

/** Flat list the LazyColumn renders: date headers, single rows, or a pair of grid cards. */
private sealed interface NbEntry {
    data class Header(val label: String) : NbEntry
    data class One(val nb: Notebook) : NbEntry
    data class Duo(val a: Notebook, val b: Notebook?) : NbEntry
}

/**
 * Notebooks tab: big title + count, view switcher (left) and sort dropdown (right),
 * date-grouped notebook cards. Tap a card → NotebookDetail, long-press → ⋮ sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotebooksScreen(
    onBack: () -> Unit,
    onNavTab: (NavTab) -> Unit,
    onOpenNotebook: (String) -> Unit,
    onSearch: () -> Unit = {},
    vm: NotebooksViewModel = viewModel(),
) {
    val c = Ink.colors
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.reload() }

    var viewIdx by rememberSaveable { mutableIntStateOf(NbView.Cards.ordinal) }
    val view = NbView.entries[viewIdx]

    // Back closes the topmost layer first.
    BackHandler(enabled = state.formOpen || state.ctxId != null || state.deleteId != null) {
        when {
            state.formOpen -> vm.closeForm()
            state.deleteId != null -> vm.dismissDelete()
            else -> vm.closeCtx()
        }
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val entries = remember(state.notebooks, state.sort, view) { buildEntries(state.notebooks, state.sort, view) }
    val total = state.notebooks.size

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            // ── Title + count ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkText("Notebooks", size = 28.sp, weight = FontWeight.Black, maxLines = 1)
                InkText(
                    "$total notebook${if (total != 1) "s" else ""}",
                    size = 13.sp,
                    weight = FontWeight.Medium,
                    color = c.text2,
                )
            }

            // ── View switcher (left) + sort dropdown (right) ──
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewSwitch(view) { viewIdx = it.ordinal }
                SortDropdown(state.sort, vm::setNbSort)
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 62.dp + navInset + 84.dp),
            ) {
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
                    entries.forEach { e ->
                        when (e) {
                            is NbEntry.Header -> stickyHeader(key = "h:${e.label}") { GroupHeader(e.label) }
                            is NbEntry.One -> item(key = "n:${e.nb.id}") {
                                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
                                    val n = state.counts[e.nb.id] ?: 0
                                    val open = { onOpenNotebook(e.nb.id) }
                                    val menu = { vm.openCtx(e.nb.id) }
                                    if (view == NbView.Compact) NotebookListRow(e.nb, n, open, menu)
                                    else NotebookRowCard(e.nb, n, open, menu)
                                }
                            }
                            is NbEntry.Duo -> item(key = "g:${e.a.id}") {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    listOfNotNull(e.a, e.b).forEach { nb ->
                                        NotebookCard(
                                            nb,
                                            count = state.counts[nb.id] ?: 0,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onOpenNotebook(nb.id) },
                                            onMenu = { vm.openCtx(nb.id) },
                                        )
                                    }
                                    if (e.b == null) Spacer(Modifier.weight(1f))
                                }
                            }
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

// ─── grouping ───────────────────────────────────────────

/** Date headers only make sense when the list is ordered by creation date. */
private fun buildEntries(list: List<Notebook>, sort: NbSort, view: NbView): List<NbEntry> {
    val byDate = sort == NbSort.Newest || sort == NbSort.Oldest
    val groups: List<Pair<String?, List<Notebook>>> =
        if (byDate) list.groupBy { groupLabel(it.createdAt) }.map { (k, v) -> k to v }
        else listOf(null to list)
    return buildList {
        groups.forEach { (label, items) ->
            if (label != null) add(NbEntry.Header(label))
            if (view == NbView.Grid) items.chunked(2).forEach { add(NbEntry.Duo(it[0], it.getOrNull(1))) }
            else items.forEach { add(NbEntry.One(it)) }
        }
    }
}

private fun groupLabel(iso: String): String {
    val t = Iso.millis(iso)
    if (t == 0L) return "Earlier"
    fun startOfDay(daysAgo: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return when {
        t >= startOfDay(0) -> "Today"
        t >= startOfDay(1) -> "Yesterday"
        t >= startOfDay(6) -> "Last 7 days"
        else -> SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(Date(t))
    }
}

// ─── toolbar pieces ─────────────────────────────────────

@Composable
private fun ViewSwitch(current: NbView, onSelect: (NbView) -> Unit) {
    val c = Ink.colors
    val outer = RoundedCornerShape(18.dp)
    Row(
        Modifier.clip(outer).background(c.bgInput).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NbView.entries.forEach { v ->
            val on = v == current
            Box(
                Modifier
                    .width(40.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (on) c.accent else Color.Transparent)
                    .tap { onSelect(v) },
                contentAlignment = Alignment.Center,
            ) {
                FaIcon(v.icon, tint = if (on) onAccent() else c.text2, size = 16.dp)
            }
        }
    }
}

@Composable
private fun SortDropdown(current: NbSort, onSelect: (NbSort) -> Unit) {
    val c = Ink.colors
    var open by remember { mutableStateOf(false) }
    val gap = with(LocalDensity.current) { 42.dp.roundToPx() }
    Box {
        Row(
            Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.bgInput)
                .tap { open = true }
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FaIcon(
                R.drawable.ic_fa_arrow_down,
                tint = c.text2,
                size = 12.dp,
                modifier = if (current == NbSort.Oldest) Modifier.rotate(180f) else Modifier,
            )
            InkText(current.label, size = 13.sp, weight = FontWeight.SemiBold, color = c.text2)
        }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, gap),
                onDismissRequest = { open = false },
            ) {
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier
                        .width(168.dp)
                        .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
                        .clip(shape)
                        .background(c.bgCard)
                        .border(1.dp, c.borderHi, shape)
                        .padding(vertical = 6.dp),
                ) {
                    NbSort.entries.forEach { o ->
                        val on = o == current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .tap { open = false; onSelect(o) }
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            InkText(
                                o.label,
                                size = 14.sp,
                                weight = if (on) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (on) c.accent else c.text1,
                            )
                            if (on) FaIcon(R.drawable.ic_fa_check, tint = c.accent, size = 13.dp)
                        }
                    }
                }
            }
        }
    }
}

/** Sticky date band ("Today", "Yesterday", …). */
@Composable
private fun GroupHeader(label: String) {
    val c = Ink.colors
    Box(
        Modifier
            .fillMaxWidth()
            .background(lerp(c.bg, c.bgElevated, 0.55f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        InkText(label, size = 15.sp, weight = FontWeight.ExtraBold)
    }
}

// ─── cards ──────────────────────────────────────────────

private fun countText(n: Int) = "$n note${if (n != 1) "s" else ""}"

/** Tap = open, long-press = ⋮ sheet (edit / delete). */
private fun Modifier.tapOrLong(onClick: () -> Unit, onLong: () -> Unit): Modifier = composed {
    val tapNow by rememberUpdatedState(onClick)
    val longNow by rememberUpdatedState(onLong)
    pointerInput(Unit) {
        detectTapGestures(onTap = { tapNow() }, onLongPress = { longNow() })
    }
}

@Composable
private fun NotebookRowCard(nb: Notebook, count: Int, onClick: () -> Unit, onMenu: () -> Unit) {
    val c = Ink.colors
    val base = notebookColor(nb.color, c.accent)
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(5.dp, shape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.10f))
            .clip(shape)
            .background(c.bgCard)
            .border(1.dp, c.border, shape)
            .tapOrLong(onClick, onMenu)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(base.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            FaIcon(notebookIconRes(nb.icon), tint = base, size = 18.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            InkText(nb.name, size = 16.sp, weight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            InkText(countText(count), size = 13.sp, weight = FontWeight.Medium, color = c.text2)
        }
        Spacer(Modifier.width(8.dp))
        FaIcon(R.drawable.ic_fa_chevron_right, tint = c.text2, size = 14.dp)
    }
}

@Composable
private fun NotebookListRow(nb: Notebook, count: Int, onClick: () -> Unit, onMenu: () -> Unit) {
    val c = Ink.colors
    val base = notebookColor(nb.color, c.accent)
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(shape)
            .background(c.bgCard)
            .border(1.dp, c.border, shape)
            .tapOrLong(onClick, onMenu)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(base.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            FaIcon(notebookIconRes(nb.icon), tint = base, size = 14.dp)
        }
        Spacer(Modifier.width(12.dp))
        InkText(
            nb.name,
            size = 15.sp,
            weight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        InkText(countText(count), size = 12.sp, weight = FontWeight.Medium, color = c.text2)
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
