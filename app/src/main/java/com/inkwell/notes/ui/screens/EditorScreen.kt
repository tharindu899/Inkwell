package com.inkwell.notes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontFamily
import com.inkwell.notes.editor.BlockType
import com.inkwell.notes.editor.TableData
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
import com.inkwell.notes.editor.Mark
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
 * Part 3: checklists (tap a box), nested + collapsible lists (tap a chevron), markdown shortcuts + toggle,
 *   Enter / Backspace list behaviour, basic code blocks.
 * Part 4: tags row → "Manage tags" modal, notebook row → "Move to Notebook" sheet (+ inline create).
 *   Both rows are tappable; they are hidden in Reading mode like the web (`.read-mode .tags-row`).
 * Part 5: inline code, code-block bar (Copy / Bottom / Delete above the toolbar), tables (drawn in the body, edited in a
 *   sheet; wide tables scroll sideways), link popover (tap a link in Reading mode to open it), divider, date/time, paste.
 * Coming next: images + drawing, markdown source mode, export.
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
    BackHandler(enabled = ctl.linkOpen) { ctl.closeLink() }
    BackHandler(enabled = ctl.nbCreateOpen) { ctl.closeCreateNotebook() }
    BackHandler(enabled = ctl.tagManageOpen) { ctl.closeTagManager() }
    BackHandler(enabled = ctl.nbPickerOpen) { ctl.closeNotebookPicker() }

    val titleFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }
    // New note → cursor starts in the title (web: `if (!paramId) titleRef.current?.focus()`).
    LaunchedEffect(ctl) { if (ctl.isNew) runCatching { titleFocus.requestFocus() } }

    // Settings → Editor: text size + serif body.
    val fontSp = remember { AppPrefs.getFontSize(context).sp }
    val serifBody = remember { AppPrefs.getPref(context, "serifBody", false) }

    val fontScale = LocalDensity.current.fontScale
    val richStyle = remember(c, fontSp, serifBody, fontScale) {
        RichStyle(
            fontSizeSp = fontSp.toFloat(),
            lineHeightFactor = 1.72f,
            textColor = c.text1,
            quoteColor = c.text2,
            accent = c.accent,
            highlightBg = Color(0xFFFFE49A).copy(alpha = 0.45f),
            serif = Ink.serif,
            doneColor = c.text3,
            codeBg = c.text3.copy(alpha = 0.16f),
            linkColor = c.accent,
            inlineCodeBg = c.text3.copy(alpha = 0.2f),
            fontScale = fontScale,
        )
    }

    // Keep the bottom panel above the keyboard / navigation bar.
    val bottomInsets = WindowInsets.navigationBars.union(WindowInsets.ime)
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    val toolbarScroll = rememberScrollState()
    val bodyScroll = rememberScrollState()
    var viewportH by remember { mutableIntStateOf(0) }
    var layoutRes by remember { mutableStateOf<TextLayoutResult?>(null) }
    val transformation = if (ctl.bodyEditable) {
        remember(ctl.doc, richStyle) { RichTransformation(ctl.doc, richStyle) }
    } else null
    val transformationNow by rememberUpdatedState(transformation)
    val tapPadPx = with(LocalDensity.current) { 8.dp.toPx() }
    val uriHandler = LocalUriHandler.current
    val padStartPx = with(LocalDensity.current) { 20.dp.roundToPx() }
    val padTopPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val tickColor = onAccent()
    val fontPx = with(LocalDensity.current) { fontSp.sp.toPx() }

    // Keep the caret (plus a margin, so sub/superscript lines are never cut) inside the visible body.
    LaunchedEffect(ctl.body.selection, ctl.body.text, layoutRes, viewportH) {
        val lay = layoutRes ?: return@LaunchedEffect
        if (viewportH == 0 || !ctl.bodyEditable) return@LaunchedEffect
        val len = lay.layoutInput.text.length
        val orig = ctl.body.selection.end.coerceIn(0, ctl.body.text.length)
        val t = (transformation?.offsetMapping?.originalToTransformed(orig) ?: orig).coerceIn(0, len)
        val r = lay.getCursorRect(t)
        val pad = with(density) { 8.dp.toPx() }
        val margin = with(density) { 32.dp.toPx() }
        val top = pad + r.top
        val bottom = pad + r.bottom
        val cur = bodyScroll.value
        when {
            bottom + margin > cur + viewportH -> bodyScroll.animateScrollTo((bottom + margin - viewportH).toInt().coerceAtLeast(0))
            top - margin < cur -> bodyScroll.animateScrollTo((top - margin).toInt().coerceAtLeast(0))
        }
    }

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
                    .onSizeChanged { viewportH = it.height }
                    .verticalScroll(bodyScroll)
                    .tap { if (ctl.bodyEditable && !ctl.readMode) runCatching { bodyFocus.requestFocus() } },
            ) {
                if (!ctl.bodyEditable) RichNoteBanner()

                Box(Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = ctl.body,
                    onValueChange = ctl::onBodyChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp)
                        .focusRequester(bodyFocus)
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
                        // Checkbox + chevron shapes are painted into the blank slots RichLayout reserves.
                        .drawBehind {
                            val lay = layoutRes
                            val tr = transformationNow
                            if (lay != null && tr != null) {
                                drawListControls(lay, tr, fontPx, c.accent, c.text3, tickColor, c.borderHi, c.text3.copy(alpha = 0.12f), c.border)
                            }
                        }
                        // Tap on a checklist box / collapse chevron: handled here (before the text field sees the
                        // tap), so it toggles without moving the caret or opening the keyboard.
                        .pointerInput(ctl) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                val lay = layoutRes
                                val tr = transformationNow
                                if (lay == null || tr == null) return@awaitEachGesture
                                val hit = hitListControl(lay, tr, down.position, tapPadPx) ?: return@awaitEachGesture
                                val slop = viewConfiguration.touchSlop
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                    if (!ch.pressed) {
                                        ch.consume()
                                        if (hit.chevron) ctl.toggleCollapse(hit.para) else ctl.toggleChecked(hit.para)
                                        return@awaitEachGesture
                                    }
                                    if ((ch.position - down.position).getDistance() > slop) return@awaitEachGesture
                                }
                            }
                        }
                        // Reading mode: a tap on a link opens it (before the text field sees the tap).
                        .pointerInput(ctl) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                if (!ctl.readMode) return@awaitEachGesture
                                val lay = layoutRes
                                val tr = transformationNow
                                if (lay == null || tr == null || lay.layoutInput.text.length != tr.layout.length) return@awaitEachGesture
                                val pos = down.position
                                if (pos.y < 0f || pos.y > lay.size.height.toFloat()) return@awaitEachGesture
                                val line = lay.getLineForVerticalPosition(pos.y)
                                if (pos.x > lay.getLineRight(line) + tapPadPx) return@awaitEachGesture
                                val t = lay.getOffsetForPosition(pos)
                                val o = tr.layout.transformedToOriginal(t)
                                val url = ctl.linkUrlAt(o - 1) ?: ctl.linkUrlAt(o) ?: return@awaitEachGesture
                                val slop = viewConfiguration.touchSlop
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                    if (!ch.pressed) {
                                        ch.consume()
                                        runCatching { uriHandler.openUri(url) }
                                        return@awaitEachGesture
                                    }
                                    if ((ch.position - down.position).getDistance() > slop) return@awaitEachGesture
                                }
                            }
                        },
                    readOnly = ctl.readMode || !ctl.bodyEditable,
                    textStyle = TextStyle(
                        color = c.text1,
                        fontSize = fontSp.sp,
                        lineHeight = (fontSp * 1.72f).sp, // .editor-body line-height 1.72
                        fontFamily = if (serifBody) Ink.serif else Ink.sans,
                    ),
                    cursorBrush = SolidColor(c.accent),
                    visualTransformation = transformation ?: VisualTransformation.None,
                    onTextLayout = { layoutRes = it },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth()) {
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

                // Code blocks: small Copy button in the top-right corner of every block
                if (layoutRes != null && transformation != null && layoutRes!!.layoutInput.text.length == transformation.layout.length) {
                    val lay2 = layoutRes!!
                    val tr2 = transformation
                    var kb = 0
                    while (kb < ctl.doc.paraCount) {
                        if (ctl.doc.paras[kb].type == BlockType.CODE && (kb == 0 || ctl.doc.paras[kb - 1].type != BlockType.CODE)) {
                            val first = kb
                            val off = tr2.layout.textStart(first)
                            if (off >= 0) {
                                val top2 = lay2.getLineTop(lay2.getLineForOffset(off.coerceAtMost(tr2.layout.length)))
                                androidx.compose.runtime.key(first + 100000) {
                                    CodeCopyButton(
                                        modifier = Modifier
                                            .padding(horizontal = 20.dp)
                                            .offset { IntOffset(0, padTopPx + top2.toInt() - 4.dp.roundToPx()) },
                                        onCopy = { ctl.copyCodeBlockAt(first) },
                                    )
                                }
                            }
                        }
                        kb++
                    }
                }

                // Tables: the text field only reserves their (tall) line; the real, sideways-scrolling table sits on top.
                val lay = layoutRes
                val tr = transformation
                if (lay != null && tr != null && lay.layoutInput.text.length == tr.layout.length) {
                    for (k in 0 until ctl.doc.paraCount) {
                        val para = ctl.doc.paras[k]
                        val data = para.table
                        if (para.type != BlockType.TABLE || data == null) continue
                        val off = tr.layout.objectOffset(k)
                        if (off < 0 || off >= tr.layout.length) continue
                        val line = lay.getLineForOffset(off)
                        val top = lay.getLineTop(line)
                        val bottom = lay.getLineBottom(line)
                        androidx.compose.runtime.key(k) {
                            TableBlock(
                                data = data,
                                modifier = Modifier
                                    .padding(horizontal = 20.dp)
                                    .offset { IntOffset(0, padTopPx + top.toInt()) }
                                    .height(with(density) { (bottom - top).toDp() }),
                                onClick = { ctl.openTable(k) },
                            )
                        }
                    }
                }
                }
            }

            // ── bottom panel: tags, notebook, counts ──
            // While the keyboard is up the writing area is small, so tags / notebook step aside.
            if (!imeVisible && !ctl.readMode) {
                TagsRow(tags = ctl.note.tags, onClick = ctl::openTagManager)
                NotebookRow(name = ctl.notebook?.name, colorHex = ctl.notebook?.color, onClick = ctl::openNotebookPicker)
            }
            EditorFooter(words = ctl.wordCount, chars = ctl.charCount, saved = ctl.saved)

            // ── code block actions (Copy / Bottom / Delete) while the caret is inside a code block ──
            if (ctl.bodyEditable && ctl.toolbarState.inCodeBlock) {
                CodeActionsBar(
                    canDelete = !ctl.readMode,
                    onCopy = ctl::copyCodeBlock,
                    onBottom = ctl::codeBlockToBottom,
                    onDelete = ctl::deleteCodeBlock,
                )
            }

            // ── formatting toolbar: docked at the bottom, right above the keyboard ──
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
                    onMarkdown = ctl::toggleMarkdown,
                    onInlineCode = { ctl.toggleMark(Mark.CODE) },
                    onCodeBlock = ctl::toggleCodeBlock,
                    onLink = ctl::onLinkButton,
                    onTable = ctl::insertTable,
                    onDivider = ctl::insertDivider,
                    onDateTime = ctl::insertDateTime,
                    scroll = toolbarScroll,
                )
            }
        }

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

        // ── Part 4: notebook picker, tag manager, create-notebook form ──
        if (ctl.nbPickerOpen) {
            NotebookPickerSheet(
                notebooks = ctl.pickerNotebooks,
                selectedId = ctl.note.notebookId,
                onPick = ctl::pickNotebook,
                onCreate = ctl::openCreateNotebook,
                onDismiss = ctl::closeNotebookPicker,
            )
        }
        if (ctl.tagManageOpen) {
            TagManageModal(
                tags = ctl.note.tags,
                saved = ctl.savedTagOptions,
                input = ctl.tagInput,
                onInput = ctl::onTagInputChange,
                onCommit = ctl::commitTag,
                onBackspaceEmpty = ctl::removeLastTag,
                onRemove = ctl::removeTag,
                onPickSaved = ctl::toggleSavedTag,
                onAddOrClose = ctl::addOrCloseTags,
                onClose = ctl::closeTagManager,
            )
        }
        if (ctl.nbCreateOpen) {
            NotebookFormModal(
                editing = null,
                title = "Create Notebook",
                subtitle = "Create a notebook and move this note into it.",
                onDismiss = ctl::closeCreateNotebook,
                onSave = ctl::createNotebookAndMove,
            )
        }

        // ── Part 5: link popover + table sheet ──
        if (ctl.linkOpen) {
            LinkModal(
                text = ctl.linkText,
                url = ctl.linkUrl,
                onText = ctl::onLinkTextChange,
                onUrl = ctl::onLinkUrlChange,
                onSubmit = ctl::submitLink,
                onClose = ctl::closeLink,
            )
        }
        ctl.tableEditing?.let { idx ->
            val data = ctl.doc.paras.getOrNull(idx)?.table
            if (data != null) {
                androidx.compose.runtime.key(idx) {
                    TableEditModal(initial = data, onClose = ctl::closeTable, onDelete = ctl::deleteTable)
                }
            }
        }

        // Last, so a toast (e.g. "Notebook name required") is never hidden behind a modal scrim.
        InkToast(
            toast = ctl.toast,
            onAction = {},
            onDismiss = ctl::dismissToast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(bottomInsets)
                .padding(bottom = 140.dp),
        )
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

