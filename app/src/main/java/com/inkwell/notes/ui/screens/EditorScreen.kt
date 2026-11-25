package com.inkwell.notes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.inkwell.notes.R
import com.inkwell.notes.data.AppPrefs
import com.inkwell.notes.editor.RichStyle
import com.inkwell.notes.editor.RichTransformation
import com.inkwell.notes.ui.Route
import com.inkwell.notes.ui.components.DeleteConfirmModal
import com.inkwell.notes.ui.components.DeleteRed
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.components.InkToast
import com.inkwell.notes.ui.components.notebookColor
import com.inkwell.notes.ui.components.onAccent
import com.inkwell.notes.ui.components.tap
import com.inkwell.notes.ui.theme.Ink
import kotlinx.coroutines.delay

/**
 * Port of `pages/Editor.jsx` — built part by part.
 *
 * Part 1: shell + load/save.
 *   top bar (back · title · reading mode · pin · delete · Save), title field, body field,
 *   tags / notebook rows (display only), word / char count + saved indicator,
 *   2 s auto-save, flush on pause / leave, delete confirm.
 * Part 2: rich body (RichDoc) + formatting toolbar (marks, headings, lists, quote, align, indent, undo/redo).
 * Coming next: checklists + markdown shortcuts, tags & notebook pickers, code blocks / tables /
 * checklists, images + drawing, markdown source mode, export.
 */
