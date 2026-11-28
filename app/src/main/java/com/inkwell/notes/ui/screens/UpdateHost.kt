package com.inkwell.notes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inkwell.notes.R
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.components.InkToast
import com.inkwell.notes.ui.components.onAccent
import com.inkwell.notes.ui.components.tap
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.update.UpdateViewModel

/**
 * App-level port of `UpdateChecker.jsx`: checks GitHub on launch / resume, shows the
 * "New APK update available" or "You are already updated" sheet, and its own toast.
 * Settings → "Check for updates" talks to the same (activity-scoped) [UpdateViewModel].
 * Put it last inside the root Box so it draws over everything.
 */
@Composable
fun BoxScope.UpdateHost(vm: UpdateViewModel = viewModel()) {
    val c = Ink.colors
    val s by vm.state.collectAsStateWithLifecycle()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Launch + every return to the app (web: App 'resume' listener).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.checkAuto() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val sheet = s.sheet
    if (sheet != null) {
        BackHandler(enabled = !s.downloading) { vm.dismiss() }
        val shape = RoundedCornerShape(28.dp)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.44f))
                .tap { vm.dismiss() }
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
                    .tap { /* swallow */ }
                    .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // .update-close
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .alpha(if (s.downloading) 0.4f else 1f)
                            .clip(CircleShape)
                            .background(c.bgCard)
                            .tap { vm.dismiss() },
                        contentAlignment = Alignment.Center,
                    ) { FaIcon(R.drawable.ic_fa_xmark, tint = c.text2, size = 12.dp) }
                }

                // .update-hero — icon + version pill
                val heroTint = if (sheet.isUpdate) c.accent else c.success
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(heroTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    FaIcon(
                        if (sheet.isUpdate) R.drawable.ic_fa_download else R.drawable.ic_fa_circle_check,
                        tint = heroTint, size = 24.dp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.clip(RoundedCornerShape(50)).background(heroTint.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    InkText(
                        if (sheet.isUpdate) versionLabel(sheet.version) else "Up to date",
                        size = 12.sp, weight = FontWeight.ExtraBold, color = heroTint,
                    )
                }

                Spacer(Modifier.height(12.dp))
                InkText(
                    if (sheet.isUpdate) "New APK update available" else "You are already updated",
                    family = Ink.serif, size = 21.sp, weight = FontWeight.Bold, align = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                InkText(
                    if (sheet.isUpdate) "Latest ${versionLabel(sheet.version)} · Current ${versionLabel(vm.localVersion)}"
                    else "Current ${versionLabel(vm.localVersion)} matches latest ${versionLabel(sheet.version)}",
                    size = 13.sp, color = c.text2, align = TextAlign.Center,
                )

                // .update-meta-grid
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetaCell("Size", UpdateViewModel.fmtMb(sheet.apkSize), Modifier.weight(1f))
                    MetaCell("Downloads", sheet.downloadCount.toString(), Modifier.weight(1f))
                    MetaCell("Repo", sheet.repo, Modifier.weight(1.8f))
                }

                // .update-progress-box
                if (s.downloading || s.done) {
                    Spacer(Modifier.height(12.dp))
                    val box = RoundedCornerShape(14.dp)
                    Column(
                        Modifier.fillMaxWidth().clip(box).background(c.bgInput).border(1.dp, c.border, box).padding(12.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            InkText(if (s.done) "Download complete" else "Downloading APK…", size = 12.sp, color = c.text2)
                            InkText(if (sheet.apkSize > 0 || s.percent > 0) "${s.percent}%" else "…", size = 12.sp, weight = FontWeight.ExtraBold, color = c.accent)
                        }
                        Spacer(Modifier.height(8.dp))
                        val bar = RoundedCornerShape(50)
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(bar).background(c.bgElevated)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(s.percent / 100f).clip(bar).background(c.accent))
                        }
                        Spacer(Modifier.height(6.dp))
                        InkText(s.progressLabel.ifBlank { "Preparing download…" }, size = 11.sp, color = c.text3)
                    }
                }

                // .update-notes
                if (sheet.isUpdate && sheet.notes.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    val box = RoundedCornerShape(14.dp)
                    Column(
                        Modifier.fillMaxWidth().clip(box).background(c.bgCard).border(1.dp, c.border, box).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        sheet.notes.forEach { line ->
                            Row {
                                InkText("•", size = 12.sp, color = c.accent)
                                Spacer(Modifier.width(8.dp))
                                InkText(line, size = 12.sp, color = c.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                // .update-current-box
                if (!sheet.isUpdate) {
                    Spacer(Modifier.height(12.dp))
                    val box = RoundedCornerShape(14.dp)
                    Row(
                        Modifier.fillMaxWidth().clip(box).background(c.success.copy(alpha = 0.10f)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FaIcon(R.drawable.ic_fa_circle_check, tint = c.success, size = 16.dp)
                        InkText(
                            "No download needed. Your installed APK is the latest release.",
                            size = 12.sp, color = c.text2, modifier = Modifier.weight(1f),
                        )
                    }
                }

                // actions
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SheetBtn(
                        if (sheet.isUpdate) "Later" else "Close",
                        c.bgCard, c.text2, c.border, null, Modifier.weight(1f).alpha(if (s.downloading) 0.5f else 1f),
                    ) { vm.dismiss() }
                    if (sheet.isUpdate) {
                        SheetBtn(
                            if (s.downloading) "Downloading…" else "Install",
                            c.accent, onAccent(), c.accent,
                            if (s.downloading) R.drawable.ic_fa_spinner else R.drawable.ic_fa_download,
                            Modifier.weight(1f).alpha(if (s.downloading) 0.6f else 1f),
                        ) { vm.install() }
                    } else {
                        SheetBtn("Done", c.accent, onAccent(), c.accent, R.drawable.ic_fa_circle_check, Modifier.weight(1f)) { vm.dismiss() }
                    }
                }
            }
        }
    }

    // Toast sits above the bottom nav like every other page's toast.
    InkToast(
        toast = s.toast,
        onAction = {},
        onDismiss = vm::dismissToast,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 17.dp, end = 17.dp, bottom = Ink.navHeight + navInset + 12.dp),
    )
}

private fun versionLabel(v: String): String {
    val clean = v.replace(Regex("^[vV]"), "").trim()
    return if (clean.isEmpty()) "latest" else "v$clean"
}

@Composable
private fun MetaCell(label: String, value: String, modifier: Modifier) {
    val c = Ink.colors
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.clip(shape).background(c.bgInput).border(1.dp, c.border, shape).padding(horizontal = 10.dp, vertical = 8.dp)) {
        InkText(label, size = 11.sp, color = c.text3)
        Spacer(Modifier.height(2.dp))
        InkText(value, size = 13.sp, weight = FontWeight.ExtraBold, color = c.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SheetBtn(
    label: String,
    bg: Color,
    textColor: Color,
    border: Color,
    icon: Int?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.heightIn(min = 52.dp).clip(shape).background(bg).border(1.dp, border, shape).tap(onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            FaIcon(icon, tint = textColor, size = 14.dp)
            Spacer(Modifier.width(6.dp))
        }
        InkText(label, size = 15.sp, weight = FontWeight.Black, color = textColor)
    }
}
