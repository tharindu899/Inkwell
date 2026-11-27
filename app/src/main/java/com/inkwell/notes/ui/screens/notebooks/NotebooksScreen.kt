package com.inkwell.notes.ui.screens.notebooks

import android.app.Application
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.inkwell.notes.InkwellApplication
import com.inkwell.notes.data.Notebook
import com.inkwell.notes.data.NotebookStyle
import com.inkwell.notes.ui.components.ActionSheet
import com.inkwell.notes.ui.components.CircleIconButton
import com.inkwell.notes.ui.components.ConfirmDialog
import com.inkwell.notes.ui.components.InkChip
import com.inkwell.notes.ui.components.InkDialog
import com.inkwell.notes.ui.components.InkIcon
import com.inkwell.notes.ui.components.InkIconView
import com.inkwell.notes.ui.components.NotebookGlyph
import com.inkwell.notes.ui.components.PillButton
import com.inkwell.notes.ui.components.ScreenHeader
import com.inkwell.notes.ui.components.SheetItem
import com.inkwell.notes.ui.components.ToastData
import com.inkwell.notes.ui.components.appearOnce
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkType
import com.inkwell.notes.ui.theme.Lime
import com.inkwell.notes.ui.theme.Paper
import com.inkwell.notes.util.SystemBars
import com.inkwell.notes.util.genId
import com.inkwell.notes.util.nowIso
import com.inkwell.notes.util.parseTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class NotebookSort(val label: String) { Name("Name"), Count("Notes"), Created("Created") }

data class NotebookItem(val notebook: Notebook, val count: Int)

data class NotebooksUiState(
    val items: List<NotebookItem> = emptyList(),
    val sort: NotebookSort = NotebookSort.Name,
    val total: Int = 0,
)

class NotebooksViewModel(app: Application) : AndroidViewModel(app) {
    private val application = app as InkwellApplication
    private val repo = application.repo
    private val sort = MutableStateFlow(NotebookSort.Name)

    val ui: StateFlow<NotebooksUiState> = combine(repo.notebooks, repo.notes, sort) { books, notes, s ->
        val counts = notes.groupingBy { it.notebookId }.eachCount()
        val items = books.map { NotebookItem(it, counts[it.id] ?: 0) }
        val sorted = when (s) {
            NotebookSort.Name -> items.sortedBy { it.notebook.name.lowercase() }
            NotebookSort.Count -> items.sortedByDescending { it.count }
            NotebookSort.Created -> items.sortedByDescending { parseTime(it.notebook.createdAt) }
        }
        NotebooksUiState(sorted, s, books.size)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NotebooksUiState())

    fun setSort(s: NotebookSort) { sort.value = s }

    fun find(id: String): Notebook? = repo.notebooks.value.firstOrNull { it.id == id }

    fun save(editing: Notebook?, name: String, color: String, icon: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val nb = editing?.copy(name = clean, color = color, icon = icon)
            ?: Notebook(id = genId(), name = clean, color = color, icon = icon, createdAt = nowIso())
        repo.upsertNotebook(nb)
        application.toasts.tryEmit(ToastData(if (editing != null) "Notebook updated" else "Notebook created"))
    }

    fun delete(id: String) {
        repo.deleteNotebook(id)
        application.toasts.tryEmit(ToastData("Notebook deleted"))
    }
}

internal fun parseNotebookColor(hex: String): Color =
    try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Lime }