/** Paints the checklist boxes and collapse chevrons (Canvas, no font glyphs) over the slots reserved in the text. */
private fun DrawScope.drawListControls(
    lay: TextLayoutResult,
    tr: RichTransformation,
    fontPx: Float,
    accent: Color,
    outline: Color,
    tick: Color,
    ruleColor: Color,
    codeFill: Color,
    codeBorder: Color,
) {
    val shownLen = lay.layoutInput.text.length
    val doc = tr.doc

    // GitHub-style code blocks: one rounded, bordered box behind all lines of a block
    var b = 0
    while (b < doc.paraCount) {
        if (doc.paras[b].type != BlockType.CODE) { b++; continue }
        var e = b
        while (e + 1 < doc.paraCount && doc.paras[e + 1].type == BlockType.CODE) e++
        val s0 = tr.layout.textStart(b)
        val e0 = tr.layout.textStart(e) + (doc.paraEnd(e) - doc.starts[e])
        if (s0 >= 0 && e0 >= 0) {
            val top = lay.getLineTop(lay.getLineForOffset(s0.coerceAtMost(shownLen)))
            val bottom = lay.getLineBottom(lay.getLineForOffset(e0.coerceAtMost(shownLen)))
            val padX = 10.dp.toPx()
            val padY = 7.dp.toPx()
            val tl = Offset(-padX, top - padY)
            val sz = Size(size.width + 2 * padX, bottom - top + 2 * padY)
            val rad = CornerRadius(9.dp.toPx())
            drawRoundRect(codeFill, tl, sz, rad)
            drawRoundRect(codeBorder, tl, sz, rad, style = Stroke(1.dp.toPx()))
        }
        b = e + 1
    }
    for (k in 0 until doc.paraCount) {
        val p = doc.paras[k]
        if (p.type == BlockType.HR) {
            val oi = tr.layout.objectOffset(k)
            if (oi in 0 until shownLen) {
                val ln = lay.getLineForOffset(oi)
                val y = (lay.getLineTop(ln) + lay.getLineBottom(ln)) / 2f
                drawLine(ruleColor, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round)
            }
        }
        val ci = tr.layout.chevronOffset(k)
        if (ci in 0 until shownLen) {
            val r = lay.getBoundingBox(ci)
            val cx = (r.left + r.right) / 2f
            val cy = (r.top + r.bottom) / 2f
            // chevron = two round-capped strokes: › when collapsed, ⌄ when open
            val h = fontPx * 0.2f
            val sw = (fontPx * 0.11f).coerceAtLeast(1.5f)
            if (p.collapsed) {
                drawLine(accent, Offset(cx - h * 0.5f, cy - h), Offset(cx + h * 0.5f, cy), sw, StrokeCap.Round)
                drawLine(accent, Offset(cx + h * 0.5f, cy), Offset(cx - h * 0.5f, cy + h), sw, StrokeCap.Round)
            } else {
                drawLine(accent, Offset(cx - h, cy - h * 0.5f), Offset(cx, cy + h * 0.5f), sw, StrokeCap.Round)
                drawLine(accent, Offset(cx, cy + h * 0.5f), Offset(cx + h, cy - h * 0.5f), sw, StrokeCap.Round)
            }
        }
        val bi = tr.layout.boxOffset(k)
        if (bi in 0 until shownLen) {
            val r = lay.getBoundingBox(bi)
            val side = fontPx * 0.95f
            val left = r.left + (r.right - r.left - side) / 2f
            val top = (r.top + r.bottom) / 2f - side / 2f
            val sw = (fontPx * 0.11f).coerceAtLeast(1.5f)
            val radius = CornerRadius(side * 0.26f)
            if (p.checked) {
                drawRoundRect(accent, Offset(left, top), Size(side, side), radius)
                val t = Path().apply {
                    moveTo(left + side * 0.25f, top + side * 0.54f)
                    lineTo(left + side * 0.43f, top + side * 0.72f)
                    lineTo(left + side * 0.76f, top + side * 0.32f)
                }
                drawPath(t, tick, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawRoundRect(outline, Offset(left + sw / 2, top + sw / 2), Size(side - sw, side - sw), radius, style = Stroke(sw))
            }
        }
    }
}

/** A tap target inside the list prefix: the collapse chevron or the checklist box of paragraph [para]. */
private class ListHit(val para: Int, val chevron: Boolean)

/**
 * Which chevron / checkbox glyph (if any) is under [pos]. [pos] is in the text layout's own coordinates.
 * Only the first line of a paragraph carries glyphs, so wrapped lines never hit.
 */
private fun hitListControl(lay: TextLayoutResult, tr: RichTransformation, pos: Offset, pad: Float): ListHit? {
    if (pos.y < 0f || pos.y > lay.size.height.toFloat()) return null
    val shownLen = lay.layoutInput.text.length
    val line = lay.getLineForVerticalPosition(pos.y)
    val para = tr.layout.paraAtTransformed(lay.getLineStart(line))
    var best: ListHit? = null
    var bestDist = Float.MAX_VALUE
    fun consider(offset: Int, chevron: Boolean) {
        if (offset < 0 || offset >= shownLen) return
        val r = lay.getBoundingBox(offset)
        if (pos.y < r.top || pos.y > r.bottom) return
        if (pos.x < r.left - pad || pos.x > r.right + pad) return
        val d = kotlin.math.abs(pos.x - (r.left + r.right) / 2f)
        if (d < bestDist) { bestDist = d; best = ListHit(para, chevron) }
    }
    consider(tr.layout.chevronOffset(para), chevron = true)
    consider(tr.layout.boxOffset(para), chevron = false)
    return best
}

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
            "This note has images or table cells with formatting. Those arrive in the next " +
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
private fun PanelRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val c = Ink.colors
    Column(Modifier.fillMaxWidth().background(c.bg).tap(onClick)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
            // `.fa-chevron-up` — tells you the row opens something
            FaIcon(R.drawable.ic_fa_chevron_up, tint = c.text3, size = 11.dp)
        }
    }
}

