package com.inkwell.notes.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.inkwell.notes.BuildConfig
import com.inkwell.notes.R
import com.inkwell.notes.auth.AuthUser
import com.inkwell.notes.data.EditorFontSize
import com.inkwell.notes.ui.components.BottomNav
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.components.InkToast
import com.inkwell.notes.ui.components.InnerTopBar
import com.inkwell.notes.ui.components.NavTab
import com.inkwell.notes.ui.components.onAccent
import com.inkwell.notes.ui.components.tap
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.ThemeMode
import com.inkwell.notes.update.UpdateViewModel

/** Tile colour for a row icon (`.si-icon`, `.ac`, `.info`, `.success`, `.danger`). */
private enum class Tone { Default, Info, Success, Danger }

/**
 * Port of `src/pages/Settings.jsx`.
 *
 * Google Drive Sync → App Update → Appearance → Editor → Local Data → Storage →
 * Danger zone → About. Check-for-updates talks to [UpdateViewModel] (sheet is drawn by UpdateHost).
 */
@Composable
fun SettingsScreen(
    user: AuthUser,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onNavTab: (NavTab) -> Unit,
    onOpenProfile: () -> Unit,
    /** "Manage tags" row — opens the Tags page on top of Settings (web: navigate('/tags')). */
    onManageTags: () -> Unit = { onNavTab(NavTab.Tags) },
    /** "Reset all data" finished — the app signs out and falls back to the default theme. */
    onResetDone: () -> Unit,
    vm: SettingsViewModel = viewModel(),
    updateVm: UpdateViewModel = viewModel(),
) {
    val c = Ink.colors
    val s by vm.state.collectAsStateWithLifecycle()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) vm.exportTo(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) vm.importFrom(uri) }

    val busy = s.backupBusy || s.restoreBusy

    // The ViewModel outlives the screen — re-read counts / storage whenever the page opens.
    LaunchedEffect(Unit) {
        vm.refresh()
        vm.loadBackupInfo()
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            InnerTopBar(title = "Settings", onBack = onBack)

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 62.dp + navInset + 14.dp),
            ) {
                // ── Google Drive Sync ──
                GroupLabel("Google Drive Sync", leading = {
                    androidx.compose.foundation.Image(
                        painterResource(R.drawable.ic_google_g),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                    )
                })
                AccountRow(user)
                SettingsRow(
                    icon = R.drawable.ic_fa_clock_rotate_left,
                    label = "Last backup",
                    sub = s.lastBackup ?: "No backup yet",
                )
                SettingsRow(
                    icon = R.drawable.ic_fa_cloud_arrow_up,
                    tone = Tone.Success,
                    label = "Back up now",
                    sub = "Save all notes to your Google Drive",
                    spinning = s.backupBusy,
                    enabled = !busy,
                    chevron = !s.backupBusy,
                    onClick = vm::backup,
                )
                SettingsRow(
                    icon = R.drawable.ic_fa_cloud_arrow_down,
                    tone = Tone.Info,
                    label = "Restore from Drive",
                    sub = "Replace local data with your cloud backup",
                    spinning = s.restoreBusy,
                    enabled = !busy,
                    chevron = !s.restoreBusy,
                    onClick = { vm.ask(SettingsConfirm.RestoreDrive) },
                )
                s.syncError?.let { err ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FaIcon(R.drawable.ic_fa_circle_exclamation, tint = c.danger, size = 12.dp)
                        InkText(err, size = 12.sp, color = c.danger)
                    }
                }

                // ── App Update ──
                GroupLabel("App Update")
                SettingsRow(
                    icon = R.drawable.ic_fa_download,
                    tone = Tone.Info,
                    label = "Check for updates",
                    sub = "Find the latest APK from GitHub Releases",
                    chevron = true,
                    onClick = updateVm::checkManual,
                )

                // ── Appearance ──
                GroupLabel("Appearance")
                SettingsRow(
                    icon = R.drawable.ic_fa_palette,
                    label = "Theme",
                    sub = "Light, Dark, or GitHub",
                    right = {
                        ThemeSeg(themeMode) { mode, label, icon ->
                            onThemeChange(mode)
                            vm.themeChanged(label, icon)
                        }
                    },
                )
                SettingsRow(
                    icon = R.drawable.ic_fa_text_height,
                    label = "Editor font size",
                    sub = "Applies to the note editor body text",
                    right = { FontSizeSeg(s.fontSize, vm::setFontSize) },
                )

                // ── Editor ──
                GroupLabel("Editor")
                SettingsRow(R.drawable.ic_fa_spell_check, "Spell check", "Underline misspelled words",
                    right = { Toggle(s.spellcheck) { vm.setPref("spellcheck", it) } })
                SettingsRow(R.drawable.ic_fa_r_floppy_disk, "Auto-save", "Save notes automatically while typing",
                    right = { Toggle(s.autosave) { vm.setPref("autosave", it) } })
                SettingsRow(R.drawable.ic_fa_code, "Markdown mode", "Enable Markdown shortcuts by default",
                    right = { Toggle(s.markdownMode) { vm.setPref("markdownMode", it) } })
                SettingsRow(R.drawable.ic_fa_font, "Serif body font", "Use Lora instead of DM Sans for notes",
                    right = { Toggle(s.serifBody) { vm.setPref("serifBody", it) } })

                // ── Local Data ──
                GroupLabel("Local Data")
                SettingsRow(
                    icon = R.drawable.ic_fa_arrow_up_from_bracket,
                    tone = Tone.Info,
                    label = "Export all notes",
                    sub = "Save a JSON backup to this device",
                    chevron = true,
                    onClick = { exportLauncher.launch(vm.exportFileName()) },
                )
                SettingsRow(
                    icon = R.drawable.ic_fa_file_import,
                    tone = Tone.Success,
                    label = "Import notes",
                    sub = "Restore from a local JSON backup file",
                    chevron = true,
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                )
                SettingsRow(
                    icon = R.drawable.ic_fa_tags,
                    label = "Manage tags",
                    sub = "${s.tagCount} unique tag${if (s.tagCount != 1) "s" else ""} across all notes",
                    chevron = true,
                    onClick = onManageTags,
                )

                // ── Storage ──
                GroupLabel("Storage")
                StorageRow(s)

                // ── Danger zone ──
                GroupLabel("Danger zone", color = c.danger)
                SettingsRow(
                    icon = R.drawable.ic_fa_note_sticky,
                    tone = Tone.Danger,
                    label = "Delete all notes",
                    sub = "Permanently remove every note",
                    onClick = { vm.ask(SettingsConfirm.ClearNotes) },
                )
                SettingsRow(
                    icon = R.drawable.ic_fa_r_trash_can,
                    tone = Tone.Danger,
                    label = "Reset all data",
                    sub = "Wipe all notes, notebooks, and settings",
                    onClick = { vm.ask(SettingsConfirm.ResetAll) },
                )

                // ── About ──
                GroupLabel("About")
                SettingsRow(
                    icon = R.drawable.ic_fa_user,
                    label = "Profile",
                    sub = "Your Google account & writing stats",
                    chevron = true,
                    onClick = onOpenProfile,
                )
                SettingsRow(
                    icon = R.drawable.ic_fa_circle_info,
                    label = "Inkwell",
                    sub = "v${BuildConfig.VERSION_NAME} — Built with ♥",
                )

                // .app-version
                InkText(
                    "Inkwell v${BuildConfig.VERSION_NAME} · Synced via Google Drive · Data never leaves your accounts",
                    size = 11.sp,
                    color = c.text3,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp),
                )
            }
        }

        BottomNav(
            active = null,
            onSelect = onNavTab,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        InkToast(
            toast = s.toast,
            onAction = {},
            onDismiss = vm::dismissToast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 17.dp, end = 17.dp, bottom = 62.dp + navInset + 12.dp),
        )

        s.confirm?.let { kind ->
            val (title, sub, okLabel, danger) = when (kind) {
                SettingsConfirm.RestoreDrive -> ConfirmText(
                    "Restore from Google Drive?",
                    "Your current local data will be replaced by the cloud backup. This cannot be undone.",
                    "Restore", false,
                )
                SettingsConfirm.ClearNotes -> ConfirmText(
                    "Delete all notes?",
                    "Every note will be permanently removed. Notebooks will remain.",
                    "Delete", true,
                )
                SettingsConfirm.ResetAll -> ConfirmText(
                    "Reset everything?",
                    "All notes, notebooks, settings, and profile will be wiped. This cannot be undone.",
                    "Reset", true,
                )
            }
            ConfirmModal(
                title = title,
                sub = sub,
                okLabel = okLabel,
                danger = danger,
                onCancel = vm::dismissConfirm,
                onConfirm = { vm.confirm(onResetDone) },
            )
        }
    }
}

