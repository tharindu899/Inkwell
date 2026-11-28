package com.inkwell.notes.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.BuildConfig
import com.inkwell.notes.data.InkStore
import com.inkwell.notes.model.FontSize
import com.inkwell.notes.model.ThemeMode
import com.inkwell.notes.ui.components.ConfirmDialog
import com.inkwell.notes.ui.components.InkSegmented
import com.inkwell.notes.ui.components.InkSwitch
import com.inkwell.notes.ui.components.InkTopBar
import com.inkwell.notes.ui.components.SettingsDivider
import com.inkwell.notes.ui.components.SettingsGroup
import com.inkwell.notes.ui.components.SettingsRow
import com.inkwell.notes.ui.components.ThemeTile
import com.inkwell.notes.ui.components.staggeredEntrance
import com.inkwell.notes.ui.theme.DisplayFont
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkDanger
import com.inkwell.notes.util.formatRelative
import kotlinx.coroutines.launch
import java.time.LocalDate

private class ConfirmSpec(
    val title: String,
    val text: String,
    val label: String,
    val danger: Boolean = true,
    val action: () -> Unit,
)

@Composable
fun SettingsScreen(
    store: InkStore,
    appVersion: String,
    onBack: () -> Unit,
    onManageTags: () -> Unit,
    onProfile: () -> Unit,
) {
    val c = Ink.colors
    val scope = rememberCoroutineScope()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var confirm by remember { mutableStateOf<ConfirmSpec?>(null) }
    val chevron: @Composable () -> Unit = { Icon(Icons.Rounded.ChevronRight, null, tint = c.subText) }
    val tagCount = store.tagCounts().size
    val kb = store.storageKb()
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { store.exportTo(ctx.contentResolver, uri) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { store.importFrom(ctx.contentResolver, uri) }
    }
    // Show the real "last backup" time from Drive when the screen opens.
    LaunchedEffect(store.user) { store.refreshBackupInfo() }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        InkTopBar("Settings", Modifier.padding(horizontal = 18.dp), onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = bottom + 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── Google Drive ──
            item(key = "drive") {
                SettingsGroup("Google Drive Sync", Modifier.staggeredEntrance(0)) {
                    SettingsRow(Icons.Rounded.Person, store.user?.name ?: "Google Account", store.user?.email, kind = 2)
                    SettingsDivider()
                    SettingsRow(
                        Icons.Rounded.History, "Last backup",
                        store.syncError ?: store.lastBackup?.let { formatRelative(it) } ?: "No backup yet",
                    )
                    SettingsDivider()
                    SettingsRow(
                        Icons.Rounded.CloudUpload, "Back up now", "Save all notes to your Google Drive", kind = 1,
                        onClick = { scope.launch { store.backup() } },
                        trailing = { if (store.backupBusy) Spinner() },
                    )
                    SettingsDivider()
                    SettingsRow(
                        Icons.Rounded.CloudDownload, "Restore from Drive", "Replace local data with your cloud backup", kind = 2,
                        onClick = {
                            confirm = ConfirmSpec(
                                "Restore from Drive?", "This replaces your local data with the cloud backup.",
                                "Restore", danger = false,
                            ) { scope.launch { store.restore() } }
                        },
                        trailing = { if (store.restoreBusy) Spinner() },
                    )
                }
            }
            // ── App update ──
            item(key = "update") {
                SettingsGroup("App Update", Modifier.staggeredEntrance(1)) {
                    SettingsRow(
                        Icons.Rounded.SystemUpdate, "Check for updates", "Find the latest APK from GitHub Releases", kind = 2,
                        onClick = { scope.launch { store.checkForUpdates(appVersion, BuildConfig.UPDATE_REPO) } },
                        trailing = chevron,
                    )
                }
            }
            // ── Appearance ──
            item(key = "appearance") {
                SettingsGroup("Appearance", Modifier.staggeredEntrance(2)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text("Theme", color = c.text, style = MaterialTheme.typography.titleMedium)
                            Text("Light, Dark, or GitHub", color = c.subText, style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ThemeMode.entries.forEach { m ->
                                ThemeTile(m, selected = store.themeMode == m, onClick = {
                                    store.themeMode = m
                                    store.toast.show("${m.label} theme on")
                                }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    SettingsDivider()
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text("Editor font size", color = c.text, style = MaterialTheme.typography.titleMedium)
                            Text("Applies to the note editor body text", color = c.subText, style = MaterialTheme.typography.bodyMedium)
                        }
                        InkSegmented(
                            items = FontSize.entries.map { it.label },
                            selected = store.fontSize.ordinal,
                            onSelect = { store.fontSize = FontSize.entries[it]; store.toast.show("Font size: ${FontSize.entries[it].label}") },
                        )
                        val size by animateFloatAsState(store.fontSize.sp, tween(250), label = "previewSize")
                        Text(
                            "The quick ink fox jumps over the lazy page.",
                            color = c.text,
                            style = TextStyle(
                                fontFamily = if (store.serifBody) DisplayFont else MaterialTheme.typography.bodyLarge.fontFamily,
                                fontSize = size.sp,
                                lineHeight = (size * 1.5f).sp,
                            ),
                        )
                    }
                }
            }
            // ── Editor ──
            item(key = "editor") {
                SettingsGroup("Editor", Modifier.staggeredEntrance(3)) {
                    SettingsRow(Icons.Rounded.Spellcheck, "Spell check", "Underline misspelled words",
                        trailing = { InkSwitch(store.spellcheck) { store.setPref("spellcheck", it) } })
                    SettingsDivider()
                    SettingsRow(Icons.Rounded.Save, "Auto-save", "Save notes automatically while typing",
                        trailing = { InkSwitch(store.autosave) { store.setPref("autosave", it) } })
                    SettingsDivider()
                    SettingsRow(Icons.Rounded.Code, "Markdown mode", "Enable Markdown shortcuts by default",
                        trailing = { InkSwitch(store.markdownMode) { store.setPref("markdownMode", it) } })
                    SettingsDivider()
                    SettingsRow(Icons.Rounded.TextFields, "Serif body font", "Use a serif font for notes",
                        trailing = { InkSwitch(store.serifBody) { store.setPref("serifBody", it) } })
                }
            }
            // ── Local data ──
            item(key = "data") {
                SettingsGroup("Local Data", Modifier.staggeredEntrance(4)) {
                    SettingsRow(Icons.Rounded.FileDownload, "Export all notes", "Download a JSON backup to this device",
                        onClick = { exportLauncher.launch("inkwell-backup-${LocalDate.now()}.json") })
                    SettingsDivider()
                    SettingsRow(Icons.Rounded.FileUpload, "Import notes", "Restore from a local JSON backup file",
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
                    SettingsDivider()
                    SettingsRow(
                        Icons.Rounded.Sell, "Manage tags",
                        "$tagCount unique tag${if (tagCount != 1) "s" else ""} across all notes",
                        onClick = onManageTags, trailing = chevron,
                    )
                }
            }
            // ── Storage ──
            item(key = "storage") {
                SettingsGroup("Storage", Modifier.staggeredEntrance(5)) {
                    SettingsRow(
                        Icons.Rounded.Storage, "Local usage",
                        "$kb KB / ${String.format("%.2f", kb / 1024f)} MB on this device",
                    )
                }
            }
            // ── Danger zone ──
            item(key = "danger") {
                SettingsGroup("Danger zone", Modifier.staggeredEntrance(6), labelColor = InkDanger) {
                    SettingsRow(
                        Icons.Rounded.DeleteSweep, "Delete all notes", "Permanently remove every note", danger = true,
                        onClick = {
                            confirm = ConfirmSpec(
                                "Delete all notes?", "Every note will be permanently removed. Notebooks will remain.", "Delete",
                            ) { store.deleteAllNotes() }
                        },
                    )
                    SettingsDivider()
                    SettingsRow(
                        Icons.Rounded.RestartAlt, "Reset all data", "Wipe all notes, notebooks, and settings", danger = true,
                        onClick = {
                            confirm = ConfirmSpec(
                                "Reset everything?",
                                "All notes, notebooks, settings, and profile will be wiped. This cannot be undone.",
                                "Reset",
                            ) { store.resetAll() }
                        },
                    )
                }
            }
            // ── About ──
            item(key = "about") {
                SettingsGroup("About", Modifier.staggeredEntrance(7)) {
                    SettingsRow(Icons.Rounded.Person, "Profile", "Your Google account & writing stats",
                        onClick = onProfile, trailing = chevron)
                    SettingsDivider()
                    SettingsRow(Icons.Rounded.Info, "Inkwell", "v$appVersion — Built with ♥")
                }
            }
        }
    }

    UpdateDialog(store, appVersion) { uriHandler.openUri(it) }

    confirm?.let { spec ->
        ConfirmDialog(
            title = spec.title,
            text = spec.text,
            confirmLabel = spec.label,
            danger = spec.danger,
            onConfirm = { confirm = null; spec.action() },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun UpdateDialog(store: InkStore, appVersion: String, open: (String) -> Unit) {
    val info = store.updateInfo ?: return
    ConfirmDialog(
        title = "Update available",
        text = "Inkwell v${info.version} is ready. You have v$appVersion.",
        confirmLabel = "Download",
        danger = false,
        onConfirm = { store.updateInfo = null; open(info.url) },
        onDismiss = { store.updateInfo = null },
    )
}

@Composable
private fun Spinner() {
    CircularProgressIndicator(Modifier.size(22.dp), color = Ink.colors.text, strokeWidth = 2.dp)
}
