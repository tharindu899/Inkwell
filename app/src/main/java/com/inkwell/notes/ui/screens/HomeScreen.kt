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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inkwell.notes.R
import com.inkwell.notes.auth.AuthUser
import com.inkwell.notes.data.model.SortOrder
import com.inkwell.notes.ui.components.BottomNav
import com.inkwell.notes.ui.components.EmptyState
import com.inkwell.notes.ui.components.Fab
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.HomeTopBar
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.components.NavTab
import com.inkwell.notes.ui.components.NoteCard
import com.inkwell.notes.ui.components.onAccent
import com.inkwell.notes.ui.components.tap
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.util.fmtNum
import com.inkwell.notes.util.greetingForNow
import kotlinx.coroutines.delay

/**
 * Port of `src/pages/Home.jsx`.
 *
 * Step 1 (this file): top bar, stats, sort chips, pinned / recent sections, empty state,
 * bottom nav, FAB.  Next steps: multi-select + bulk pin/delete, long-press, pinned drag,
 * Create-New menu, navigation to Editor / Search / Settings / Tags / Notebooks.
 */
@Composable
fun HomeScreen(
    user: AuthUser,
    onSignOut: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val c = Ink.colors
    val state by vm.state.collectAsStateWithLifecycle()

    // useGreeting — re-evaluated every minute
    var greeting by remember { mutableStateOf(greetingForNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            greeting = greetingForNow()
        }
    }

    val notebooksById = remember(state.notebooks) { state.notebooks.associateBy { it.id } }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            HomeTopBar(
                greeting = greeting,
                firstName = user.name.trim().split(" ").firstOrNull().orEmpty().ifEmpty { "User" },
                picture = user.picture,
                onAvatarClick = { /* TODO Profile screen */ },
                // TEMPORARY until Profile is ported: long-press the avatar to sign out.
                onAvatarLongClick = onSignOut,
                onSearch = { /* TODO Search screen */ },
                onSettings = { /* TODO Settings screen */ },
            )

            // .page-content.home-page  (padding: 10 16 62+inset+14)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 62.dp + navInset + 14.dp),
            ) {
                item(key = "stats") {
                    StatsRow(state.stats.notes, state.stats.notebooks, fmtNum(state.stats.words))
                }
                item(key = "sort") {
                    SortRow(state.sortOrder, vm::setSortOrder)
                }

                if (state.notes.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = R.drawable.ic_fa_note_sticky,
                            title = "No notes yet",
                            sub = "Tap the + button to write your first note.",
                        )
                    }
                } else {
                    val pinned = state.pinned
                    val recent = state.recent

                    if (pinned.isNotEmpty()) {
                        item(key = "pinned-h") {
                            SectionHeader(R.drawable.ic_fa_thumbtack, "Pinned notes", pinned.size)
                        }
                        items(pinned, key = { it.id }) { n ->
                            NoteCard(n, n.notebookId?.let(notebooksById::get), onClick = { /* TODO Editor */ })
                        }
                    }

                    item(key = "recent-h") {
                        SectionHeader(
                            R.drawable.ic_fa_clock,
                            "Recent notes",
                            recent.size,
                            modifier = Modifier.padding(top = if (pinned.isNotEmpty()) 12.dp else 0.dp),
                        )
                    }
                    if (recent.isNotEmpty()) {
                        items(recent, key = { it.id }) { n ->
                            NoteCard(n, n.notebookId?.let(notebooksById::get), onClick = { /* TODO Editor */ })
                        }
                    } else {
                        item(key = "recent-empty") { EmptyMini("No recent notes. Unpin a note or create a new one.") }
                    }
                }
            }
        }

        BottomNav(
            active = NavTab.Home,
            onSelect = { /* TODO Tags / Notebooks screens */ },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        Fab(
            onClick = { /* TODO Create-New menu */ },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 18.dp, bottom = 62.dp + 12.dp),
        )
    }
}

/** .stats-row / .stat-card */
@Composable
private fun StatsRow(notes: Int, books: Int, words: String) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        StatCard(notes.toString(), "Notes", Modifier.weight(1f))
        StatCard(books.toString(), "Books", Modifier.weight(1f))
        StatCard(words, "Words", Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(num: String, label: String, modifier: Modifier = Modifier) {
    val c = Ink.colors
    val shape = RoundedCornerShape(13.dp)
    Column(
        modifier
            .clip(shape)
            .background(c.bgCard)
            .border(1.dp, c.border, shape)
            .padding(horizontal = 7.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InkText(num, size = 18.sp, weight = FontWeight.ExtraBold, color = c.accent, lineHeight = 19.sp)
        Spacer(Modifier.height(1.dp))
        InkText(
            label.uppercase(),
            size = 9.sp,
            weight = FontWeight.Bold,
            color = c.text3,
            letterSpacing = 0.45.sp,
            align = TextAlign.Center,
        )
    }
}

/** .sort-row / .sort-btn */
@Composable
private fun SortRow(current: SortOrder, onSelect: (SortOrder) -> Unit) {
    val c = Ink.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkText("SORT BY", size = 10.sp, weight = FontWeight.ExtraBold, color = c.text3, letterSpacing = 0.45.sp)
        SortOrder.entries.forEach { s ->
            val on = s == current
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .height(28.dp)
                    .clip(shape)
                    .background(if (on) c.accent else c.bgCard)
                    .border(1.5.dp, if (on) c.accent else c.border, shape)
                    .tap { onSelect(s) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                InkText(
                    s.label,
                    size = 10.sp,
                    weight = FontWeight.Bold,
                    color = if (on) onAccent() else c.text2,
                )
            }
        }
    }
}

/** .sec-header / .sec-title / .count-badge */
@Composable
private fun SectionHeader(icon: Int, title: String, count: Int, modifier: Modifier = Modifier) {
    val c = Ink.colors
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaIcon(icon, tint = c.accent, size = 11.dp)
        Spacer(Modifier.size(5.dp))
        InkText(
            title.uppercase(),
            size = 11.sp,
            weight = FontWeight.ExtraBold,
            color = c.text3,
            letterSpacing = 0.5.sp,
        )
        Spacer(Modifier.size(5.dp))
        Box(
            Modifier.clip(RoundedCornerShape(20.dp)).background(c.bgElevated).padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            InkText(count.toString(), size = 9.5.sp, weight = FontWeight.ExtraBold, color = c.text2)
        }
    }
}

/** .empty-mini — dashed box */
@Composable
private fun EmptyMini(text: String) {
    val c = Ink.colors
    val shape = RoundedCornerShape(15.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.bgCard)
            .drawBehind {
                drawRoundRect(
                    color = c.border,
                    cornerRadius = CornerRadius(15.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    ),
                )
            }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        InkText(text, size = 12.sp, color = c.text3, align = TextAlign.Center)
    }
}
