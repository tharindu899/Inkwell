package com.inkwell.notes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import com.inkwell.notes.util.Iso
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
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
 * Notebooks page: big title + count, search field, a view toggle (cards / list / grid) on the
 * left and a sort dropdown on the right, notebooks grouped under date headers when sorted by
 * date. Tap → NotebookDetail, long-press (or ⋮ in grid view) → context sheet.
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

    // Back closes the topmost layer first.
    BackHandler(enabled = state.formOpen || state.ctxId != null || state.deleteId != null) {
        when {
            state.formOpen -> vm.closeForm()
            state.deleteId != null -> vm.dismissDelete()
            else -> vm.closeCtx()
        }
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Date headers only make sense when the list is ordered by date.
    val sections = remember(state.notebooks, state.sort) {
        if (state.sort == NbSort.Newest || state.sort == NbSort.Oldest) groupByDate(state.notebooks)
        else listOf<Pair<String?, List<Notebook>>>(null to state.notebooks)
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 62.dp + navInset + 84.dp),
            ) {
                item(key = "head") { NotebooksHeader(state.notebooks.size, onBack) }
                item(key = "search") { SearchField("Search notebooks…", onSearch) }
                item(key = "toolbar") {
                    NotebooksToolbar(state.view, vm::setNbView, state.sort, vm::setNbSort)
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
                    sections.forEach { (label, group) ->
                        if (label != null) stickyHeader(key = "h-$label") { DateHeader(label) }
                        if (state.view == NbView.Grid) {
                            items(group.chunked(2), key = { "g-" + it.first().id }) { pair ->
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
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
                        } else {
                            items(group, key = { it.id }) { nb ->
                                NotebookRow(
                                    nb,
                                    count = state.counts[nb.id] ?: 0,
                                    compact = state.view == NbView.Compact,
                                    onClick = { onOpenNotebook(nb.id) },
                                    onMenu = { vm.openCtx(nb.id) },
                                )
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


// ─── header / search / toolbar ──────────────────────────

@Composable
private fun NotebooksHeader(total: Int, onBack: () -> Unit) {
    val c = Ink.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(c.bgCard).tap(onBack),
            contentAlignment = Alignment.Center,
        ) {
            FaIcon(R.drawable.ic_fa_arrow_left, tint = c.text1, size = 13.dp)
        }
        Spacer(Modifier.size(12.dp))
        InkText("Notebooks", size = 24.sp, weight = FontWeight.Black, color = c.text1, modifier = Modifier.weight(1f))
        InkText("$total notebook${if (total != 1) "s" else ""}", size = 13.sp, color = c.text2)
    }
}

/** Pill-shaped fake field; tapping it opens the Search page. */
@Composable
private fun SearchField(hint: String, onClick: () -> Unit) {
    val c = Ink.colors
    val pill = RoundedCornerShape(percent = 50)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(42.dp)
            .clip(pill)
            .background(c.bgCard)
            .border(1.dp, c.borderHi, pill)
            .tap(onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaIcon(R.drawable.ic_fa_magnifying_glass, tint = c.text2, size = 14.dp)
        Spacer(Modifier.size(10.dp))
        InkText(hint, size = 14.sp, color = c.text2)
    }
}

@Composable
private fun NotebooksToolbar(view: NbView, onView: (NbView) -> Unit, sort: NbSort, onSort: (NbSort) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ViewToggle(view, onView)
        Spacer(Modifier.weight(1f))
        SortDropdown(sort, onSort)
    }
}

/** Three-way layout switch: stacked cards · list · grid. */
@Composable
private fun ViewToggle(view: NbView, onView: (NbView) -> Unit) {
    val c = Ink.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.clip(shape).background(c.bgElevated.copy(alpha = 0.7f)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        NbView.entries.forEach { v ->
            val on = v == view
            Box(
                Modifier
                    .size(width = 42.dp, height = 32.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (on) c.accent else Color.Transparent)
                    .tap { onView(v) },
                contentAlignment = Alignment.Center,
            ) {
                ViewIcon(v, if (on) onAccent() else c.text2)
            }
        }
    }
}

@Composable
private fun ViewIcon(view: NbView, tint: Color) {
    Canvas(Modifier.size(16.dp)) {
        val w = size.width
        val u = w / 20f
        when (view) {
            NbView.Cards -> { // two stacked rounded bars
                val r = CornerRadius(3f * u)
                drawRoundRect(tint, Offset(1f * u, 2f * u), Size(18f * u, 7f * u), r)
                drawRoundRect(tint, Offset(1f * u, 11f * u), Size(18f * u, 7f * u), r)
            }
            NbView.Compact -> { // three rows: bullet + line
                listOf(4f, 10f, 16f).forEach { y ->
                    drawRoundRect(tint, Offset(1f * u, (y - 1.5f) * u), Size(3f * u, 3f * u), CornerRadius(1f * u))
                    drawRoundRect(tint, Offset(6.5f * u, (y - 1.2f) * u), Size(12.5f * u, 2.4f * u), CornerRadius(1.2f * u))
                }
            }
            NbView.Grid -> { // 2×2 outlined squares
                val stroke = Stroke(width = 1.8f * u)
                val r = CornerRadius(2.2f * u)
                listOf(1.9f, 11.1f).forEach { x ->
                    listOf(1.9f, 11.1f).forEach { y ->
                        drawRoundRect(tint, Offset(x * u, y * u), Size(7f * u, 7f * u), r, style = stroke)
                    }
                }
            }
        }
    }
}

/** "↓ Newest" pill; opens a small popup menu with every sort option. */
@Composable
private fun SortDropdown(sort: NbSort, onSort: (NbSort) -> Unit) {
    val c = Ink.colors
    var open by remember { mutableStateOf(false) }
    val gap = with(LocalDensity.current) { 40.dp.roundToPx() }
    Box {
        Row(
            Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(c.bgElevated.copy(alpha = 0.7f))
                .tap { open = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FaIcon(R.drawable.ic_fa_arrow_left, tint = c.text2, size = 11.dp, modifier = Modifier.rotate(-90f))
            Spacer(Modifier.size(8.dp))
            InkText(sort.label, size = 13.sp, color = c.text2, weight = FontWeight.Medium)
        }
        if (open) {
            Popup(alignment = Alignment.TopEnd, offset = IntOffset(0, gap), onDismissRequest = { open = false }) {
                val shape = RoundedCornerShape(18.dp)
                Column(
                    Modifier
                        .width(176.dp)
                        .shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
                        .clip(shape)
                        .background(c.bgCard)
                        .border(1.dp, c.borderHi, shape)
                        .padding(6.dp),
                ) {
                    NbSort.entries.forEach { o ->
                        val on = o == sort
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(if (on) c.accent.copy(alpha = 0.12f) else Color.Transparent)
                                .tap { open = false; onSort(o) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            InkText(
                                o.label, size = 14.sp,
                                weight = if (on) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (on) c.accent else c.text1,
                                modifier = Modifier.weight(1f),
                            )
                            if (on) FaIcon(R.drawable.ic_fa_check, tint = c.accent, size = 11.dp)
                        }
                    }
                }
            }
        }
    }
}

/** "Today" / "Yesterday" / "This week" / "November 2026" band — opaque so it can stick. */
@Composable
private fun DateHeader(label: String) {
    val c = Ink.colors
    Box(
        Modifier
            .fillMaxWidth()
            .background(lerp(c.bg, c.bgElevated, 0.6f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        InkText(label, size = 13.sp, weight = FontWeight.ExtraBold, color = c.text1)
    }
}

// ─── notebook rows (cards / list view) ──────────────────

/** Folder-style row: tinted icon tile, name, note count, chevron. Long-press opens the menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotebookRow(nb: Notebook, count: Int, compact: Boolean, onClick: () -> Unit, onMenu: () -> Unit) {
    val c = Ink.colors
    val base = notebookColor(nb.color, c.accent)
    val shape = RoundedCornerShape(if (compact) 14.dp else 20.dp)
    val tile = if (compact) 34.dp else 46.dp
    val countText = "$count note${if (count != 1) "s" else ""}"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (compact) 3.dp else 5.dp)
            .shadow(if (compact) 2.dp else 8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.07f), spotColor = Color.Black.copy(alpha = 0.07f))
            .clip(shape)
            .background(c.bgCard)
            .border(1.dp, c.border, shape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onMenu,
            )
            .heightIn(min = if (compact) 48.dp else 72.dp)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 7.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(tile)
                .clip(RoundedCornerShape(if (compact) 11.dp else 15.dp))
                .background(base.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            FaIcon(notebookIconRes(nb.icon), tint = base, size = if (compact) 14.dp else 19.dp)
        }
        Spacer(Modifier.size(if (compact) 10.dp else 14.dp))
        if (compact) {
            InkText(
                nb.name, size = 14.sp, weight = FontWeight.ExtraBold, color = c.text1,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            InkText(countText, size = 12.sp, color = c.text2)
            Spacer(Modifier.size(10.dp))
        } else {
            Column(Modifier.weight(1f)) {
                InkText(nb.name, size = 16.sp, weight = FontWeight.ExtraBold, color = c.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                InkText(countText, size = 12.sp, color = c.text2)
            }
        }
        FaIcon(R.drawable.ic_fa_chevron_right, tint = c.text2, size = if (compact) 10.dp else 13.dp)
    }
}

// ─── date grouping ──────────────────────────────────────

/** Consecutive runs of notebooks that share a date label (list is already date-sorted). */
private fun groupByDate(list: List<Notebook>): List<Pair<String?, List<Notebook>>> {
    val out = ArrayList<Pair<String, MutableList<Notebook>>>()
    list.forEach { nb ->
        val label = dateBucket(Iso.millis(nb.createdAt))
        val last = out.lastOrNull()
        if (last != null && last.first == label) last.second.add(nb) else out.add(label to mutableListOf(nb))
    }
    return out
}

private fun dateBucket(ms: Long): String {
    if (ms <= 0L) return "Earlier"
    fun dayStart(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val days = Math.round((dayStart(System.currentTimeMillis()) - dayStart(ms)) / 86_400_000.0)
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    return when {
        days <= 0 -> "Today"
        days == 1L -> "Yesterday"
        days <= 6 -> "This week"
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.MONTH) == then.get(Calendar.MONTH) -> "This month"
        else -> SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(then.time)
    }
}
