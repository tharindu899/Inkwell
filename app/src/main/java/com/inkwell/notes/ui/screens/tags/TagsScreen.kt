package com.inkwell.notes.ui.screens.tags

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.inkwell.notes.InkwellApplication
import com.inkwell.notes.data.Note
import com.inkwell.notes.data.Notebook
import com.inkwell.notes.ui.components.CircleIconButton
import com.inkwell.notes.ui.components.InkChip
import com.inkwell.notes.ui.components.InkIcon
import com.inkwell.notes.ui.components.InkIconView
import com.inkwell.notes.ui.components.ScreenHeader
import com.inkwell.notes.ui.home.noteGrid
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkType
import com.inkwell.notes.ui.theme.Lime
import com.inkwell.notes.ui.theme.Paper
import com.inkwell.notes.util.SystemBars
import com.inkwell.notes.util.sortNotes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TagsUiState(
    val tags: List<Pair<String, Int>> = emptyList(),
    val active: String? = null,
    val notes: List<Note> = emptyList(),
    val notebooks: Map<String, Notebook> = emptyMap(),
    val totalNotes: Int = 0,
)

class TagsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as InkwellApplication).repo
    private val active = MutableStateFlow<String?>(null)

    val ui: StateFlow<TagsUiState> = combine(repo.notes, repo.notebooks, repo.sortOrder, active) { notes, books, sort, act ->
        val counts = HashMap<String, Int>()
        notes.forEach { n -> n.tags.forEach { t -> counts[t] = (counts[t] ?: 0) + 1 } }
        val tags = counts.entries.map { it.key to it.value }.sortedBy { it.first.lowercase() }
        // a tag that no longer exists can't stay selected
        val current = act?.takeIf { counts.containsKey(it) }
        val sorted = sortNotes(notes, sort)
        TagsUiState(
            tags = tags,
            active = current,
            notes = if (current != null) sorted.filter { current in it.tags } else sorted,
            notebooks = books.associateBy { it.id },
            totalNotes = notes.size,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TagsUiState())

    fun toggle(tag: String) { active.value = if (active.value == tag) null else tag }
    fun clear() { active.value = null }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TagsScreen(vm: TagsViewModel, onOpenNote: (String) -> Unit, onSearch: () -> Unit) {
    SystemBars(lightNavIcons = false)
    val ui by vm.ui.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Paper),
        contentPadding = PaddingValues(bottom = 150.dp),
    ) {
        item(key = "header") {
            ScreenHeader(
                title = "Tags",
                subtitle = "${ui.tags.size} ${if (ui.tags.size == 1) "tag" else "tags"} · ${ui.totalNotes} notes",
                trailing = { CircleIconButton(InkIcon.Search, "Search notes", onSearch) },
            )
        }

        item(key = "cloud") {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 8.dp)) {
                if (ui.tags.isEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).background(Ink).padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
                            InkIconView(InkIcon.Tag, Ink, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.size(14.dp))
                        Text("No tags yet. Add tags while editing a note.", style = InkType.Body, color = Paper)
                    }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ui.tags.forEach { (tag, count) ->
                            InkChip(
                                label = tag,
                                on = ui.active == tag,
                                onClick = { vm.toggle(tag) },
                                icon = InkIcon.Tag,
                                count = count,
                            )
                        }
                    }
                }
            }
        }

        item(key = "title") {
            Row(
                Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (ui.active != null) "Notes" else "All notes", style = InkType.Title, color = Ink)
                Spacer(Modifier.size(8.dp))
                Box(Modifier.clip(CircleShape).background(Ink).padding(horizontal = 9.dp, vertical = 2.dp)) {
                    Text("${ui.notes.size}", style = InkType.Label, color = Lime)
                }
                if (ui.active != null) {
                    Spacer(Modifier.weight(1f))
                    InkChip(label = ui.active!!, on = true, onClick = vm::clear, icon = InkIcon.Close)
                }
            }
        }

        if (ui.notes.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (ui.active != null) "No notes with this tag." else "No notes yet. Tap + to write your first note.",
                    style = InkType.Body,
                    color = Ink,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        } else {
            noteGrid(ui.notes, ui.notebooks, keyPrefix = "tags-", onOpen = onOpenNote)
        }
    }
}
