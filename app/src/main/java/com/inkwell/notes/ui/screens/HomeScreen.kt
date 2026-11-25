package com.inkwell.notes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.inkwell.notes.ui.components.DeleteConfirmModal
import com.inkwell.notes.ui.components.DeleteRed
import com.inkwell.notes.ui.components.EmptyState
import com.inkwell.notes.ui.components.Fab
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.HomeTopBar
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.components.InkToast
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
 * Step 1: top bar, stats, sort chips, pinned / recent sections, empty state, bottom nav, FAB.
 * Step 2: multi-select (Select chip or long-press), bulk Pin/Unpin, bulk Delete
 * with confirm modal + Undo toast.
 * Step 3 (this version): pinned drag-reorder via the grip handle.
 * Next steps: Create-New menu, navigation to Editor / Search / Settings /
 * Tags / Notebooks.
 */
@Composable
fun HomeScreen(
    user: AuthUser,
    onSignOut: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val c = Ink.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    // useGreeting — re-evaluated every minute
    var greeting by remember { mutableStateOf(greetingForNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            greeting = greetingForNow()
        }
    }

    // Back closes the delete modal first, then leaves select mode.
    BackHandler(enabled = state.selectMode) {
        if (state.deleteConfirmOpen) vm.dismissDelete() else vm.cancelSelectMode()
    }

    // ── pinned drag-reorder (web `startPinnedDrag`) ──
    // The pointer is tracked in list coordinates; whichever other pinned card is under it
    // swaps places with the dragged one, live.
    val listState = rememberLazyListState()
    val density = LocalDensity.current.density
    val pinnedIds by rememberUpdatedState(state.pinned.map { it.id }.toSet())
    var dragId by remember { mutableStateOf<String?>(null) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    // After a swap the list needs one layout pass before the next hit-test is valid.
    var swapFromOffset by remember { mutableStateOf<Int?>(null) }

    fun itemInfo(id: String) = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }

    fun onPinDragStart(id: String, touch: androidx.compose.ui.geometry.Offset) {
        val info = itemInfo(id) ?: return
        dragId = id
        swapFromOffset = null
        pointerY = info.offset + 9f * density + touch.y // handle sits 9dp below the card top
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun onPinDrag(dy: Float) {
        val active = dragId ?: return
        pointerY += dy
        val me = itemInfo(active) ?: return
        val waiting = swapFromOffset
        if (waiting != null) {
            if (me.offset == waiting) return // layout hasn't caught up with the last swap yet
            swapFromOffset = null
        }
        val over = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            val k = it.key
            k is String && k != active && k in pinnedIds && pointerY >= it.offset && pointerY < it.offset + it.size
        }?.key as? String ?: return
        if (vm.movePinned(active, over)) {
            swapFromOffset = me.offset
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun onPinDragEnd() {
        dragId = null
        swapFromOffset = null
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
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 62.dp + navInset + 14.dp),
            ) {
                item(key = "stats") {
                    StatsRow(state.stats.notes, state.stats.notebooks, fmtNum(state.stats.words))
                }

                if (state.selectMode) {
                    item(key = "select-bar") {
                        SelectToolbar(
                            count = state.selected.size,
                            anyPinned = state.anySelectedPinned,
                            onCancel = vm::cancelSelectMode,
                            onPin = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                vm.bulkPin()
                            },
                            onDelete = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                vm.requestDelete()
                            },
                        )
                    }
                } else {
                    item(key = "sort") {
                        SortRow(
                            current = state.sortOrder,
                            onSelect = vm::setSortOrder,
                            onStartSelect = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                vm.startSelectMode()
                            },
                        )
                    }
                }

                if (state.notes.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = R.drawable.ic_fa_note_sticky,
                            title = "No notes yet",
                            sub = "Tap the + button to write your first note.",
                        )
                    }
                } else if (state.selectMode) {
                    // select mode: one flat list (no Pinned / Recent headers), like the web page
                    items(state.notes, key = { it.id }) { n ->
                        NoteCard(
                            n,
                            n.notebookId?.let(notebooksById::get),
                            onClick = { vm.toggleSelect(n.id) },
                            selectMode = true,
                            selected = n.id in state.selected,
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.toggleSelect(n.id)
                            },
                        )
                    }
                } else {
                    val pinned = state.pinned
                    val recent = state.recent
                    val enterSelect: (String) -> Unit = { id ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.enterSelectMode(id)
                    }

                    if (pinned.isNotEmpty()) {
                        item(key = "pinned-h") {
                            SectionHeader(R.drawable.ic_fa_thumbtack, "Pinned notes", pinned.size)
                        }
                        items(pinned, key = { it.id }) { n ->
                            NoteCard(
                                n,
                                n.notebookId?.let(notebooksById::get),
                                onClick = { /* TODO Editor */ },
                                onLongClick = { enterSelect(n.id) },
                                draggablePinned = true,
                                dragging = dragId == n.id,
                                onDragStart = { onPinDragStart(n.id, it) },
                                onDrag = ::onPinDrag,
                                onDragEnd = ::onPinDragEnd,
                            )
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
                            NoteCard(
                                n,
                                n.notebookId?.let(notebooksById::get),
                                onClick = { /* TODO Editor */ },
                                onLongClick = { enterSelect(n.id) },
                            )
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

        // {!selectMode && <Fab />}
        if (!state.selectMode) {
            Fab(
                onClick = { /* TODO Create-New menu */ },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(end = 18.dp, bottom = 62.dp + 12.dp),
            )
        }

        // .toast — sits 12dp above the bottom nav
        InkToast(
            toast = state.toast,
            onAction = vm::onToastAction,
            onDismiss = vm::dismissToast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 17.dp, end = 17.dp, bottom = 62.dp + navInset + 12.dp),
        )

        if (state.deleteConfirmOpen) {
            DeleteConfirmModal(
                count = state.selected.size,
                onCancel = vm::dismissDelete,
                onConfirm = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.confirmDelete()
                },
            )
        }
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

/** .sort-row / .sort-btn (+ .select-now-btn) */
@Composable
private fun SortRow(current: SortOrder, onSelect: (SortOrder) -> Unit, onStartSelect: () -> Unit) {
    val c = Ink.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkText("SORT BY", size = 10.sp, weight = FontWeight.ExtraBold, color = c.text3, letterSpacing = 0.45.sp)

        // Select — enters multi-select with nothing picked yet
        Row(
            Modifier
                .height(28.dp)
                .clip(shape)
                .background(c.bgCard)
                .border(1.5.dp, c.border, shape)
                .tap(onStartSelect)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FaIcon(R.drawable.ic_fa_circle_check, tint = c.text2, size = 11.dp)
            Spacer(Modifier.size(6.dp))
            InkText("Select", size = 10.sp, weight = FontWeight.Bold, color = c.text2)
        }

        SortOrder.entries.forEach { s ->
            val on = s == current
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

/** Multi-select toolbar: Cancel · "N selected" · Pin/Unpin · Delete. */
@Composable
private fun SelectToolbar(
    count: Int,
    anyPinned: Boolean,
    onCancel: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Ink.colors
    val enabled = count > 0
    // the thumbtack turns 45° when the button says "Unpin"
    val pinRotation by animateFloatAsState(if (anyPinned) 45f else 0f, tween(200), label = "pinRotation")

    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarButton(
            label = "Cancel",
            icon = R.drawable.ic_fa_xmark,
            textColor = c.text1,
            background = null,
            border = c.border,
            weight = FontWeight.SemiBold,
            enabled = true,
            onClick = onCancel,
        )
        InkText(
            if (count == 0) "Tap notes to select" else "$count selected",
            size = 13.sp,
            weight = FontWeight.SemiBold,
            color = c.accent,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        ToolbarButton(
            label = if (anyPinned) "Unpin" else "Pin",
            icon = R.drawable.ic_fa_thumbtack,
            iconRotation = pinRotation,
            textColor = onAccent(),
            background = c.accent,
            border = null,
            weight = FontWeight.Bold,
            enabled = enabled,
            onClick = onPin,
        )
        ToolbarButton(
            label = "Delete",
            icon = R.drawable.ic_fa_trash_can,
            textColor = Color.White,
            background = DeleteRed,
            border = null,
            weight = FontWeight.Bold,
            enabled = enabled,
            onClick = onDelete,
        )
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    icon: Int,
    textColor: Color,
    background: Color?,
    border: Color?,
    weight: FontWeight,
    enabled: Boolean,
    onClick: () -> Unit,
    iconRotation: Float = 0f,
) {
    val shape = RoundedCornerShape(Ink.radius)
    var m: Modifier = Modifier.alpha(if (enabled) 1f else 0.4f).clip(shape)
    if (background != null) m = m.background(background)
    if (border != null) m = m.border(1.dp, border, shape)
    if (enabled) m = m.tap(onClick)
    Row(m.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        FaIcon(icon, tint = textColor, size = 13.dp, modifier = Modifier.rotate(iconRotation))
        Spacer(Modifier.size(5.dp))
        InkText(label, size = 13.sp, weight = weight, color = textColor)
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
