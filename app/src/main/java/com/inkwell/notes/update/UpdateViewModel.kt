package com.inkwell.notes.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inkwell.notes.BuildConfig
import com.inkwell.notes.R
import com.inkwell.notes.ui.components.ToastState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** The sheet's content (web `update` state). */
data class UpdateSheet(
    val isUpdate: Boolean,
    val version: String,
    val apkName: String,
    val apkSize: Long,
    val apkUrl: String?,
    val downloadCount: Int,
    val repo: String,
    val notes: List<String>,
)

data class UpdateState(
    val sheet: UpdateSheet? = null,
    val downloading: Boolean = false,
    val done: Boolean = false,
    val percent: Int = 0,
    val progressLabel: String = "",
    val toast: ToastState? = null,
)

/**
 * Port of `UpdateChecker.jsx` logic. Activity-scoped so Settings ("Check for updates")
 * and the app-level host (auto check on launch / resume) share one instance.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val installer = ApkInstaller(application)
    private val repo = BuildConfig.GITHUB_REPO
    val localVersion: String = BuildConfig.VERSION_NAME

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var checking = false
    private var dismissedVersion: String? = null
    private var lastAutoCheck = 0L
    private var downloadJob: Job? = null

    init {
        installer.cleanup() // temp APK from the previous install
    }

    /** Settings → "Check for updates" */
    fun checkManual() = check(manual = true)

    /** App start / resume. Throttled so a quick app switch doesn't hit the GitHub rate limit. */
    fun checkAuto() {
        val now = System.currentTimeMillis()
        if (now - lastAutoCheck < AUTO_CHECK_GAP_MS) return
        lastAutoCheck = now
        check(manual = false)
    }

    private fun check(manual: Boolean) {
        if (checking || _state.value.downloading) return
        checking = true
        viewModelScope.launch {
            try {
                if (manual) toast("Checking GitHub release…", R.drawable.ic_fa_rotate)
                val release = UpdateApi.fetchLatest(repo)
                if (release == null) {
                    if (manual) toast("No published GitHub release found", R.drawable.ic_fa_circle_info)
                    return@launch
                }
                val newer = UpdateApi.isNewer(release.version, localVersion)
                if (!manual && dismissedVersion == release.version) return@launch
                if (newer || manual) {
                    showSheet(release, newer)
                    if (manual) {
                        if (newer) toast("Update available", R.drawable.ic_fa_download)
                        else toast("You are already updated", R.drawable.ic_fa_circle_check)
                    }
                }
            } catch (e: Exception) {
                if (manual) {
                    val status = (e as? GithubHttpException)?.status
                    val msg = when (status) {
                        404 -> "Repo not found: $repo. Make repo public and publish an APK release."
                        403 -> "GitHub rate-limited — try again later"
                        else -> "Update check failed (${status ?: "network error"})"
                    }
                    toast(msg, R.drawable.ic_fa_circle_exclamation)
                }
            } finally {
                checking = false
            }
        }
    }

    private fun showSheet(r: ReleaseInfo, newer: Boolean) {
        _state.update {
            it.copy(
                sheet = UpdateSheet(
                    isUpdate = newer,
                    version = r.version,
                    apkName = r.apkName ?: "Open GitHub Release",
                    apkSize = r.apkSize,
                    apkUrl = r.apkUrl,
                    downloadCount = r.downloadCount,
                    repo = repo,
                    notes = releaseLines(r.body),
                ),
                downloading = false,
                done = false,
                percent = 0,
                progressLabel = "",
            )
        }
        dismissedVersion = null
    }

    fun dismiss() {
        val s = _state.value
        if (s.downloading) return
        dismissedVersion = s.sheet?.version
        _state.update { it.copy(sheet = null) }
    }

    /** "Install" button: download inside the app, then open the system installer. */
    fun install() {
        val sheet = _state.value.sheet ?: return
        if (!sheet.isUpdate || _state.value.downloading) return
        val url = sheet.apkUrl
        if (url == null) {
            toast("This release has no APK file attached", R.drawable.ic_fa_circle_exclamation)
            return
        }
        if (!installer.canInstall()) {
            installer.openInstallPermissionSettings()
            toast("Allow “Install unknown apps” for Inkwell, then tap Install again", R.drawable.ic_fa_circle_info)
            return
        }
        _state.update {
            it.copy(
                downloading = true, done = false, percent = 0,
                progressLabel = if (sheet.apkSize > 0) "0 MB / ${fmtMb(sheet.apkSize)}" else "Preparing download…",
            )
        }
        downloadJob = viewModelScope.launch {
            try {
                var lastPct = -1
                val file = installer.download(url, sheet.apkName, sheet.apkSize) { loaded, total ->
                    val pct = if (total > 0) minOf(100, (loaded * 100 / total).toInt()) else 0
                    if (pct != lastPct) { // only publish when the number changes
                        lastPct = pct
                        _state.update {
                            it.copy(
                                percent = pct,
                                progressLabel = "${fmtMb(loaded)} / ${if (total > 0) fmtMb(total) else "…"}",
                            )
                        }
                    }
                }
                _state.update { it.copy(percent = 100, done = true, progressLabel = "Installer opened — temporary APK will be deleted") }
                installer.install(file)
                toast("Android installer opened", R.drawable.ic_fa_circle_check)
            } catch (e: Exception) {
                _state.update { it.copy(percent = 0, progressLabel = "Install failed") }
                toast("In-app install failed — check unknown-app install permission", R.drawable.ic_fa_circle_exclamation)
            } finally {
                _state.update { it.copy(downloading = false) }
            }
        }
    }

    fun dismissToast() {
        _state.update { it.copy(toast = null) }
    }

    private fun toast(message: String, icon: Int) {
        _state.update { it.copy(toast = ToastState(System.nanoTime(), message, icon)) }
    }

    companion object {
        private const val AUTO_CHECK_GAP_MS = 10 * 60 * 1000L

        /** `fmtMb()` */
        fun fmtMb(bytes: Long): String =
            if (bytes <= 0) "—" else String.format(Locale.US, if (bytes > 10L * 1024 * 1024) "%.1f MB" else "%.2f MB", bytes / 1024.0 / 1024.0)

        /** First 3 short, useful lines of the release body (`cleanReleaseLine` + filters). */
        fun releaseLines(body: String): List<String> =
            body.split("\n")
                .map { it.replace("`", "").replace(Regex("\\[(.*?)]\\((.*?)\\)"), "$1").replace(Regex("^[-*#>\\s]+"), "").trim() }
                .filter { it.isNotEmpty() }
                .filterNot { Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(it) }
                .filterNot { Regex("commit|actions/runs|github\\.com", RegexOption.IGNORE_CASE).containsMatchIn(it) }
                .map { if (it.length > 70) it.take(70) + "…" else it }
                .take(3)
    }
}
