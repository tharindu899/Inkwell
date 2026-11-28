package com.inkwell.notes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inkwell.notes.R
import com.inkwell.notes.data.DateField
import com.inkwell.notes.data.DateRange
import com.inkwell.notes.data.DayPart
import com.inkwell.notes.data.NoteFilter
import com.inkwell.notes.data.NoteOrder
import com.inkwell.notes.data.applyNoteFilter
import com.inkwell.notes.ui.components.BottomNav
import com.inkwell.notes.ui.components.ContextSheet
import com.inkwell.notes.ui.components.DeleteRed
import com.inkwell.notes.ui.components.EmptyState
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.IconBtn
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.components.InnerTopBar
import com.inkwell.notes.ui.components.NavTab
import com.inkwell.notes.ui.components.NoteCard
import com.inkwell.notes.ui.components.onAccent
import com.inkwell.notes.ui.components.tap
import com.inkwell.notes.ui.theme.Ink
import java.text.Collator

/**
 * All Notes page (replaces the old Tags page).
 *
 * Every note in one list. The filter icon (next to search) opens a sheet to pick tags and to
 * sort / narrow by date and time of day. The FAB opens a speed dial (new note / notebook / tag, search, manage tags);
 * Settings → "Manage tags" lands here with that manager already open ([openManageTags]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllNotesScreen(
    openManageTags: Boolean,
    onBack: () -> Unit,
    onNavTab: (NavTab) -> Unit,
    onSearch: () -> Unit,
    onOpenNote: (String) -> Unit = {},
    onNewNote: (String?) -> Unit = {},
    vm: HomeViewModel = viewModel(),
    avm: AllNotesViewModel = viewModel(),
) {
    val c = Ink.colors
    val hs by vm.state.collectAsStateWithLifecycle()
    val st by avm.state.collectAsStateWithLifecycle()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Notes may have changed on another screen since this view model last loaded.
    LaunchedEffect(Unit) { vm.reload() }
    LaunchedEffect(openManageTags) { if (openManageTags) avm.openManage() }

    // tag → note count, A→Z
    val tags: List<Pair<String, Int>> = remember(hs.notes) {
        val collator = Collator.getInstance()
        hs.notes
            .flatMap { it.tags }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedWith(Comparator { a, b -> collator.compare(a.first, b.first) })
    }
    // A tag that no longer exists on any note can't stay selected in the filter.
    LaunchedEffect(tags) { avm.prune(tags.map { it.first }.toSet()) }

    val filter = st.filter
    val filtered = remember(hs.notes, filter) { applyNoteFilter(hs.notes, filter) }
    val notebooksById = remember(hs.notebooks) { hs.notebooks.associateBy { it.id } }

    val layerOpen = st.filterOpen || st.manageOpen || st.renaming != null || st.deleting != null
    BackHandler(enabled = layerOpen && !hs.createMenuOpen) { avm.closeTopLayer() }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            InnerTopBar(
                title = "Notes",
                onBack = onBack,
                actions = {
                    FilterButton(count = filter.activeCount, onClick = avm::openFilter)
                    IconBtn(R.drawable.ic_fa_magnifying_glass, onSearch)
                },
            )

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 62.dp + navInset + 80.dp),
            ) {
                item(key = "h") {
                    SectionHeader(null, if (filter.isActive) "Filtered notes" else "Notes", filtered.size, topPadding = 0.dp)
                }

                if (filter.isActive) {
                    item(key = "active-filters") {
                        ActiveFilters(filter, avm, Modifier.padding(bottom = 12.dp))
                    }
                }

                if (filtered.isEmpty()) {
                    item(key = "empty") {
                        if (hs.notes.isEmpty()) {
                            EmptyState(
                                icon = R.drawable.ic_fa_note_sticky,
                                title = "No notes yet",
                                sub = "Notes you write will show up here.",
                            )
                        } else {
                            EmptyState(
                                icon = R.drawable.ic_fa_filter,
                                title = "No notes match",
                                sub = "Try changing or resetting the filter.",
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { n ->
                        NoteCard(n, n.notebookId?.let(notebooksById::get), onClick = { onOpenNote(n.id) })
                    }
                }
            }
        }

        BottomNav(
            active = NavTab.AllNotes,
            onSelect = onNavTab,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // FAB speed dial (same as Home) + tag manager shortcut
        CreateLayer(
            state = hs,
            vm = vm,
            onNewNote = { onNewNote(null) },
            onSearch = onSearch,
            onManageTags = avm::openManage,
            onNotebookCreated = { onNavTab(NavTab.Notebooks) },
            onTagCreated = { onNewNote(it) },
        )

        if (st.filterOpen) {
            FilterSheet(
                filter = filter,
                tags = tags,
                resultCount = filtered.size,
                avm = avm,
            )
        }

        if (st.manageOpen) {
            TagManagerSheet(
                tags = tags,
                onClose = avm::closeManage,
                onFilter = avm::filterOnlyTag,
                onEdit = avm::startRename,
                onDelete = avm::askDelete,
            )
        }

        st.renaming?.let { tag ->
            TagEditModal(
                tag = tag,
                onDismiss = avm::cancelRename,
                onSave = { text ->
                    avm.cancelRename()
                    vm.renameTag(tag, text)?.let { new -> avm.onTagRenamed(tag, new) }
                },
            )
        }

        st.deleting?.let { tag ->
            TagDeleteModal(
                tag = tag,
                count = tags.firstOrNull { it.first == tag }?.second ?: 0,
                onCancel = avm::cancelDelete,
                onConfirm = {
                    avm.cancelDelete()
                    vm.deleteTag(tag)
                },
            )
        }
    }
}

// ─── top bar filter button ──────────────────────────────

/** 38dp round button like the search one; shows an accent badge with the number of active filters. */
@Composable
private fun FilterButton(count: Int, onClick: () -> Unit) {
    val c = Ink.colors
    val active = count > 0
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(38.dp).tap(onClick)) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (active) c.accent.copy(alpha = 0.18f) else c.bgCard),
            contentAlignment = Alignment.Center,
        ) {
            FaIcon(R.drawable.ic_fa_filter, tint = if (active) c.accent else c.text2, size = 15.dp)
        }
        if (active) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                    .clip(CircleShape)
                    .background(c.accent),
                contentAlignment = Alignment.Center,
            ) {
                InkText(count.toString(), size = 9.sp, weight = FontWeight.Black, color = onAccent())
            }
        }
    }
}

