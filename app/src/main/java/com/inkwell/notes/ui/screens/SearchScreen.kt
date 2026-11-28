package com.inkwell.notes.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.R
import com.inkwell.notes.data.NotesStore
import com.inkwell.notes.data.model.Note
import com.inkwell.notes.data.model.SearchMode
import com.inkwell.notes.ui.components.BottomNav
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.components.InnerTopBar
import com.inkwell.notes.ui.components.NavTab
import com.inkwell.notes.ui.components.NoteCard
import com.inkwell.notes.ui.components.onAccent
import com.inkwell.notes.ui.components.parseColor
import com.inkwell.notes.ui.components.tap
import com.inkwell.notes.ui.theme.Ink

/**
 * Port of `src/pages/Search.jsx` (with the final `.search-*` / `.f-chip` / `.recent-*` CSS).
 *
 * Search bar (pill) → mode chips (All / Title / Content / Tags) → notebook chips → either
 * the "Recent" list (no query) or the result cards with matches highlighted.
 * Enter / the keyboard's search key saves the query to the recent list (max 8).
 *
 * Reads straight from [NotesStore] like the web page reads storage.js — a fresh screen is
 * created every time Search is opened, so the notes are always current.
 *
 * Tapping a result opens it in the Editor.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavTab: (NavTab) -> Unit,
    onOpenNote: (Note) -> Unit = {},
) {
    val c = Ink.colors
    val context = LocalContext.current
    val store = remember { NotesStore(context) }
    val notebooks = remember { store.getNotebooks() }
    val notebooksById = remember(notebooks) { notebooks.associateBy { it.id } }

    var query by rememberSaveable { mutableStateOf("") }
    var modeKey by rememberSaveable { mutableStateOf(SearchMode.All.name) }
    var notebookFilter by rememberSaveable { mutableStateOf<String?>(null) } // null = all notebooks
    var recents by remember { mutableStateOf(store.getSearches()) }

    val mode = SearchMode.entries.firstOrNull { it.name == modeKey } ?: SearchMode.All

    // `results` — null means "show recent searches" (empty query).
    val results: List<Note>? = remember(query, mode, notebookFilter) {
        if (query.isBlank()) null
        else store.searchNotes(query, mode).let { res ->
            notebookFilter?.let { id -> res.filter { it.notebookId == id } } ?: res
        }
    }

    fun handleSearch(q: String) {
        if (q.isBlank()) return
        store.addSearch(q)
        recents = store.getSearches()
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        Column(Modifier.fillMaxSize()) {
            InnerTopBar(title = "Search", onBack = onBack)

            // .search-wrap + .search-bar
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                onSearch = { handleSearch(query) },
                onClear = {
                    query = ""
                    runCatching { focusRequester.requestFocus() }
                },
                focusRequester = focusRequester,
            )

            // .filter-row — mode chips
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchMode.entries.forEach { m ->
                    FilterChip(label = m.label, on = mode == m, onClick = { modeKey = m.name })
                }
            }

            // .filter-row — notebook chips (gap 6, no top padding)
            if (notebooks.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    notebooks.forEach { nb ->
                        val on = notebookFilter == nb.id
                        FilterChip(
                            label = nb.name,
                            on = on,
                            icon = R.drawable.ic_fa_book_open,
                            tint = parseColor(nb.color, c.accent),
                            onClick = { notebookFilter = if (on) null else nb.id },
                        )
                    }
                }
            }

            // .search-results-count
            if (results != null) {
                SmallLabel(
                    "${results.size} result${if (results.size != 1) "s" else ""} for \"$query\"".uppercase(),
                )
            }

            // .search-page-content
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = Ink.navHeight + navInset + 18.dp),
            ) {
                if (results == null) {
                    // Recent searches
                    item(key = "recent-h") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            InkText(
                                "RECENT",
                                size = 12.sp,
                                weight = FontWeight.ExtraBold,
                                color = c.text3,
                                letterSpacing = 0.6.sp,
                            )
                            InkText(
                                "Clear all",
                                size = 13.sp,
                                weight = FontWeight.ExtraBold,
                                color = c.accent,
                                modifier = Modifier.tap {
                                    store.clearSearches()
                                    recents = emptyList()
                                },
                            )
                        }
                    }
                    if (recents.isEmpty()) {
                        item(key = "recent-empty") {
                            NoResults(
                                icon = R.drawable.ic_fa_clock_rotate_left,
                                text = buildAnnotatedString { append("No recent searches") },
                                verticalPadding = 30.dp,
                            )
                        }
                    } else {
                        items(recents, key = { "r-$it" }) { s ->
                            RecentItem(
                                text = s,
                                onClick = {
                                    query = s
                                    handleSearch(s)
                                },
                            )
                        }
                    }
                } else if (results.isEmpty()) {
                    item(key = "no-results") {
                        NoResults(
                            icon = R.drawable.ic_fa_face_frown_open,
                            text = buildAnnotatedString {
                                append("No notes match \"")
                                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(query) }
                                append("\"")
                            },
                        )
                    }
                } else {
                    items(results, key = { it.id }) { n ->
                        Box(Modifier.padding(horizontal = 18.dp)) {
                            NoteCard(
                                note = n,
                                notebook = n.notebookId?.let(notebooksById::get),
                                onClick = { onOpenNote(n) },
                                highlight = query,
                            )
                        }
                    }
                }
            }
        }

        BottomNav(
            active = null,
            onSelect = onNavTab,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** .search-bar — pill with magnifier, input and a clear (✕) button. Border turns accent on focus. */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
) {
    val c = Ink.colors
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(c.bgCard)
                .border(1.5.dp, if (focused) c.accent else c.border, RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FaIcon(R.drawable.ic_fa_magnifying_glass, tint = c.text3, size = 15.dp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    InkText("Search notes, tags…", size = 14.sp, color = c.text3, maxLines = 1)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = c.text1, fontSize = 14.sp, fontFamily = Ink.sans),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused },
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(20.dp).tap(onClear), contentAlignment = Alignment.Center) {
                    FaIcon(R.drawable.ic_fa_xmark, tint = c.text3, size = 13.dp)
                }
            }
        }
    }
}