private data class ConfirmText(val title: String, val sub: String, val okLabel: String, val danger: Boolean)

// ─── building blocks ────────────────────────────────────────────────

/** `.sg-label` */
@Composable
private fun GroupLabel(
    text: String,
    color: Color = Ink.colors.text3,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(5.dp))
        }
        InkText(
            text.uppercase(),
            size = 11.sp,
            weight = FontWeight.ExtraBold,
            color = color,
            letterSpacing = 0.8.sp,
        )
    }
}

/**
 * `.si` row — 36dp icon tile, label + sub, optional [right] content or chevron,
 * 1dp divider under it. Clickable only when [onClick] is given and [enabled].
 */
@Composable
private fun SettingsRow(
    @DrawableRes icon: Int,
    label: String,
    sub: String,
    tone: Tone = Tone.Default,
    spinning: Boolean = false,
    enabled: Boolean = true,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    right: (@Composable () -> Unit)? = null,
) {
    val c = Ink.colors
    val (tile, tint) = toneColors(tone)
    val danger = tone == Tone.Danger
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
            .then(if (onClick != null && enabled) Modifier.tap(onClick) else Modifier)
            .drawBehind {
                drawRect(c.border, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
            }
            .padding(horizontal = 2.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tile, tint, spinning)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            InkText(label, size = 14.sp, weight = FontWeight.ExtraBold, color = if (danger) c.danger else c.text1)
            Spacer(Modifier.height(1.dp))
            InkText(sub, size = 12.sp, color = c.text3, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (right != null) {
            Spacer(Modifier.width(12.dp))
            right()
        } else if (chevron) {
            Spacer(Modifier.width(12.dp))
            FaIcon(R.drawable.ic_fa_chevron_right, tint = c.text3, size = 13.dp)
        }
    }
}

@Composable
private fun toneColors(tone: Tone): Pair<Color, Color> {
    val c = Ink.colors
    return when (tone) {
        Tone.Default -> c.accentDim to c.accent
        Tone.Info -> c.info.copy(alpha = 0.12f) to c.info
        Tone.Success -> c.success.copy(alpha = 0.12f) to c.success
        Tone.Danger -> c.danger.copy(alpha = 0.12f) to c.danger
    }
}

/** 36dp rounded tile; when [spinning] the glyph becomes a rotating spinner (`.spin-icon`). */
@Composable
private fun IconTile(@DrawableRes icon: Int, bg: Color, tint: Color, spinning: Boolean) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (spinning) {
            val angle by rememberInfiniteTransition(label = "spin").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
                label = "spinAngle",
            )
            FaIcon(R.drawable.ic_fa_spinner, tint = tint, size = 16.dp, modifier = Modifier.rotate(angle))
        } else {
            FaIcon(icon, tint = tint, size = 16.dp)
        }
    }
}

