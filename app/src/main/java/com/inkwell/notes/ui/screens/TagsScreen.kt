package com.inkwell.notes.ui.screens

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.inkwell.notes.ui.components.BottomNav
import com.inkwell.notes.ui.components.EmptyState
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.IconBtn
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.components.InnerTopBar
import com.inkwell.notes.ui.components.NavTab
import com.inkwell.notes.ui.components.NoteCard
import com.inkwell.notes.ui.components.tap
import com.inkwell.notes.ui.theme.Ink
import java.text.Collator

/**
 * Port of `src/pages/Tags.jsx`.
 *
 * All tags as chips (with note counts); tapping a chip filters the note list below,
 * tapping it again (or the ✕ on the label) clears the filter. With no filter the page
 * lists every note. Notes, sort order, FAB, Create menu and toast all come from the
 * shared [HomeViewModel] (the web's `useAppStore()`).
 *
 * TODO Editor: note tap, "new note" actions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsScreen(
    initialTag: String?,
    onBack: () -> Unit,
    onNavTab: (NavTab) -> Unit,
    onSearch: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val c = Ink.colors
    val state by vm.state.collectAsStateWithLifecycle()

    // Notes may have changed on another screen since this view model last loaded.
    LaunchedEffect(Unit) { vm.reload() }

    var activeTag by rememberSaveable { mutableStateOf(initialTag) }

    // tagMap → [(tag, count)] sorted A→Z (web: Object.entries(tagMap).sort(localeCompare))
    val tags = remember(state.notes) {
        val collator = Collator.getInstance()
        state.notes
            .flatMap { it.tags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(Comparator { a, b -> collator.compare(a.key, b.key) })
    }
    // state.notes is already `sortNotes(notes)` (pinned first, then the chosen sort).
    val filtered = remember(state.notes, activeTag) {
        val t = activeTag
        if (t == null) emptyList() else state.notes.filter { t in it.tags }
    }
    val notebooksById = remember(state.notebooks) { state.notebooks.associateBy { it.id } }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            InnerTopBar(
                title = "Tags",
                onBack = onBack,
                actions = { IconBtn(R.drawable.ic_fa_magnifying_glass, onSearch) },
            )

            // .page-content.compact-page.tags-page
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 62.dp + navInset + 14.dp),
            ) {
                // ── Tag cloud ──
                item(key = "tags-h") {
                    SectionHeader(null, "All tags", tags.size, topPadding = 0.dp)
                }
                if (tags.isEmpty()) {
                    item(key = "tags-help") { TagsHelpCard() }
                } else {
                    item(key = "tags-row") {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            tags.forEach { (name, count) ->
                                TagChip(
                                    name = name,
                                    count = count,
                                    active = activeTag == name,
                                    onClick = { activeTag = if (activeTag == name) null else name },
                                )
                            }
                        }
                    }
                }

                val tag = activeTag
                if (tag != null) {
                    // ── Filtered results ──
                    item(key = "notes-h") { SectionHeader(null, "Notes", filtered.size) }
                    item(key = "active-tag") {
                        Box(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                            ActiveTagLabel(tag, onClear = { activeTag = null })
                        }
                    }
                    if (filtered.isEmpty()) {
                        item(key = "filtered-empty") {
                            EmptyState(
                                icon = R.drawable.ic_fa_tag,
                                title = "No notes with this tag",
                                sub = "Add the tag \"$tag\" to a note in the editor.",
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { n ->
                            NoteCard(n, n.notebookId?.let(notebooksById::get), onClick = { /* TODO Editor */ })
                        }
                    }
                } else {
                    // ── All notes (default view) ──
                    item(key = "all-h") { SectionHeader(null, "All notes", state.notes.size) }
                    if (state.notes.isEmpty()) {
                        item(key = "all-empty") {
                            EmptyState(
                                icon = R.drawable.ic_fa_note_sticky,
                                title = "No notes yet",
                                sub = "Tap + to write your first note.",
                            )
                        }
                    } else {
                        items(state.notes, key = { it.id }) { n ->
                            NoteCard(n, n.notebookId?.let(notebooksById::get), onClick = { /* TODO Editor */ })
                        }
                    }
                }
            }
        }

        BottomNav(
            active = NavTab.Tags,
            onSelect = { tab ->
                vm.closeCreateMenu()
                onNavTab(tab)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        CreateLayer(
            state = state,
            vm = vm,
            onNewNote = { /* TODO Editor (?new=1) */ },
            onSearch = onSearch,
            onImage = { /* TODO Editor (?new=1&type=image) */ },
            onVoice = { /* TODO Editor (?new=1&type=voice) */ },
            onChecklist = { /* TODO Editor (?new=1&type=checklist) */ },
            onNotebookCreated = { onNavTab(NavTab.Notebooks) },
            onTagCreated = { /* TODO Editor: new note with this tag (?new=1&tag=$it) */ },
        )
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
            .widthIn(max = 150.dp)
            .height(28.dp)
            .clip(pill)
            .background(if (active) c.accent.copy(alpha = 0.20f) else c.bgCard)
            .border(1.dp, if (active) c.accent.copy(alpha = 0.38f) else c.border, pill)
            .tap(onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaIcon(R.drawable.ic_fa_tag, tint = c.accent, size = 9.dp)
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
            InkText(
                count.toString(),
                size = 9.sp,
                weight = FontWeight.Black,
                color = if (active) c.accent else c.text2,
            )
        }
    }
}

/** `.active-tag-label.active-note-tag` — the selected tag with a ✕ to clear it. */
@Composable
private fun ActiveTagLabel(tag: String, onClear: () -> Unit) {
    val c = Ink.colors
    val pill = RoundedCornerShape(percent = 50)
    val subtle = if (c.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)
    Row(
        Modifier
            .height(28.dp)
            .clip(pill)
            .background(c.accent.copy(alpha = 0.12f))
            .border(1.dp, c.accent.copy(alpha = 0.22f), pill)
            .padding(start = 9.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaIcon(R.drawable.ic_fa_tag, tint = c.accent, size = 9.dp)
        Spacer(Modifier.size(6.dp))
        InkText(
            tag,
            size = 11.sp,
            weight = FontWeight.ExtraBold,
            color = c.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.size(8.dp))
        Box(
            Modifier.size(18.dp).clip(CircleShape).background(subtle).tap(onClear),
            contentAlignment = Alignment.Center,
        ) {
            FaIcon(R.drawable.ic_fa_xmark, tint = c.accent, size = 8.dp)
        }
    }
}

/** `.tags-help-card` */
@Composable
private fun TagsHelpCard() {
    val c = Ink.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 16.dp)
            .clip(shape)
            .background(c.bgCard)
            .border(1.dp, c.border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaIcon(R.drawable.ic_fa_circle_info, tint = c.accent, size = 13.dp)
        Spacer(Modifier.size(6.dp))
        InkText("No tags yet. Add tags while editing a note.", size = 13.sp, color = c.text2, lineHeight = 20.sp)
    }
}