// ─── active filter chips under the header ───────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilters(filter: NoteFilter, avm: AllNotesViewModel, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filter.tags.sorted().forEach { t ->
            ActiveChip(t, R.drawable.ic_fa_tag) { avm.toggleTag(t) }
        }
        if (filter.range != DateRange.Any) {
            ActiveChip("${filter.dateField.label}: ${filter.range.label}", R.drawable.ic_fa_clock) { avm.setRange(DateRange.Any) }
        }
        filter.dayParts.sortedBy { it.ordinal }.forEach { p ->
            ActiveChip("${filter.dateField.label} ${p.label.lowercase()}", R.drawable.ic_fa_clock) { avm.toggleDayPart(p) }
        }
        if (filter.order != NoteOrder.Newest) {
            ActiveChip(filter.order.label, R.drawable.ic_fa_turn_up) { avm.setOrder(NoteOrder.Newest) }
        }
        if (filter.dateField != DateField.Modified) {
            ActiveChip("By ${filter.dateField.label.lowercase()} date", R.drawable.ic_fa_clock) { avm.setDateField(DateField.Modified) }
        }
        ClearAllChip(avm::resetFilter)
    }
}

/** Accent pill with an ✕ — tapping it removes that one filter. */
@Composable
private fun ActiveChip(label: String, icon: Int, onClear: () -> Unit) {
    val c = Ink.colors
    val pill = RoundedCornerShape(percent = 50)
    val subtle = if (c.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)
    Row(
        Modifier
            .height(28.dp)
            .clip(pill)
            .background(c.accent.copy(alpha = 0.12f))
            .border(1.dp, c.accent.copy(alpha = 0.22f), pill)
            .tap(onClear)
            .padding(start = 9.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaIcon(icon, tint = c.accent, size = 9.dp)
        Spacer(Modifier.size(6.dp))
        InkText(
            label,
            size = 11.sp,
            weight = FontWeight.ExtraBold,
            color = c.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
        Spacer(Modifier.size(8.dp))
        Box(Modifier.size(18.dp).clip(CircleShape).background(subtle), contentAlignment = Alignment.Center) {
            FaIcon(R.drawable.ic_fa_xmark, tint = c.accent, size = 8.dp)
        }
    }
}

@Composable
private fun ClearAllChip(onClick: () -> Unit) {
    val c = Ink.colors
    val pill = RoundedCornerShape(percent = 50)
    Row(
        Modifier
            .height(28.dp)
            .clip(pill)
            .background(c.bgCard)
            .border(1.dp, c.border, pill)
            .tap(onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaIcon(R.drawable.ic_fa_rotate_left, tint = c.text2, size = 10.dp)
        Spacer(Modifier.size(6.dp))
        InkText("Clear all", size = 11.sp, weight = FontWeight.ExtraBold, color = c.text2)
    }
}

// ─── filter sheet ───────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    filter: NoteFilter,
    tags: List<Pair<String, Int>>,
    resultCount: Int,
    avm: AllNotesViewModel,
) {
    val c = Ink.colors
    ContextSheet(title = "Filter & sort", onDismiss = avm::closeFilter) {
        Column(
            Modifier
                .heightIn(max = 430.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
        ) {
            // ── Tags ──
            FilterLabel("Tags", if (filter.tags.isNotEmpty()) "Clear" else null, avm::clearTags)
            if (tags.isEmpty()) {
                InkText("No tags yet. Add tags while editing a note.", size = 13.sp, color = c.text2, lineHeight = 20.sp)
            } else {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { (name, count) ->
                        TagChip(name, count, active = name in filter.tags) { avm.toggleTag(name) }
                    }
                }
                if (filter.tags.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OptionChip("Match any tag", !filter.matchAll) { avm.setMatchAll(false) }
                        OptionChip("Match all tags", filter.matchAll) { avm.setMatchAll(true) }
                    }
                }
            }

            // ── Sort ──
            FilterLabel("Sort by")
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NoteOrder.entries.forEach { o -> OptionChip(o.label, filter.order == o) { avm.setOrder(o) } }
            }

            // ── Date ──
            FilterLabel("Date based on")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField.entries.forEach { d -> OptionChip(d.label, filter.dateField == d) { avm.setDateField(d) } }
            }
            FilterLabel("Date")
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateRange.entries.forEach { r -> OptionChip(r.label, filter.range == r) { avm.setRange(r) } }
            }

            // ── Time of day ──
            FilterLabel("Time of day", if (filter.dayParts.isNotEmpty()) "Clear" else null, avm::clearDayParts)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DayPart.entries.forEach { p ->
                    OptionChip("${p.label} · ${p.hours}", p in filter.dayParts) { avm.toggleDayPart(p) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (filter.isActive) SheetButton("Reset", primary = false, modifier = Modifier.weight(1f), onClick = avm::resetFilter)
            SheetButton(
                "Show $resultCount note${if (resultCount != 1) "s" else ""}",
                primary = true,
                modifier = Modifier.weight(if (filter.isActive) 2f else 1f),
                onClick = avm::closeFilter,
            )
        }
    }
}