/**
 * .f-chip — pill. Selected: solid accent with white/dark text (final CSS).
 * Notebook chips pass their own [tint]: selected = tinted border + text + 13% fill.
 */
@Composable
private fun FilterChip(
    label: String,
    on: Boolean,
    onClick: () -> Unit,
    icon: Int? = null,
    tint: Color? = null,
) {
    val c = Ink.colors
    val shape = RoundedCornerShape(50)
    val bg: Color
    val stroke: Color
    val fg: Color
    when {
        on && tint != null -> { bg = tint.copy(alpha = 0.133f); stroke = tint; fg = tint }
        on -> { bg = c.accent; stroke = c.accent; fg = onAccent() }
        else -> { bg = c.bgCard; stroke = c.border; fg = c.text2 }
    }
    Row(
        Modifier
            .clip(shape)
            .background(bg)
            .border(1.5.dp, stroke, shape)
            .tap(onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            FaIcon(icon, tint = fg, size = 9.dp)
            Spacer(Modifier.width(4.dp))
        }
        InkText(label, size = 12.sp, weight = FontWeight.Bold, color = fg, maxLines = 1)
    }
}

/** .search-results-count */
@Composable
private fun SmallLabel(text: String) {
    InkText(
        text,
        size = 12.sp,
        weight = FontWeight.ExtraBold,
        color = Ink.colors.text3,
        letterSpacing = 0.6.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
    )
}

/** .recent-search-item — clock icon, text, and a "use this" arrow; 1px divider below. */
@Composable
private fun RecentItem(text: String, onClick: () -> Unit) {
    val c = Ink.colors
    Row(
        Modifier
            .fillMaxWidth()
            .tap(onClick)
            .drawBehind { drawRect(c.border, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx())) }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(16.dp), contentAlignment = Alignment.CenterStart) {
            FaIcon(R.drawable.ic_fa_clock_rotate_left, tint = c.text3, size = 14.dp)
        }
        Spacer(Modifier.width(12.dp))
        InkText(text, size = 14.sp, weight = FontWeight.Bold, color = c.text2, maxLines = 1, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        // fa-turn-up rotated 90° (points right)
        FaIcon(R.drawable.ic_fa_turn_up, tint = c.text3, size = 12.dp, modifier = Modifier.graphicsLayer { rotationZ = 90f })
    }
}

/** .no-results */
@Composable
private fun NoResults(icon: Int, text: androidx.compose.ui.text.AnnotatedString, verticalPadding: androidx.compose.ui.unit.Dp = 40.dp) {
    val c = Ink.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FaIcon(icon, tint = c.text3, size = 44.dp)
        Spacer(Modifier.height(10.dp))
        InkText(text, size = 14.sp, weight = FontWeight.Bold, color = c.text3, align = TextAlign.Center)
    }
}
