package com.inkwell.notes.ui.screens.search

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.inkwell.notes.ui.screens.notebooks.parseNotebookColor
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkType
import com.inkwell.notes.ui.theme.Lime
import com.inkwell.notes.ui.theme.Paper
import com.inkwell.notes.util.SystemBars
import com.inkwell.notes.util.formatDate
import com.inkwell.notes.util.parseTime
import com.inkwell.notes.util.stripHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

enum class SearchMode(val label: String) { All("All"), Title("Title"), Content("Content"), Tags("Tags") }

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as InkwellApplication).repo

    private val query = MutableStateFlow("")
    private val mode = MutableStateFlow(SearchMode.All)
    private val notebookFilter = MutableStateFlow<String?>(null)

    val modeState: StateFlow<SearchMode> = mode
    val notebookState: StateFlow<String?> = notebookFilter
    val notebooks = repo.notebooks
    val recents = repo.recentSearches

    // note id -> (updatedAt, lower-cased plain text) so typing stays fast
    private val textCache = HashMap<String, Pair<String, String>>()

    /** null = no query typed (show recent searches) */
    val results: StateFlow<List<Note>?> = combine(query, mode, notebookFilter, repo.notes) { q, m, nb, notes ->
        val needle = q.trim().lowercase()
        if (needle.isEmpty()) {
            null
        } else {
            notes.filter { n ->
                (nb == null || n.notebookId == nb) && when (m) {
                    SearchMode.Title -> titleHit(n, needle)
                    SearchMode.Content -> contentHit(n, needle)
                    SearchMode.Tags -> tagHit(n, needle)
                    SearchMode.All -> titleHit(n, needle) || contentHit(n, needle) || tagHit(n, needle)
                }
            }.sortedByDescending { parseTime(it.updatedAt) }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun titleHit(n: Note, q: String) = n.title.lowercase().contains(q)
    private fun tagHit(n: Note, q: String) = n.tags.any { it.lowercase().contains(q) }
    private fun contentHit(n: Note, q: String): Boolean {
        val cached = textCache[n.id]
        val text = if (cached != null && cached.first == n.updatedAt) cached.second else {
            val plain = stripHtml(n.content, limit = 0).lowercase() + " " + n.markdown.lowercase()
            textCache[n.id] = n.updatedAt to plain
            plain
        }
        return text.contains(q)
    }

    fun setQuery(q: String) { query.value = q }
    fun setMode(m: SearchMode) { mode.value = m }
    fun toggleNotebook(id: String) { notebookFilter.value = if (notebookFilter.value == id) null else id }
    fun commit(q: String) = repo.addSearch(q)
    fun clearRecents() = repo.clearSearches()
}

@Composable
fun SearchScreen(vm: SearchViewModel, onBack: () -> Unit, onOpenNote: (String) -> Unit) {
    SystemBars(lightNavIcons = false)
    var text by rememberSaveable { mutableStateOf("") }
    val mode by vm.modeState.collectAsStateWithLifecycle()
    val nbFilter by vm.notebookState.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val notebooks by vm.notebooks.collectAsStateWithLifecycle()
    val booksMap = remember(notebooks) { notebooks.associateBy { it.id } }

    LaunchedEffect(Unit) { vm.setQuery(text) }
    val keyboard = LocalSoftwareKeyboardController.current
    val q = text.trim()

    Column(Modifier.fillMaxSize().background(Paper).imePadding()) {
        ScreenHeader(title = "Search", big = false, onBack = onBack) {
            Spacer(Modifier.height(14.dp))
            SearchField(
                text = text,
                onChange = { text = it; vm.setQuery(it) },
                onClear = { text = ""; vm.setQuery("") },
                onSearch = { vm.commit(text); keyboard?.hide() },
            )
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item(key = "modes") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchMode.entries.forEach { m -> InkChip(m.label, on = m == mode, onClick = { vm.setMode(m) }) }
                }
            }
            if (notebooks.isNotEmpty()) {
                item(key = "books") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        notebooks.forEach { nb ->
                            InkChip(
                                label = nb.name,
                                on = nbFilter == nb.id,
                                onClick = { vm.toggleNotebook(nb.id) },
                                icon = InkIcon.Book,
                                activeBg = parseNotebookColor(nb.color),
                                activeFg = Ink,
                            )
                        }
                    }
                }
            }

            val res = results
            if (res == null) {
                item(key = "recentHeader") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Recent", style = InkType.Title, color = Ink, modifier = Modifier.weight(1f))
                        if (recents.isNotEmpty()) {
                            Text(
                                "Clear all",
                                style = InkType.Label.copy(fontSize = 14.sp),
                                color = Ink,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button) { vm.clearRecents() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                if (recents.isEmpty()) {
                    item(key = "noRecent") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            InkIconView(InkIcon.Clock, Ink.copy(alpha = 0.6f), Modifier.size(32.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("No recent searches", style = InkType.Body, color = Ink)
                        }
                    }
                } else {
                    items(recents, key = { "recent-$it" }) { s ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { text = s; vm.setQuery(s); vm.commit(s) }
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            InkIconView(InkIcon.Clock, Ink, Modifier.size(20.dp))
                            Spacer(Modifier.size(14.dp))
                            Text(s, style = InkType.Body.copy(fontSize = 16.sp), color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            } else {
                item(key = "count") {
                    Text(
                        "${res.size} result${if (res.size != 1) "s" else ""} for “$q”",
                        style = InkType.Label.copy(fontSize = 14.sp),
                        color = Ink,
                        modifier = Modifier.padding(horizontal = 24.dp).padding(top = 20.dp, bottom = 12.dp),
                    )
                }
                if (res.isEmpty()) {
                    item(key = "none") {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(72.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
                                InkIconView(InkIcon.Search, Ink, Modifier.size(30.dp))
                            }
                            Spacer(Modifier.height(14.dp))
                            Text("No notes match “$q”", style = InkType.Title, color = Ink)
                        }
                    }
                } else {
                    items(res, key = { "res-" + it.id }) { n ->
                        ResultCard(
                            note = n,
                            notebook = n.notebookId?.let { booksMap[it] },
                            query = q,
                            onClick = { vm.commit(text); onOpenNote(n.id) },
                            modifier = Modifier.animateItem().padding(horizontal = 24.dp).padding(bottom = 12.dp),
                        )
                    }
                }
                item(key = "bottomSpace") { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SearchField(text: String, onChange: (String) -> Unit, onClear: () -> Unit, onSearch: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.dp, Ink, CircleShape)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkIconView(InkIcon.Search, Ink, Modifier.size(20.dp), strokeWidth = 2.4f)
        Spacer(Modifier.size(12.dp))
        BasicTextField(
            value = text,
            onValueChange = onChange,
            singleLine = true,
            textStyle = InkType.Body.copy(fontSize = 16.sp, color = Ink),
            cursorBrush = SolidColor(Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.weight(1f).focusRequester(focus),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text("Search notes, tags…", style = InkType.Body.copy(fontSize = 16.sp), color = Color(0xFF6B6B64))
                    inner()
                }
            },
        )
        if (text.isNotEmpty()) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onClear)
                    .semantics { contentDescription = "Clear search" },
                contentAlignment = Alignment.Center,
            ) { InkIconView(InkIcon.Close, Ink, Modifier.size(18.dp), strokeWidth = 2.6f) }
        }
    }
}