@Composable
fun NotebooksScreen(vm: NotebooksViewModel, onOpenNotebook: (String) -> Unit) {
    SystemBars(lightNavIcons = false)
    val ui by vm.ui.collectAsStateWithLifecycle()

    var formFor by remember { mutableStateOf<Notebook?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Notebook?>(null) }
    var deleteFor by remember { mutableStateOf<Notebook?>(null) }

    fun openForm(nb: Notebook?) { formFor = nb; showForm = true }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Paper),
        contentPadding = PaddingValues(bottom = 150.dp),
    ) {
        item(key = "header") {
            ScreenHeader(
                title = "Notebooks",
                subtitle = "${ui.total} total",
                trailing = { CircleIconButton(InkIcon.Plus, "New notebook", { openForm(null) }, filled = true) },
            )
        }
        item(key = "sort") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NotebookSort.entries.forEach { s -> InkChip(s.label, on = s == ui.sort, onClick = { vm.setSort(s) }) }
            }
        }

        if (ui.items.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
                        InkIconView(InkIcon.Book, Ink, Modifier.size(36.dp), strokeWidth = 2f)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("No notebooks yet", style = InkType.Headline, color = Ink)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Create a notebook to organise your notes by topic, project, or mood.",
                        style = InkType.Body, color = Ink,
                    )
                    Spacer(Modifier.height(20.dp))
                    PillButton("Create notebook", { openForm(null) }, Modifier.fillMaxWidth())
                }
            }
        } else {
            val rows = ui.items.chunked(2)
            itemsIndexed(rows, key = { _, r -> r.first().notebook.id }) { ri, row ->
                Row(
                    Modifier
                        .animateItem()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 12.dp)
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEachIndexed { col, item ->
                        NotebookCard(
                            item = item,
                            index = ri * 2 + col,
                            onClick = { onOpenNotebook(item.notebook.id) },
                            onMenu = { menuFor = item.notebook },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    if (showForm) {
        NotebookFormDialog(
            initial = formFor,
            onDismiss = { showForm = false },
            onSave = { name, color, icon ->
                vm.save(formFor, name, color, icon)
                showForm = false
            },
        )
    }

    menuFor?.let { nb ->
        ActionSheet(
            title = nb.name,
            items = listOf(
                SheetItem("Edit notebook", InkIcon.Pen) { openForm(nb) },
                SheetItem("View notes", InkIcon.Book) { onOpenNotebook(nb.id) },
                SheetItem("Delete notebook", InkIcon.Trash, danger = true) { deleteFor = nb },
            ),
            onDismiss = { menuFor = null },
        )
    }

    deleteFor?.let { nb ->
        ConfirmDialog(
            title = "Delete notebook?",
            message = "Notes in this notebook will not be deleted — they'll just become unorganised.",
            confirmLabel = "Delete",
            onCancel = { deleteFor = null },
            onConfirm = { vm.delete(nb.id); deleteFor = null },
        )
    }
}

@Composable
private fun NotebookCard(item: NotebookItem, index: Int, onClick: () -> Unit, onMenu: () -> Unit, modifier: Modifier = Modifier) {
    val nb = item.notebook
    val color = parseNotebookColor(nb.color)
    val shape = RoundedCornerShape(28.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "nbScale",
    )
    Box(
        modifier
            .appearOnce("nb-" + nb.id, index)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = 156.dp)
            .clip(shape)
            .background(color)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
    ) {
        Column(Modifier.padding(18.dp)) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                NotebookGlyph(nb.icon, color, Modifier.size(24.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(nb.name, style = InkType.Title, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text("${item.count} note${if (item.count != 1) "s" else ""}", style = InkType.Label, color = Ink)
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onMenu)
                .semantics { contentDescription = "Options for ${nb.name}" },
            contentAlignment = Alignment.Center,
        ) { InkIconView(InkIcon.More, Ink, Modifier.size(20.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotebookFormDialog(initial: Notebook?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color ?: NotebookStyle.colors.first()) }
    var icon by remember { mutableStateOf(initial?.icon ?: NotebookStyle.icons.first()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (initial == null) focus.requestFocus() }

    InkDialog(onDismiss) {
        Text(if (initial != null) "Edit notebook" else "New notebook", style = InkType.Headline.copy(fontSize = 24.sp), color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            if (initial != null) "Update name, colour, or icon." else "Name it, pick a colour and an icon.",
            style = InkType.Body, color = Ink,
        )
        Spacer(Modifier.height(18.dp))

        FieldLabel("Name")
        BasicTextField(
            value = name,
            onValueChange = { if (it.length <= 32) name = it },
            singleLine = true,
            textStyle = InkType.Body.copy(fontSize = 16.sp, color = Ink),
            cursorBrush = SolidColor(Ink),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .border(2.dp, Ink, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    if (name.isEmpty()) Text("e.g. Work, Ideas…", style = InkType.Body.copy(fontSize = 16.sp), color = Color(0xFF6B6B64))
                    inner()
                }
            },
        )

        Spacer(Modifier.height(16.dp))
        FieldLabel("Colour")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NotebookStyle.colors.forEach { c ->
                val sel = c.equals(color, ignoreCase = true)
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(parseNotebookColor(c))
                        .then(if (sel) Modifier.border(3.dp, Ink, CircleShape) else Modifier)
                        .clickable(role = Role.RadioButton) { color = c }
                        .semantics { contentDescription = "Colour $c" },
                    contentAlignment = Alignment.Center,
                ) { if (sel) InkIconView(InkIcon.Check, Ink, Modifier.size(16.dp), strokeWidth = 3f) }
            }
        }

        Spacer(Modifier.height(16.dp))
        FieldLabel("Icon")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NotebookStyle.icons.forEach { ic ->
                val sel = ic == icon
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .then(if (sel) Modifier.background(Ink) else Modifier.border(2.dp, Ink.copy(alpha = 0.25f), RoundedCornerShape(14.dp)))
                        .clickable(role = Role.RadioButton) { icon = ic }
                        .semantics { contentDescription = "Icon ${ic.removePrefix("fa-")}" },
                    contentAlignment = Alignment.Center,
                ) { NotebookGlyph(ic, if (sel) Lime else Ink, Modifier.size(22.dp)) }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Cancel", onDismiss, Modifier.weight(1f), filled = false)
            PillButton("Save", { onSave(name, color, icon) }, Modifier.weight(1f), enabled = name.isNotBlank())
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = InkType.Label.copy(fontSize = 13.sp), color = Ink, modifier = Modifier.padding(bottom = 8.dp))
}