@Composable
fun EditorScreen(route: Route.Editor, onBack: () -> Unit) {
    val context = LocalContext.current
    val c = Ink.colors
    val ctl = remember(route) { EditorController(context, route) }

    // Note vanished (deleted elsewhere / stale id) → leave, like `navigate('/', { replace: true })`.
    LaunchedEffect(ctl.missing) { if (ctl.missing) onBack() }
    if (ctl.missing) return

    // Save flush on background / unmount (web: pagehide, visibilitychange, Capacitor `pause`, cleanup).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, ctl) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) ctl.flush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ctl.flush()
        }
    }

    // Auto-save: 2 s after the last edit; never while Reading mode is on.
    LaunchedEffect(ctl.edits, ctl.readMode) {
        if (ctl.edits > 0 && ctl.autosaveEnabled && !ctl.readMode) {
            delay(2_000L)
            ctl.save()
        }
    }

    BackHandler(enabled = ctl.deleteConfirmOpen) { ctl.dismissDelete() }

    val titleFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }
    // New note → cursor starts in the title (web: `if (!paramId) titleRef.current?.focus()`).
    LaunchedEffect(ctl) { if (ctl.isNew) runCatching { titleFocus.requestFocus() } }

    // Settings → Editor: text size + serif body.
    val fontSp = remember { AppPrefs.getFontSize(context).sp }
    val serifBody = remember { AppPrefs.getPref(context, "serifBody", false) }

    val richStyle = remember(c, fontSp, serifBody) {
        RichStyle(
            fontSizeSp = fontSp.toFloat(),
            lineHeightFactor = 1.72f,
            textColor = c.text1,
            quoteColor = c.text2,
            accent = c.accent,
            highlightBg = Color(0xFFFFE49A).copy(alpha = 0.45f),
            serif = Ink.serif,
        )
    }

    // Keep the bottom panel above the keyboard / navigation bar.
    val bottomInsets = WindowInsets.navigationBars.union(WindowInsets.ime)

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(bottomInsets)) {
            EditorTopBar(
                title = ctl.title.text.ifBlank { "New Note" },
                readMode = ctl.readMode,
                pinned = ctl.note.pinned,
                onBack = { ctl.flush(); onBack() },
                onReadMode = ctl::toggleReadMode,
                onPin = ctl::togglePin,
                onDelete = ctl::requestDelete,
                onSave = { ctl.save(); onBack() },
            )

            // ── formatting toolbar (hidden while reading, and for notes that are still read-only) ──
            if (ctl.bodyEditable && !ctl.readMode) {
                EditorToolbar(
                    state = ctl.toolbarState,
                    onUndo = ctl::undo,
                    onRedo = ctl::redo,
                    onMark = ctl::toggleMark,
                    onBlock = ctl::setBlock,
                    onAlign = ctl::setAlign,
                    onIndent = ctl::changeIndent,
                    onClear = ctl::clearFormat,
                )
            }

            // ── title ──
            BasicTextField(
                value = ctl.title,
                onValueChange = { v ->
                    if ('\n' in v.text) runCatching { bodyFocus.requestFocus() } // Enter → body
                    ctl.onTitleChange(v)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus)
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
                readOnly = ctl.readMode,
                textStyle = TextStyle(
                    color = c.text1,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Ink.serif,
                ),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { inner ->
                    Box {
                        if (ctl.title.text.isEmpty()) {
                            InkText(
                                "Note title…",
                                color = c.text3,
                                size = 22.sp,
                                weight = FontWeight.Bold,
                                family = Ink.serif,
                            )
                        }
                        inner()
                    }
                },
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))

            // ── body (scrolls; tapping the empty area below the text focuses it) ──
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .tap { if (ctl.bodyEditable && !ctl.readMode) runCatching { bodyFocus.requestFocus() } },
            ) {
                if (!ctl.bodyEditable) RichNoteBanner()

                BasicTextField(
                    value = ctl.body,
                    onValueChange = ctl::onBodyChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp)
                        .focusRequester(bodyFocus)
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
                    readOnly = ctl.readMode || !ctl.bodyEditable,
                    textStyle = TextStyle(
                        color = c.text1,
                        fontSize = fontSp.sp,
                        lineHeight = (fontSp * 1.72f).sp, // .editor-body line-height 1.72
                        fontFamily = if (serifBody) Ink.serif else Ink.sans,
                    ),
                    cursorBrush = SolidColor(c.accent),
                    visualTransformation = if (ctl.bodyEditable) {
                        remember(ctl.doc, richStyle) { RichTransformation(ctl.doc, richStyle) }
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    decorationBox = { inner ->
                        Box {
                            if (ctl.body.text.isEmpty() && ctl.bodyEditable) {
                                InkText(
                                    "Start writing…",
                                    color = c.text3,
                                    size = fontSp.sp,
                                    lineHeight = (fontSp * 1.72f).sp,
                                    family = if (serifBody) Ink.serif else Ink.sans,
                                )
                            }
                            inner()
                        }
                    },
                )
            }

            // ── bottom panel: tags, notebook, counts ──
            TagsRow(tags = ctl.note.tags)
            NotebookRow(name = ctl.notebook?.name, colorHex = ctl.notebook?.color)
            EditorFooter(words = ctl.wordCount, chars = ctl.charCount, saved = ctl.saved)
        }

        InkToast(
            toast = ctl.toast,
            onAction = {},
            onDismiss = ctl::dismissToast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(bottomInsets)
                .padding(bottom = 140.dp),
        )

        if (ctl.deleteConfirmOpen) {
            DeleteConfirmModal(
                count = 1,
                title = "Delete this note?",
                message = "This note will be deleted permanently.",
                onCancel = ctl::dismissDelete,
                onConfirm = {
                    ctl.confirmDelete()
                    onBack()
                },
            )
        }
    }
}

// ─────────────────────────────────────────────
// Top bar — `.top-bar.editor-top`
// ─────────────────────────────────────────────