@Composable
private fun ResultCard(note: Note, notebook: Notebook?, query: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    val body = remember(note.id, note.updatedAt, query) { snippet(stripHtml(note.content, limit = 0), query) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(2.dp, Ink, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(18.dp),
    ) {
        Text(highlight(note.title.ifBlank { "Untitled" }, query), style = InkType.Title, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (body.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(highlight(body, query), style = InkType.Label.copy(fontSize = 14.sp, lineHeight = 20.sp), color = Color(0xFF3A3A36), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatDate(note.updatedAt), style = InkType.Label, color = Color(0xFF3A3A36))
            if (notebook != null) {
                Row(
                    Modifier.clip(CircleShape).background(parseNotebookColor(notebook.color)).padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(notebook.name, style = InkType.Label, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            note.tags.take(2).forEach { t ->
                Text(
                    highlight(t, query),
                    style = InkType.Label,
                    color = Ink,
                    maxLines = 1,
                    modifier = Modifier.border(1.5.dp, Ink.copy(alpha = 0.5f), CircleShape).padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Marks every match of [q] in [text] with a lime background. */
private fun highlight(text: String, q: String): AnnotatedString = buildAnnotatedString {
    val lt = text.lowercase()
    val lq = q.lowercase()
    if (q.isBlank() || lt.length != text.length) {
        append(text)
        return@buildAnnotatedString
    }
    var i = 0
    while (true) {
        val j = lt.indexOf(lq, i)
        if (j < 0) {
            append(text.substring(i))
            break
        }
        append(text.substring(i, j))
        withStyle(SpanStyle(background = Lime, color = Ink)) { append(text.substring(j, j + q.length)) }
        i = j + q.length
    }
}

/** ~130 characters of text around the first match (or the start of the note). */
private fun snippet(text: String, q: String, max: Int = 130): String {
    if (text.isBlank()) return ""
    val idx = if (q.isBlank() || text.length != text.lowercase().length) -1 else text.lowercase().indexOf(q.lowercase())
    val start = if (idx > 40) idx - 40 else 0
    val end = minOf(text.length, start + max)
    return (if (start > 0) "…" else "") + text.substring(start, end) + (if (end < text.length) "…" else "")
}
