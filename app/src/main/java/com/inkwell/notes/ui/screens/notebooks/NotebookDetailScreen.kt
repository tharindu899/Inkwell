package com.inkwell.notes.ui.screens.notebooks

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.inkwell.notes.InkwellApplication
import com.inkwell.notes.data.Note
import com.inkwell.notes.data.Notebook
import com.inkwell.notes.data.SortOrder
import com.inkwell.notes.ui.components.ActionSheet
import com.inkwell.notes.ui.components.CircleIconButton
import com.inkwell.notes.ui.components.ConfirmDialog
import com.inkwell.notes.ui.components.InkChip
import com.inkwell.notes.ui.components.InkIcon
import com.inkwell.notes.ui.components.InkIconView
import com.inkwell.notes.ui.components.NotebookGlyph
import com.inkwell.notes.ui.components.ScreenHeader
import com.inkwell.notes.ui.components.SheetItem
import com.inkwell.notes.ui.components.ToastData
import com.inkwell.notes.ui.home.noteGrid
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkType
import com.inkwell.notes.ui.theme.Lime
import com.inkwell.notes.ui.theme.Paper
import com.inkwell.notes.util.SystemBars
import com.inkwell.notes.util.sortNotes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class NotebookDetailState(
    val notebook: Notebook? = null,
    val notes: List<Note> = emptyList(),
    val sort: SortOrder = SortOrder.Modified,
)

class NotebookDetailViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val application = app as InkwellApplication
    private val repo = application.repo
    val id: String = handle.get<String>("id").orEmpty()

    val ui: StateFlow<NotebookDetailState> = combine(repo.notebooks, repo.notes, repo.sortOrder) { books, notes, sort ->
        NotebookDetailState(
            notebook = books.firstOrNull { it.id == id },
            notes = sortNotes(notes.filter { it.notebookId == id }, sort),
            sort = sort,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NotebookDetailState(notebook = repo.notebooks.value.firstOrNull { it.id == id }))

    fun setSort(s: SortOrder) = repo.setSort(s)

    fun delete() {
        repo.deleteNotebook(id)
        application.toasts.tryEmit(ToastData("Notebook deleted"))
    }
}

@Composable
fun NotebookDetailScreen(
    vm: NotebookDetailViewModel,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onSearch: () -> Unit,
) {
    SystemBars(lightNavIcons = false)
    val ui by vm.ui.collectAsStateWithLifecycle()

    var leaving by remember { mutableStateOf(false) }
    var lastNb by remember { mutableStateOf(ui.notebook) }
    if (ui.notebook != null) lastNb = ui.notebook

    // notebook vanished for some other reason (e.g. deleted elsewhere) -> go back once
    LaunchedEffect(ui.notebook == null) {
        if (ui.notebook == null && !leaving) onBack()
    }

    val nb = lastNb ?: return
    val color = parseNotebookColor(nb.color)

    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val booksMap = remember(nb) { mapOf(nb.id to nb) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Paper),
        contentPadding = PaddingValues(bottom = 150.dp),
    ) {
        item(key = "header") {
            ScreenHeader(
                title = nb.name,
                subtitle = "${ui.notes.size} note${if (ui.notes.size != 1) "s" else ""}",
                background = color,
                onBack = onBack,
                iconSlot = {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                        NotebookGlyph(nb.icon, color, Modifier.size(28.dp))
                    }
                },
                trailing = { CircleIconButton(InkIcon.More, "Notebook options", { menu = true }) },
            )
        }
        item(key = "sort") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SortOrder.entries.forEach { s -> InkChip(s.label, on = s == ui.sort, onClick = { vm.setSort(s) }) }
            }
        }
        if (ui.notes.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
                        InkIconView(InkIcon.Pen, Ink, Modifier.size(36.dp), strokeWidth = 2f)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("No notes yet", style = InkType.Headline, color = Ink)
                    Spacer(Modifier.height(6.dp))
                    Text("Tap + to write your first note in this notebook.", style = InkType.Body, color = Ink)
                }
            }
        } else {
            noteGrid(ui.notes, booksMap, keyPrefix = "nb-", onOpen = onOpenNote)
        }
    }

    if (menu) {
        ActionSheet(
            title = nb.name,
            items = listOf(
                SheetItem("All notebooks", InkIcon.Book) { onBack() },
                SheetItem("Search in all notes", InkIcon.Search) { onSearch() },
                SheetItem("Delete notebook", InkIcon.Trash, danger = true) { confirm = true },
            ),
            onDismiss = { menu = false },
        )
    }
    if (confirm) {
        ConfirmDialog(
            title = "Delete notebook?",
            message = "Notes in this notebook will stay but won't be in any notebook.",
            confirmLabel = "Delete",
            onCancel = { confirm = false },
            onConfirm = {
                confirm = false
                leaving = true
                vm.delete()
                onBack()
            },
        )
    }
}