/** `.tag-selector-row`: "In <accent bold names> +N more" / "No tags selected". Tap → Manage tags. */
@Composable
private fun TagsRow(tags: List<String>, onClick: () -> Unit) {
    val c = Ink.colors
    PanelRow(onClick) {
        FaIcon(R.drawable.ic_fa_tag, tint = c.text3, size = 14.dp)
        if (tags.isEmpty()) {
            InkText("No tags selected", modifier = Modifier.weight(1f), size = 13.sp, color = c.text3)
        } else {
            val more = if (tags.size > 2) " +${tags.size - 2} more" else ""
            InkText(
                text = buildAnnotatedString {
                    append("In ")
                    withStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Bold)) {
                        append(tags.take(2).joinToString(", "))
                    }
                    append(more)
                },
                modifier = Modifier.weight(1f),
                size = 13.sp,
                color = c.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** `.nb-selector`: "In <notebook>" / "No notebook selected". Tap → Move to Notebook. */
@Composable
private fun NotebookRow(name: String?, colorHex: String?, onClick: () -> Unit) {
    val c = Ink.colors
    PanelRow(onClick) {
        FaIcon(R.drawable.ic_fa_book_open, tint = c.text3, size = 14.dp)
        if (name == null) {
            InkText("No notebook selected", modifier = Modifier.weight(1f), size = 13.sp, color = c.text3)
        } else {
            Row(Modifier.weight(1f)) {
                InkText("In ", size = 13.sp, color = c.text2)
                InkText(
                    name,
                    modifier = Modifier.weight(1f, fill = false),
                    size = 13.sp,
                    weight = FontWeight.SemiBold,
                    color = notebookColor(colorHex.orEmpty(), c.accent),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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

// ─────────────────────────────────────────────
// Table block (Part 5)
// ─────────────────────────────────────────────

/**
 * A table as shown in the body: fixed-height rows, plain-text cells (one line, ellipsis), scrolls sideways when it is wider
 * than the screen. Tapping it opens the table sheet. Its height matches the line [RichTransformation] reserved for it.
 */
@Composable
private fun TableBlock(data: TableData, modifier: Modifier, onClick: () -> Unit) {
    val c = Ink.colors
    val t = data.normalized()
    val shape = RoundedCornerShape(8.dp)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val colW = maxOf(110.dp, maxWidth / t.colCount)
        Column(
            Modifier
                .padding(vertical = RichTransformation.TABLE_PAD_DP.dp)
                .clip(shape)
                .border(1.dp, c.border, shape)
                .horizontalScroll(rememberScrollState())
                .tap(onClick),
        ) {
            for (r in 0 until t.rowCount) {
                val head = t.header && r == 0
                Row {
                    for (cc in 0 until t.colCount) {
                        Box(
                            Modifier
                                .width(colW)
                                .height(RichTransformation.TABLE_ROW_DP.dp)
                                .background(if (head) c.accentDim else c.bgCard)
                                .border(0.5.dp, c.border)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            InkText(
                                t.cell(r, cc),
                                size = 13.sp,
                                weight = if (head) FontWeight.Bold else FontWeight.Normal,
                                color = c.text1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** GitHub-style "Copy" chip in the corner of a code block; shows "Copied" for a moment. */
@Composable
private fun CodeCopyButton(modifier: Modifier, onCopy: () -> Unit) {
    val c = Ink.colors
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1000L); copied = false } }
    val shape = RoundedCornerShape(7.dp)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
        Box(
            Modifier
                .padding(end = 6.dp)
                .clip(shape)
                .background(c.bg.copy(alpha = 0.92f))
                .border(1.dp, c.border, shape)
                .tap { onCopy(); copied = true }
                .padding(horizontal = 9.dp, vertical = 4.dp),
        ) {
            InkText(if (copied) "Copied" else "Copy", size = 11.sp, weight = FontWeight.Bold, color = if (copied) c.success else c.text2)
        }
    }
}