@Composable
private fun FilterLabel(text: String, action: String? = null, onAction: () -> Unit = {}) {
    val c = Ink.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkText(
            text.uppercase(),
            size = 11.sp,
            weight = FontWeight.ExtraBold,
            color = c.text3,
            letterSpacing = 0.8.sp,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            InkText(action, size = 12.sp, weight = FontWeight.ExtraBold, color = c.accent, modifier = Modifier.tap(onAction).padding(4.dp))
        }
    }
}

/** Selectable pill used for sort / date / time options. */
@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Ink.colors
    val pill = RoundedCornerShape(percent = 50)
    Row(
        Modifier
            .height(34.dp)
            .clip(pill)
            .background(if (selected) c.accent.copy(alpha = 0.20f) else c.bgCard)
            .border(1.dp, if (selected) c.accent.copy(alpha = 0.38f) else c.border, pill)
            .tap(onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            FaIcon(R.drawable.ic_fa_check, tint = c.accent, size = 10.dp)
            Spacer(Modifier.size(6.dp))
        }
        InkText(label, size = 12.sp, weight = FontWeight.ExtraBold, color = if (selected) c.accent else c.text1)
    }
}

/** `.all-tag-chip` — pill with tag icon, name and count; accent-tinted when active. */
@Composable
private fun TagChip(name: String, count: Int, active: Boolean, onClick: () -> Unit) {
    val c = Ink.colors
    val pill = RoundedCornerShape(percent = 50)
    val subtle = if (c.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)
    Row(
        Modifier
            .widthIn(max = 170.dp)
            .height(30.dp)
            .clip(pill)
            .background(if (active) c.accent.copy(alpha = 0.20f) else c.bgCard)
            .border(1.dp, if (active) c.accent.copy(alpha = 0.38f) else c.border, pill)
            .tap(onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaIcon(if (active) R.drawable.ic_fa_check else R.drawable.ic_fa_tag, tint = c.accent, size = 9.dp)
        Spacer(Modifier.size(6.dp))
        InkText(
            name,
            size = 11.sp,
            weight = FontWeight.ExtraBold,
            color = if (active) c.accent else c.text1,
            letterSpacing = 0.22.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.size(6.dp))
        Box(
            Modifier
                .defaultMinSize(minWidth = 16.dp)
                .height(16.dp)
                .clip(CircleShape)
                .background(if (active) c.accent.copy(alpha = 0.18f) else subtle)
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            InkText(count.toString(), size = 9.sp, weight = FontWeight.Black, color = if (active) c.accent else c.text2)
        }
    }
}

@Composable
private fun SheetButton(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ink.colors
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier
            .heightIn(min = 46.dp)
            .clip(shape)
            .background(if (primary) c.accent else c.bgCard)
            .border(1.dp, if (primary) c.accent else c.border, shape)
            .tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        InkText(label, size = 14.sp, weight = FontWeight.Black, color = if (primary) onAccent() else c.text2)
    }
}