@Composable
private fun EditorTopBar(
    title: String,
    readMode: Boolean,
    pinned: Boolean,
    onBack: () -> Unit,
    onReadMode: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
) {
    val c = Ink.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(c.bgCard).tap(onBack),
            contentAlignment = Alignment.Center,
        ) {
            FaIcon(R.drawable.ic_fa_arrow_left, tint = c.text1, size = 14.dp)
        }

        InkText(
            title,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            size = 14.sp,
            weight = FontWeight.Bold,
            family = Ink.serif,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        TopIconButton(R.drawable.ic_fa_book_open, on = readMode, onClick = onReadMode)
        TopIconButton(R.drawable.ic_fa_thumbtack, on = pinned, onClick = onPin)
        TopIconButton(R.drawable.ic_fa_trash_can, tint = DeleteRed, onClick = onDelete)

        val saveShape = RoundedCornerShape(8.dp)
        Box(
            Modifier.clip(saveShape).background(c.accent).tap(onSave).padding(horizontal = 11.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            InkText("Save", size = 12.sp, weight = FontWeight.Bold, color = onAccent())
        }
    }
}

/** `.icon-btn` (+ `.act` / `.on`): 34dp, radius 9, accent tint when active. */
@Composable
private fun TopIconButton(
    @DrawableRes icon: Int,
    on: Boolean = false,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val c = Ink.colors
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier
            .size(34.dp)
            .clip(shape)
            .background(if (on) c.accentDim else c.bgCard)
            .border(1.dp, if (on) c.accent else c.border, shape)
            .tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        FaIcon(icon, tint = tint ?: if (on) c.accent else c.text2, size = 14.dp)
    }
}

// ─────────────────────────────────────────────
// Body helpers
// ─────────────────────────────────────────────

/** Shown above the body of notes that use rich formatting the Part 1 editor can't write back yet. */
@Composable
private fun RichNoteBanner() {
    val c = Ink.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(c.accentDim)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FaIcon(R.drawable.ic_fa_circle_info, tint = c.accent, size = 14.dp)
        InkText(
            "This note has tables, code, links, images or a checklist. Those blocks arrive in the next " +
                "editor parts — until then the body stays read-only and untouched (title, pin and delete work).",
            size = 12.sp,
            color = c.text2,
            lineHeight = 17.sp,
        )
    }
}

// ─────────────────────────────────────────────
// Bottom panel — `.editor-bottom-panel`
// ─────────────────────────────────────────────

@Composable
private fun PanelRow(content: @Composable () -> Unit) {
    val c = Ink.colors
    Column(Modifier.fillMaxWidth().background(c.bg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun TagsRow(tags: List<String>) {
    val c = Ink.colors
    PanelRow {
        FaIcon(R.drawable.ic_fa_tag, tint = c.text3, size = 14.dp)
        if (tags.isEmpty()) {
            InkText("No tags selected", size = 13.sp, color = c.text3)
        } else {
            val more = if (tags.size > 2) " +${tags.size - 2} more" else ""
            InkText(
                "In ${tags.take(2).joinToString(", ")}$more",
                size = 13.sp,
                color = c.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NotebookRow(name: String?, colorHex: String?) {
    val c = Ink.colors
    PanelRow {
        FaIcon(R.drawable.ic_fa_book_open, tint = c.text3, size = 14.dp)
        if (name == null) {
            InkText("No notebook selected", size = 13.sp, color = c.text3)
        } else {
            InkText("In ", size = 13.sp, color = c.text2)
            InkText(
                name,
                size = 13.sp,
                weight = FontWeight.SemiBold,
                color = notebookColor(colorHex.orEmpty(), c.accent),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** `.editor-footer`: word / char count on the left, `.autosave-dot` on the right. */
@Composable
private fun EditorFooter(words: Int, chars: Int, saved: Boolean) {
    val c = Ink.colors
    Column(Modifier.fillMaxWidth().background(c.bgCard)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                InkText("$words ${if (words == 1) "word" else "words"}", size = 11.sp, color = c.text3)
                InkText("$chars ${if (chars == 1) "char" else "chars"}", size = 11.sp, color = c.text3)
            }
            InkText(
                if (saved) "● Saved" else "● Saving…",
                size = 11.sp,
                weight = FontWeight.Bold,
                color = if (saved) c.success else c.accent,
            )
        }
    }
}
