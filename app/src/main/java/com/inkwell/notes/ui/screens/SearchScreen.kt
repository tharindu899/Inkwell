package com.inkwell.notes.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.inkwell.notes.model.Note
import com.inkwell.notes.model.Notebook
import com.inkwell.notes.model.SearchMode
import com.inkwell.notes.ui.components.HighlightedText
import com.inkwell.notes.ui.components.InkChip
import com.inkwell.notes.ui.components.InkSearchField
import com.inkwell.notes.ui.components.InkTopBar
import com.inkwell.notes.ui.components.notebookIcon
import com.inkwell.notes.ui.components.pressScale
import com.inkwell.notes.ui.components.staggeredEntrance
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.util.formatRelative
import com.inkwell.notes.util.parseHex
import com.inkwell.notes.util.stripHtml
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    notebooks: List<Notebook>,
    recents: List<String>,
    search: (query: String, mode: SearchMode, notebookId: String?) -> List<Note>,
    notebookOf: (String?) -> Notebook?,
    onSubmit: (String) -> Unit,
    onClearRecents: () -> Unit,
    onOpenNote: (String) -> Unit,
    onBack: () -> Unit,
) {
    val c = Ink.colors
    val bottom = maxOf(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
    )
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(SearchMode.All) }
    var notebookFilter by rememberSaveable { mutableStateOf<String?>(null) }

    // Wait for the slide-in to finish before opening the keyboard.
    LaunchedEffect(Unit) {
        delay(350)
        try { focus.requestFocus() } catch (_: IllegalStateException) { }
    }

    val results: List<Note>? = if (query.isBlank()) null else search(query, mode, notebookFilter)

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        InkTopBar("Search", Modifier.padding(horizontal = 18.dp), onBack = onBack)
        InkSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search notes, tags…",
            onSearch = {
                onSubmit(query)
                focusManager.clearFocus()
            },
            onClear = { query = ""; focus.requestFocus() },
            modifier = Modifier.padding(horizontal = 18.dp),
            focusRequester = focus,
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchMode.entries.forEach { m -> InkChip(m.label, selected = m == mode, onClick = { mode = m }) }
        }
        if (notebooks.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                notebooks.forEach { nb ->
                    NotebookFilterChip(nb, selected = notebookFilter == nb.id) {
                        notebookFilter = if (notebookFilter == nb.id) null else nb.id
                    }
                }
            }
        }
        if (results != null) {
            Text(
                "${results.size} result${if (results.size != 1) "s" else ""} for \"${query.trim()}\"",
                color = c.subText,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }

        Crossfade(targetState = results == null, animationSpec = tween(220), label = "searchBody") { showRecents ->
            LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = bottom + 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (showRecents) {
                    item(key = "recentHeader") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("RECENT", color = c.subText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            if (recents.isNotEmpty()) {
                                Text(
                                    "Clear all",
                                    color = c.text,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.pressScale(onClearRecents).padding(4.dp),
                                )
                            }
                        }
                    }
                    if (recents.isEmpty()) {
                        item(key = "noRecents") {
                            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.History, null, tint = c.subText, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No recent searches", color = c.subText, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    itemsIndexed(recents, key = { _, s -> "r-$s" }) { i, s ->
                        RecentRow(s, { query = s; onSubmit(s); focusManager.clearFocus() }, Modifier.animateItem().staggeredEntrance(i))
                    }
                } else {
                    val list = results.orEmpty()
                    if (list.isEmpty()) {
                        item(key = "noResults") {
                            Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.SentimentDissatisfied, null, tint = c.subText, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No notes match \"${query.trim()}\"", color = c.subText, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    itemsIndexed(list, key = { _, n -> n.id }) { i, n ->
                        SearchResultCard(n, notebookOf(n.notebookId), query, { onOpenNote(n.id) }, Modifier.animateItem().staggeredEntrance(i))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.fillMaxWidth().pressScale(onClick).clip(shape).background(c.surface).border(1.dp, c.border, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.History, null, tint = c.subText, modifier = Modifier.size(18.dp))
        Text(text, color = c.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
        Icon(Icons.Rounded.NorthWest, "Use this search", tint = c.subText, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun NotebookFilterChip(nb: Notebook, selected: Boolean, onClick: () -> Unit) {
    val c = Ink.colors
    val tint = parseHex(nb.color)
    val bg by animateColorAsState(if (selected) tint.copy(alpha = 0.22f) else c.surface, tween(220), label = "nbChipBg")
    val line by animateColorAsState(if (selected) tint else c.border, tween(220), label = "nbChipLine")
    Row(
        Modifier.pressScale(onClick).clip(CircleShape).background(bg).border(1.dp, line, CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(notebookIcon(nb.icon), null, tint = if (selected) tint else c.subText, modifier = Modifier.size(14.dp))
        Text(nb.name, color = c.text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SearchResultCard(note: Note, notebook: Notebook?, query: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth().pressScale(onClick).clip(shape).background(c.surface).border(1.dp, c.border, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HighlightedText(note.title.ifBlank { "Untitled" }, query, c.text, MaterialTheme.typography.titleMedium, maxLines = 1)
        HighlightedText(note.content.stripHtml().take(120), query, c.subText, MaterialTheme.typography.bodyMedium, maxLines = 2)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(formatRelative(note.updatedAt), color = c.subText, style = MaterialTheme.typography.labelMedium)
            if (notebook != null) {
                val tint = parseHex(notebook.color)
                Row(
                    Modifier.clip(CircleShape).background(tint.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
                    Text(notebook.name, color = c.text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
            note.tags.take(2).forEach { tag ->
                HighlightedText(
                    "#$tag", query, c.subText, MaterialTheme.typography.labelMedium, maxLines = 1,
                )
            }
        }
    }
}