/** Account row: Google avatar (or the Google glyph), name, email, green "connected" dot. */
@Composable
private fun AccountRow(user: AuthUser) {
    val c = Ink.colors
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(c.border, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
            }
            .padding(horizontal = 2.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (user.picture.isNotBlank()) {
            AsyncImage(
                model = user.picture,
                contentDescription = user.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)),
            )
        } else {
            IconTile(R.drawable.ic_fa_brand_google, c.accentDim, c.accent, spinning = false)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            InkText(
                user.name.ifBlank { "Google Account" },
                size = 14.sp, weight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            InkText(user.email, size = 12.sp, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        // .sync-dot.online — 8dp dot with a 3dp soft ring
        Box(Modifier.size(14.dp).clip(CircleShape).background(c.success.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.success))
        }
    }
}

/** `.toggle` — 44×24 track, 20dp thumb that slides 20dp. */
@Composable
private fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Ink.colors
    val thumbX by animateDpAsState(if (checked) 22.dp else 2.dp, tween(200), label = "thumb")
    Box(
        Modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) c.accent else c.bgElevated)
            .border(1.dp, if (checked) c.accent else c.border, RoundedCornerShape(50))
            .tap { onChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = thumbX, y = 2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** `.seg-control.theme-seg.icon-only` — Light / Dark / GitHub. */
@Composable
private fun ThemeSeg(current: ThemeMode, onPick: (ThemeMode, String, Int) -> Unit) {
    val c = Ink.colors
    val options = listOf(
        Triple(ThemeMode.Light, "Light", R.drawable.ic_fa_r_sun),
        Triple(ThemeMode.Dark, "Dark", R.drawable.ic_fa_r_moon),
        Triple(ThemeMode.Github, "GitHub", R.drawable.ic_fa_brand_github),
    )
    SegContainer(radius = 18.dp, padding = 4.dp, gap = 4.dp) {
        options.forEach { (mode, label, icon) ->
            val on = current == mode
            Box(
                Modifier
                    .size(width = 40.dp, height = 36.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) c.accent else Color.Transparent)
                    .tap { onPick(mode, label, icon) },
                contentAlignment = Alignment.Center,
            ) {
                FaIcon(icon, tint = if (on) onAccent() else c.text2, size = 15.dp)
            }
        }
    }
}

