package com.inkwell.notes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.unit.IntOffset
import android.content.Intent
import android.net.Uri
import com.inkwell.notes.editor.BlockType
import com.inkwell.notes.editor.LinkTable
import com.inkwell.notes.editor.Mark
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
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
 * Part 3: checklists (tap a box), nested + collapsible lists (tap a chevron), markdown shortcuts + toggle,
 *   Enter / Backspace list behaviour, basic code blocks.
 * Part 4: tags row → "Manage tags" modal, notebook row → "Move to Notebook" sheet (+ inline create).
 *   Both rows are tappable; they are hidden in Reading mode like the web (`.read-mode .tags-row`).
 * Part 7: Markdown text mode (toolbar M), Reading-mode polish, Download note (PDF / md / html / txt / xls).
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

    BackHandler(enabled = ctl.toolbarManageOpen) { ctl.closeToolbarManager() }
    BackHandler(enabled = ctl.exportOpen) { ctl.closeExport() }
    // Back with an image selected only drops the handles; it must not leave the note.
    BackHandler(enabled = ctl.selectedImage != null && !ctl.exportOpen && !ctl.toolbarManageOpen) { ctl.deselectImage() }
    BackHandler(enabled = ctl.drawOpen) { ctl.closeDraw() }
    BackHandler(enabled = ctl.linkDraft != null) { ctl.closeLink() }
    BackHandler(enabled = ctl.tableEdit != null) { ctl.closeCell() }
    BackHandler(enabled = ctl.deleteConfirmOpen) { ctl.dismissDelete() }
    BackHandler(enabled = ctl.nbCreateOpen) { ctl.closeCreateNotebook() }
    BackHandler(enabled = ctl.tagManageOpen) { ctl.closeTagManager() }
    BackHandler(enabled = ctl.nbPickerOpen) { ctl.closeNotebookPicker() }

    // Photo picker: the system chooser (no storage permission needed); the picture is scaled + encoded off the main thread.
    val scope = rememberCoroutineScope()
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val url = withContext(Dispatchers.IO) { com.inkwell.notes.util.ImageIo.uriToDataUrl(context, uri) }
                if (url != null) ctl.insertImage(url, "Photo", drawing = false) else ctl.photoFailed()
            }
        }
    }

    // Reading mode: drop focus + hide the keyboard so the note is just a page to read.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(ctl.readMode) {
        if (ctl.readMode) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    // Export: the system "save as" chooser (no storage permission needed).
    val exportMime = ctl.pendingExport?.mime ?: "application/octet-stream"
    val saveExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(exportMime)) { uri ->
        val job = ctl.pendingExport
        if (uri == null || job == null) {
            ctl.consumeExport()
        } else {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(job.bytes) } != null
                    }.getOrDefault(false)
                }
                ctl.exportSaved(ok)
            }
        }
    }
    LaunchedEffect(ctl.pendingExport) {
        ctl.pendingExport?.let { saveExport.launch(it.fileName) }
    }

    // Part 8: haptics like the web `haptic()` — light on save, medium on pin, heavy on delete.
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val tickLight = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }
    val tickStrong = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) }

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
            doneColor = c.text3,
            codeBg = Color(0xFF1C1A17),
            codeFg = Color(0xFFD9F7C7),
            inlineCodeBg = c.text3.copy(alpha = 0.16f),
            inlineCodeFg = c.accent,
            linkColor = c.accent,
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
    // Tables are Compose overlays: each one reports its height, and the text field reserves that many blank lines.
    val tableHeights = remember { mutableStateMapOf<Int, Int>() }
    val lineHpx = with(LocalDensity.current) { (fontSp * 1.72f).sp.toPx() }
    val marginPx = with(LocalDensity.current) { 12.dp.toPx() }
    val objLines: List<Int> = if (ctl.bodyEditable) {
        ctl.doc.paras.mapIndexed { k, p ->
            if (p.type == BlockType.TABLE || p.type == BlockType.IMAGE) {
                val h = tableHeights[k]
                if (h == null) (if (p.type == BlockType.IMAGE) 6 else 4) else kotlin.math.ceil((h + marginPx) / lineHpx).toInt().coerceAtLeast(2)
            } else 1
        }
    } else emptyList()
    val transformation = if (ctl.bodyEditable) {
        remember(ctl.doc, richStyle, objLines) { RichTransformation(ctl.doc, richStyle) { k -> objLines.getOrElse(k) { 1 } } }
    } else null
    val transformationNow by rememberUpdatedState(transformation)
    val tapPadPx = with(LocalDensity.current) { 8.dp.toPx() }
    val tickColor = onAccent()
    val fontPx = with(LocalDensity.current) { fontSp.sp.toPx() }

    // Keep the caret (plus a margin, so sub/superscript lines are never cut) inside the visible body.
    LaunchedEffect(ctl.body.selection, ctl.body.text, layoutRes, viewportH) {
        val lay = layoutRes ?: return@LaunchedEffect
        if (viewportH == 0 || !ctl.bodyEditable || ctl.sourceMode) return@LaunchedEffect
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
                onPin = { tickStrong(); ctl.togglePin() },
                onExport = ctl::openExport,
                onDelete = ctl::requestDelete,
                onSave = { tickLight(); ctl.save(); onBack() },
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

                if (ctl.sourceMode) {
                    SourceField(
                        value = ctl.source,
                        onChange = ctl::onSourceChange,
                        fontSp = fontSp.toFloat(),
                        focus = bodyFocus,
                        scroll = bodyScroll,
                        viewportH = viewportH,
                        readOnly = ctl.readMode,
                    )
                } else androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalClipboardManager provides
                        MarkdownClipboard(androidx.compose.ui.platform.LocalClipboardManager.current, ctl),
                ) { Box(Modifier.fillMaxWidth()) {
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
                                drawBlockDecor(lay, tr, c.borderHi, Color(0xFF1C1A17), Color.White.copy(alpha = 0.12f))
                                drawListControls(lay, tr, fontPx, c.accent, c.text3, tickColor)
                            }
                        }
                        // Reading mode: tapping a link opens it.
                        .pointerInput(ctl.readMode) {
                            if (!ctl.readMode) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                val lay = layoutRes
                                val tr = transformationNow
                                if (lay == null || tr == null) return@awaitEachGesture
                                val url = linkAt(lay, tr, down.position) ?: return@awaitEachGesture
                                val slop = viewConfiguration.touchSlop
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                    if (!ch.pressed) {
                                        ch.consume()
                                        openUrl(context, url)
                                        return@awaitEachGesture
                                    }
                                    if ((ch.position - down.position).getDistance() > slop) return@awaitEachGesture
                                }
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
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrectEnabled = ctl.spellcheck,
                    ),
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

                // ── Part 5 overlays: tables + code-block buttons, positioned from the text layout ──
                val lay = layoutRes
                val tr = transformationNow
                if (lay != null && tr != null && ctl.bodyEditable) {
                    val shown = lay.layoutInput.text.length
                    val doc = tr.doc
                    for (k in 0 until doc.paraCount) {
                        val p = doc.paras[k]
                        val ps = tr.layout.prefixStart(k)
                        if (ps < 0 || ps > shown) continue
                        val table = p.table
                        if (p.type == BlockType.TABLE && table != null) {
                            TableOverlay(
                                table = table,
                                style = richStyle,
                                editable = !ctl.readMode,
                                onCell = { r, cc -> ctl.openCell(k, r, cc) },
                                modifier = Modifier
                                    .padding(start = 20.dp, end = 20.dp)
                                    .offset {
                                        val l = layoutRes
                                        val y = if (l == null) 0f else l.getLineTop(l.getLineForOffset(ps.coerceAtMost(maxOf(0, l.layoutInput.text.length - 1))))
                                        IntOffset(0, (y + with(density) { 8.dp.toPx() + 6.dp.toPx() }).toInt())
                                    }
                                    .onSizeChanged { sz -> if (tableHeights[k] != sz.height) tableHeights[k] = sz.height },
                            )
                        } else if (p.type == BlockType.IMAGE && p.image != null) {
                            ImageOverlay(
                                image = p.image,
                                selected = ctl.selectedImage == k && !ctl.readMode,
                                editable = !ctl.readMode,
                                onSelect = { ctl.selectImage(k) },
                                onCommit = { ctl.commitImage(k, it) },
                                onDelete = { ctl.deleteImage(k) },
                                modifier = Modifier
                                    .padding(start = 20.dp, end = 20.dp)
                                    .offset {
                                        val l = layoutRes
                                        val y = if (l == null) 0f else l.getLineTop(l.getLineForOffset(ps.coerceAtMost(maxOf(0, l.layoutInput.text.length - 1))))
                                        IntOffset(0, (y + with(density) { 8.dp.toPx() + 6.dp.toPx() }).toInt())
                                    }
                                    .onSizeChanged { sz -> if (tableHeights[k] != sz.height) tableHeights[k] = sz.height },
                            )
                        } else if (tr.layout.isCodeHeader(k)) {
                            val blockId = k
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 28.dp)
                                    .offset {
                                        val l = layoutRes
                                        val y = if (l == null) 0f else l.getLineTop(l.getLineForOffset(ps.coerceAtMost(maxOf(0, l.layoutInput.text.length - 1))))
                                        IntOffset(0, (y + with(density) { 8.dp.toPx() + 4.dp.toPx() }).toInt())
                                    },
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                            ) {
                                CodeChip("Copy") { ctl.copyCode(blockId) }
                                if (!ctl.readMode) {
                                    CodeChip("Bottom") { ctl.jumpBelowCode(blockId) }
                                    CodeChip("Delete", DeleteRed) { ctl.deleteCode(blockId) }
                                }
                            }
                        }
                    }
                }
                } }
            }

            // ── bottom panel: tags, notebook, counts ──
            // While the keyboard is up the writing area is small, so tags / notebook step aside.
            if (!imeVisible && !ctl.readMode) {
                TagsRow(tags = ctl.note.tags, onClick = ctl::openTagManager)
                NotebookRow(name = ctl.notebook?.name, colorHex = ctl.notebook?.color, onClick = ctl::openNotebookPicker)
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
                    onTable = ctl::insertTable,
                    onDateTime = ctl::insertDateTime,
                    onLink = ctl::openLink,
                    onHr = ctl::insertHr,
                    onPhoto = { pickPhoto.launch("image/*") },
                    onDraw = ctl::openDraw,
                    onCode = ctl::toggleInlineCode,
                    onCodeBlock = ctl::toggleCodeBlock,
                    hidden = ctl.hiddenTools,
                    onManage = ctl::openToolbarManager,
                    scroll = toolbarScroll,
                )
            }

            // Reading mode = just the note: the tags / notebook / counts / toolbar step away.
            if (!ctl.readMode) {
                EditorFooter(words = ctl.wordCount, chars = ctl.charCount, selected = ctl.selectedWords, streak = ctl.streak, saved = ctl.saved)
            }
        }

        if (ctl.deleteConfirmOpen) {
            DeleteConfirmModal(
                count = 1,
                title = "Delete this note?",
                message = "This note will be deleted permanently.",
                onCancel = ctl::dismissDelete,
                onConfirm = {
                    tickStrong()
                    ctl.confirmDelete()
                    onBack()
                },
            )
        }

        if (ctl.toolbarManageOpen) {
            ToolbarModal(
                hidden = ctl.hiddenTools,
                onToggle = ctl::setToolVisible,
                onReset = ctl::resetToolbar,
                onClose = ctl::closeToolbarManager,
            )
        }

        if (ctl.exportOpen) {
            ExportModal(onPick = ctl::export, onClose = ctl::closeExport)
        }

        // ── Part 6: drawing dialog ──
        if (ctl.drawOpen) {
            DrawModal(
                onInsert = { url ->
                    ctl.closeDraw()
                    ctl.insertImage(url, "Drawing", drawing = true)
                },
                onClose = ctl::closeDraw,
            )
        }

        // ── Part 5: link dialog, table cell sheet ──
        ctl.linkDraft?.let { d ->
            LinkModal(draft = d, onSubmit = ctl::submitLink, onRemove = ctl::removeLink, onClose = ctl::closeLink)
        }
        val te = ctl.tableEdit
        val tbl = ctl.tableForEdit
        if (te != null && tbl != null) {
            TableCellSheet(
                table = tbl, row = te.row, col = te.col, text = ctl.cellText(),
                onText = ctl::onCellText,
                onRowAbove = ctl::rowAbove, onRowBelow = ctl::rowBelow, onDeleteRow = ctl::deleteRow,
                onColLeft = ctl::colLeft, onColRight = ctl::colRight, onDeleteCol = ctl::deleteCol,
                onToggleHeader = ctl::toggleHeader, onDeleteTable = ctl::deleteTable,
                onClose = ctl::closeCell,
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
    onExport: () -> Unit,
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
        TopIconButton(R.drawable.ic_fa_download, onClick = onExport)
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
) {
    val shownLen = lay.layoutInput.text.length
    val doc = tr.doc
    for (k in 0 until doc.paraCount) {
        val p = doc.paras[k]
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
            "This note uses markup the editor can't write back yet (for example merged table cells or " +
                "relative image paths), so the body stays read-only and untouched (title, pin and delete work).",
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
private fun EditorFooter(words: Int, chars: Int, selected: Int, streak: Int, saved: Boolean) {
    val c = Ink.colors
    Column(Modifier.fillMaxWidth().background(c.bgCard)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InkText("$words ${if (words == 1) "word" else "words"}", size = 11.sp, color = c.text3)
                InkText("$chars ${if (chars == 1) "char" else "chars"}", size = 11.sp, color = c.text3)
                InkText("$selected selected", size = 11.sp, color = if (selected > 0) c.accent else c.text3)
                if (streak > 0) {
                    InkText(
                        "🔥 $streak day${if (streak != 1) "s" else ""}",
                        size = 11.sp,
                        weight = FontWeight.Bold,
                        color = if (streak >= 7) Color(0xFFF28C40) else c.text2,
                    )
                }
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
// Part 5 helpers
// ─────────────────────────────────────────────

/** Small pill on a code block's header line. */
@Composable
private fun CodeChip(label: String, color: Color = Color(0xFFD9F7C7), onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .height(24.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.10f))
            .tap(onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        InkText(label, size = 11.sp, weight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

/** Divider lines and code-block cards, painted behind the text over the lines RichLayout reserved. */
private fun DrawScope.drawBlockDecor(
    lay: TextLayoutResult,
    tr: RichTransformation,
    ruleColor: Color,
    cardColor: Color,
    cardBorder: Color,
) {
    val shown = lay.layoutInput.text.length
    if (shown == 0) return
    val doc = tr.doc
    fun lineOf(t: Int) = lay.getLineForOffset(t.coerceIn(0, shown))
    var k = 0
    while (k < doc.paraCount) {
        val p = doc.paras[k]
        val ps = tr.layout.prefixStart(k)
        if (ps >= 0 && p.type == BlockType.HR) {
            val line = lineOf(ps)
            val y = (lay.getLineTop(line) + lay.getLineBottom(line)) / 2f
            drawLine(ruleColor, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round)
        } else if (ps >= 0 && tr.layout.isCodeHeader(k)) {
            var last = k
            while (last + 1 < doc.paraCount && doc.paras[last + 1].type == BlockType.CODE) last++
            val endT = tr.layout.textStart(last) + (doc.paraEnd(last) - doc.starts[last])
            val top = lay.getLineTop(lineOf(ps))
            val bottom = lay.getLineBottom(lineOf(endT))
            val pad = 8.dp.toPx()
            val tl = Offset(-pad, top)
            val sz = Size(size.width + pad * 2, bottom - top + 4.dp.toPx())
            drawRoundRect(cardColor, tl, sz, CornerRadius(12.dp.toPx()))
            drawRoundRect(cardBorder, tl, sz, CornerRadius(12.dp.toPx()), style = Stroke(1.dp.toPx()))
            k = last
        }
        k++
    }
}

/** The link under [pos] (text-layout coordinates), if any. */
private fun linkAt(lay: TextLayoutResult, tr: RichTransformation, pos: Offset): String? {
    val doc = tr.doc
    val shown = lay.layoutInput.text.length
    if (pos.y < 0f || pos.y > lay.size.height.toFloat()) return null
    val t = lay.getOffsetForPosition(pos)
    for (ti in intArrayOf(t - 1, t)) {
        if (ti < 0 || ti >= shown) continue
        val r = lay.getBoundingBox(ti)
        if (pos.x < r.left || pos.x > r.right || pos.y < r.top || pos.y > r.bottom) continue
        val o = tr.layout.transformedToOriginal(ti)
        if (o !in 0 until doc.text.length || doc.text[o] == '\n') continue
        val id = Mark.linkId(doc.marks[o])
        if (id != 0) return LinkTable.urlOf(id)
    }
    return null
}

/** Opens http(s) / mailto / tel links in the matching app; anything else is ignored. */
private fun openUrl(context: android.content.Context, url: String) {
    val u = url.trim()
    val ok = listOf("http://", "https://", "mailto:", "tel:").any { u.startsWith(it, ignoreCase = true) }
    if (!ok) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Copy / cut in the rendered view puts the selection on the clipboard as Markdown text
 * (`**bold**`, `# heading`, `- item`), so pasting into another app keeps the structure.
 * Everything else about the clipboard is the real one ([base]).
 */
private class MarkdownClipboard(
    private val base: androidx.compose.ui.platform.ClipboardManager,
    private val ctl: EditorController,
) : androidx.compose.ui.platform.ClipboardManager by base {
    override fun setText(annotatedString: androidx.compose.ui.text.AnnotatedString) {
        val sel = ctl.body.selection
        val md = if (ctl.bodyEditable && !ctl.sourceMode && !sel.collapsed) {
            runCatching { com.inkwell.notes.editor.HtmlCodec.toMarkdown(ctl.doc.slice(sel.min, sel.max)).trim('\n') }.getOrNull()
        } else null
        base.setText(if (md.isNullOrEmpty()) annotatedString else androidx.compose.ui.text.AnnotatedString(md))
    }
}

/** Markdown text mode: the note's markdown source as plain monospace text. */
@Composable
private fun SourceField(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    fontSp: Float,
    focus: FocusRequester,
    scroll: androidx.compose.foundation.ScrollState,
    viewportH: Int,
    readOnly: Boolean,
) {
    val c = Ink.colors
    val density = LocalDensity.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var fieldTop by remember { mutableIntStateOf(0) }

    // Keep the caret on screen while typing (same rule as the rendered view: a margin above the keyboard / below the top).
    LaunchedEffect(value.selection, value.text, layout, viewportH, fieldTop) {
        val lay = layout ?: return@LaunchedEffect
        if (viewportH == 0) return@LaunchedEffect
        val at = value.selection.end.coerceIn(0, lay.layoutInput.text.length)
        val r = lay.getCursorRect(at)
        val margin = with(density) { 32.dp.toPx() }
        val top = fieldTop + r.top
        val bottom = fieldTop + r.bottom
        val cur = scroll.value
        when {
            bottom + margin > cur + viewportH -> scroll.animateScrollTo((bottom + margin - viewportH).toInt().coerceAtLeast(0))
            top - margin < cur -> scroll.animateScrollTo((top - margin).toInt().coerceAtLeast(0))
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkText("Markdown text mode — tap M to render", size = 11.sp, color = c.accent, weight = FontWeight.Bold)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                .defaultMinSize(minHeight = 240.dp)
                .onGloballyPositioned { fieldTop = it.positionInParent().y.toInt() },
            readOnly = readOnly,
            onTextLayout = { layout = it },
            textStyle = TextStyle(
                color = c.text1,
                fontSize = (fontSp - 1f).sp,
                lineHeight = ((fontSp - 1f) * 1.6f).sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            ),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
        )
    }
}