// ─── tag manager sheet ──────────────────────────────────

@Composable
private fun TagManagerSheet(
    tags: List<Pair<String, Int>>,
    onClose: () -> Unit,
    onFilter: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val c = Ink.colors
    ContextSheet(title = "Manage tags", onDismiss = onClose) {
        if (tags.isEmpty()) {
            InkText(
                "No tags yet. Add tags while editing a note.",
                size = 13.sp,
                color = c.text2,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        } else {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                tags.forEach { (name, count) ->
                    TagRow(name, count, onOpen = { onFilter(name) }, onEdit = { onEdit(name) }, onDelete = { onDelete(name) })
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SheetButton("Done", primary = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp), onClick = onClose)
    }
}

/** One tag: tap the name to show its notes, pencil = rename, bin = delete. */
@Composable
private fun TagRow(name: String, count: Int, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val c = Ink.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .tap(onOpen)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.bgCard),
            contentAlignment = Alignment.Center,
        ) {
            FaIcon(R.drawable.ic_fa_tag, tint = c.accent, size = 15.dp)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            InkText(name, size = 14.sp, weight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            InkText("$count note${if (count != 1) "s" else ""}", size = 12.sp, color = c.text3)
        }
        RoundAction(R.drawable.ic_fa_r_pen_to_square, c.accent, c.bgCard, onEdit)
        Spacer(Modifier.size(6.dp))
        RoundAction(R.drawable.ic_fa_trash_can, DeleteRed, DeleteRed.copy(alpha = 0.12f), onDelete)
    }
}

@Composable
private fun RoundAction(icon: Int, tint: Color, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(bg).tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        FaIcon(icon, tint = tint, size = 15.dp)
    }
}