/** `.seg-control` S / M / L. */
@Composable
private fun FontSizeSeg(current: EditorFontSize, onPick: (EditorFontSize) -> Unit) {
    val c = Ink.colors
    SegContainer(radius = 12.dp, padding = 2.dp, gap = 0.dp) {
        EditorFontSize.entries.forEach { size ->
            val on = current == size
            Box(
                Modifier
                    .heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) c.accent else Color.Transparent)
                    .tap { onPick(size) }
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                InkText(
                    size.label.take(1),
                    size = 13.sp,
                    weight = FontWeight.ExtraBold,
                    color = if (on) onAccent() else c.text2,
                )
            }
        }
    }
}

@Composable
private fun SegContainer(
    radius: androidx.compose.ui.unit.Dp,
    padding: androidx.compose.ui.unit.Dp,
    gap: androidx.compose.ui.unit.Dp,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val c = Ink.colors
    val shape = RoundedCornerShape(radius)
    Row(
        Modifier.clip(shape).background(c.bgInput).border(1.dp, c.border, shape).padding(padding),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Storage row — usage text, meter and the Notes / Books / Tags grid. */
@Composable
private fun StorageRow(s: SettingsState) {
    val c = Ink.colors
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(c.border, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
            }
            .padding(horizontal = 2.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(R.drawable.ic_fa_database, c.accentDim, c.accent, spinning = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            InkText("Local usage", size = 14.sp, weight = FontWeight.ExtraBold)
            Spacer(Modifier.height(1.dp))
            InkText("${s.storage.kb} KB / ${s.storage.mb} MB on this device", size = 12.sp, color = c.text3)

            // .storage-meter
            Spacer(Modifier.height(10.dp))
            val meterShape = RoundedCornerShape(50)
            Box(
                Modifier.fillMaxWidth().height(9.dp).clip(meterShape)
                    .background(c.bgElevated).border(1.dp, c.border, meterShape),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(s.storage.percent / 100f)
                        .clip(meterShape)
                        .background(Brush.horizontalGradient(listOf(c.accent, lerp(c.accent, Color.White, 0.35f)))),
                )
            }

            // .storage-count-grid
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountCell(s.noteCount, "Notes", Modifier.weight(1f))
                CountCell(s.notebookCount, "Books", Modifier.weight(1f))
                CountCell(s.tagCount, "Tags", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.width(12.dp))
        InkText(
            "${s.noteCount} note${if (s.noteCount != 1) "s" else ""}",
            size = 12.sp, weight = FontWeight.SemiBold, color = c.accent,
        )
    }
}

@Composable
private fun CountCell(n: Int, label: String, modifier: Modifier) {
    val c = Ink.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape).background(c.bgInput).border(1.dp, c.border, shape).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        InkText(n.toString(), size = 15.sp, weight = FontWeight.Bold, color = c.accent)
        Spacer(Modifier.height(2.dp))
        InkText(label, size = 11.sp, color = c.text3)
    }
}

/** `.modal-overlay.show` + `.modal` — bottom card over a dim scrim, Cancel / OK. */
@Composable
private fun ConfirmModal(
    title: String,
    sub: String,
    okLabel: String,
    danger: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val c = Ink.colors
    val shape = RoundedCornerShape(28.dp)
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f))
            .tap(onCancel)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 12.dp, end = 12.dp, top = 56.dp, bottom = 14.dp + navInset),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(c.bg)
                .border(1.dp, c.border, shape)
                .tap { /* swallow taps so the scrim doesn't dismiss */ }
                .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 20.dp),
        ) {
            InkText(title, family = Ink.serif, size = 21.sp, weight = FontWeight.Bold, lineHeight = 24.sp)
            Spacer(Modifier.height(8.dp))
            InkText(sub, size = 14.sp, color = c.text2, lineHeight = 20.sp)
            Spacer(Modifier.height(30.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModalBtn("Cancel", c.bgCard, c.text2, c.border, Modifier.weight(1f), onCancel)
                if (danger) ModalBtn(okLabel, c.danger, Color.White, c.danger, Modifier.weight(1f), onConfirm)
                else ModalBtn(okLabel, c.accent, onAccent(), c.accent, Modifier.weight(1f), onConfirm)
            }
        }
    }
}

@Composable
private fun ModalBtn(label: String, bg: Color, textColor: Color, border: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.heightIn(min = 52.dp).clip(shape).background(bg).border(1.dp, border, shape).tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        InkText(label, size = 15.sp, weight = FontWeight.Black, color = textColor)
    }
}
