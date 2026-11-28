package com.inkwell.notes.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.inkwell.notes.R
import com.inkwell.notes.auth.AuthUser
import com.inkwell.notes.data.NotesStore
import com.inkwell.notes.data.model.Note
import com.inkwell.notes.ui.components.BottomNav
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.components.InnerTopBar
import com.inkwell.notes.ui.components.NavTab
import com.inkwell.notes.ui.components.tap
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.util.fmtNum
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Port of `src/pages/Profile.jsx`.
 *
 * Google account hero → Notes / Books / Words → writing streak → 14-week activity grid →
 * quick actions (Settings, My notebooks) → Sign out (with confirm modal).
 * This replaces the temporary "long-press the avatar to sign out" on Home.
 */
@Composable
fun ProfileScreen(
    user: AuthUser,
    onBack: () -> Unit,
    onNavTab: (NavTab) -> Unit,
    onSignOut: () -> Unit,
    onSettings: () -> Unit = {},
) {
    val c = Ink.colors
    val context = LocalContext.current
    val store = remember { NotesStore(context) }
    val notes = remember { store.getNotes() }
    val stats = remember { store.getStats() }
    val (streak, best) = remember(notes) { calcStreak(notes) }
    val cells = remember(notes) { buildActivityLevels(notes) }

    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            InnerTopBar(title = "Profile", onBack = onBack)

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = Ink.navHeight + navInset + 14.dp),
            ) {
                Hero(user)

                // .p-stats
                StatsGrid(stats.notes.toString(), stats.notebooks.toString(), fmtNum(stats.words))
                Spacer(Modifier.height(16.dp))

                StreakCard(streak, best)
                Spacer(Modifier.height(10.dp))

                // .settings-group with 14px 18px padding — activity grid
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                    InkText(
                        "NOTES WRITTEN — LAST 14 WEEKS",
                        size = 11.sp,
                        weight = FontWeight.ExtraBold,
                        color = c.text3,
                        letterSpacing = 0.6.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    ActivityGrid(cells)
                }

                Spacer(Modifier.height(16.dp))
                SettingRow(
                    icon = R.drawable.ic_fa_gear,
                    label = "Settings",
                    sub = "Theme, font size, Drive sync",
                    onClick = onSettings,
                )
                SettingRow(
                    icon = R.drawable.ic_fa_book_open,
                    label = "My notebooks",
                    sub = "View and manage notebooks",
                    onClick = { onNavTab(NavTab.Notebooks) },
                )

                Spacer(Modifier.height(8.dp))
                SettingRow(
                    icon = R.drawable.ic_fa_right_from_bracket,
                    label = "Sign out",
                    sub = "Signed in as ${user.email}",
                    danger = true,
                    chevron = false,
                    onClick = { confirmSignOut = true },
                )
            }
        }

        BottomNav(
            active = null,
            onSelect = onNavTab,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (confirmSignOut) {
            SignOutModal(onCancel = { confirmSignOut = false }, onConfirm = onSignOut)
        }
    }
}

/** .profile-hero — avatar (with Google badge), name, email, "Google Account". */
@Composable
private fun Hero(user: AuthUser) {
    val c = Ink.colors
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // .avatar-wrap — 80dp
        Box(Modifier.size(80.dp)) {
            if (user.picture.isNotBlank()) {
                AsyncImage(
                    model = user.picture,
                    contentDescription = user.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(80.dp).clip(CircleShape).border(2.5.dp, c.accent, CircleShape),
                )
            } else {
                Box(
                    Modifier.size(80.dp).clip(CircleShape).background(c.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    InkText(
                        user.name.trim().take(1).uppercase().ifEmpty { "U" },
                        family = Ink.serif,
                        size = 32.sp,
                        weight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            // .google-badge — 22dp, bottom 2 / right 2
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-2).dp, y = (-2).dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(c.bgCard)
                    .border(1.5.dp, c.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        InkText(
            user.name.ifBlank { "Inkwell User" },
            family = Ink.serif,
            size = 20.sp,
            weight = FontWeight.Bold,
            align = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        if (user.email.isNotBlank()) {
            InkText(user.email, size = 13.sp, color = c.text2, align = TextAlign.Center)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FaIcon(R.drawable.ic_fa_brand_google, tint = c.text3, size = 10.dp)
            Spacer(Modifier.width(4.dp))
            InkText("Google Account", size = 11.sp, color = c.text3)
        }
    }
}

/** .p-stats — three cells separated by 1dp lines inside a rounded border. */
@Composable
private fun StatsGrid(notes: String, books: String, words: String) {
    val c = Ink.colors
    val shape = RoundedCornerShape(Ink.radius)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(c.border)
            .border(1.dp, c.border, shape),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        listOf(notes to "Notes", books to "Books", words to "Words").forEach { (num, label) ->
            Column(
                Modifier.weight(1f).background(c.bgCard).padding(horizontal = 10.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                InkText(num, family = Ink.serif, size = 20.sp, weight = FontWeight.Bold, color = c.accent)
                Spacer(Modifier.height(2.dp))
                InkText(label.uppercase(), size = 10.sp, color = c.text3, letterSpacing = 0.5.sp)
            }
        }
    }
}

/** .streak-card */
@Composable
private fun StreakCard(streak: Int, best: Int) {
    val c = Ink.colors
    val shape = RoundedCornerShape(Ink.radius)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.bgCard)
            .border(1.dp, c.border, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkText("\uD83D\uDD25", size = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            InkText(streak.toString(), family = Ink.serif, size = 22.sp, weight = FontWeight.Bold, color = c.accent, lineHeight = 22.sp)
            Spacer(Modifier.height(2.dp))
            InkText("day writing streak", size = 12.sp, color = c.text3)
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            InkText("Longest", size = 11.sp, color = c.text3)
            InkText(best.toString(), family = Ink.serif, size = 16.sp, weight = FontWeight.Bold, color = c.text2)
        }
    }
}

/** .activity-grid — 14 columns × 7 rows, filled column by column (oldest day top-left). */
@Composable
private fun ActivityGrid(levels: List<Int>) {
    val c = Ink.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (col in 0 until 14) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (row in 0 until 7) {
                    val lvl = levels.getOrElse(col * 7 + row) { 0 }
                    val fill = when (lvl) {
                        0 -> c.bgElevated
                        1 -> lerp(c.bgElevated, c.accent, 0.30f)
                        2 -> lerp(c.bgElevated, c.accent, 0.55f)
                        3 -> lerp(c.bgElevated, c.accent, 0.80f)
                        else -> c.accent
                    }
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(3.dp)).background(fill))
                }
            }
        }
    }
}

/** .si row — 36dp icon tile, label + sub, chevron; 1dp divider under it. */
@Composable
private fun SettingRow(
    icon: Int,
    label: String,
    sub: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    chevron: Boolean = true,
) {
    val c = Ink.colors
    val tile = if (danger) c.danger.copy(alpha = 0.12f) else c.accentDim
    val tint = if (danger) c.danger else c.accent
    Row(
        Modifier
            .fillMaxWidth()
            .tap(onClick)
            .drawBehind {
                drawRect(c.border, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
            }
            .padding(horizontal = 2.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tile),
            contentAlignment = Alignment.Center,
        ) {
            FaIcon(icon, tint = tint, size = 16.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            InkText(label, size = 14.sp, weight = FontWeight.ExtraBold, color = if (danger) c.danger else c.text1)
            Spacer(Modifier.height(1.dp))
            InkText(sub, size = 12.sp, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (chevron) {
            Spacer(Modifier.width(12.dp))
            FaIcon(R.drawable.ic_fa_chevron_right, tint = c.text3, size = 13.dp)
        }
    }
}

/** "Sign out?" confirm — bottom card over a dim scrim (`.modal-overlay.show` + `.modal`). */
@Composable
private fun SignOutModal(onCancel: () -> Unit, onConfirm: () -> Unit) {
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
            InkText("Sign out?", family = Ink.serif, size = 21.sp, weight = FontWeight.Bold, lineHeight = 24.sp)
            Spacer(Modifier.height(8.dp))
            InkText(
                "Your notes are saved on this device. Sign back in anytime to sync with Google Drive.",
                size = 14.sp,
                color = c.text2,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(30.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModalBtn("Cancel", c.bgCard, c.text2, c.border, Modifier.weight(1f), onCancel)
                ModalBtn("Sign out", c.danger, Color.White, c.danger, Modifier.weight(1f), onConfirm)
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

// ─── data helpers (web: buildActivityCells / calcStreak, UTC day keys like toISOString) ───

private fun dayKey(daysAgo: Int): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(cal.time)
}

/** 98 levels (0..4), oldest first. */
private fun buildActivityLevels(notes: List<Note>): List<Int> {
    val counts = notes
        .map { (it.updatedAt.ifEmpty { it.createdAt }).take(10) }
        .filter { it.isNotEmpty() }
        .groupingBy { it }
        .eachCount()
    val max = maxOf(1, counts.values.maxOrNull() ?: 0)
    return List(98) { i ->
        val v = counts[dayKey(97 - i)] ?: 0
        val r = v.toFloat() / max
        when {
            v == 0 -> 0
            r < 0.25f -> 1
            r < 0.5f -> 2
            r < 0.75f -> 3
            else -> 4
        }
    }
}

/** (current streak, longest run) over the last 365 days. */
private fun calcStreak(notes: List<Note>): Pair<Int, Int> {
    val days = notes.map { it.updatedAt.take(10) }.filter { it.isNotEmpty() }.toSet()
    var streak = 0
    var best = 0
    var cur = 0
    for (i in 0 until 365) {
        if (dayKey(i) in days) {
            cur++
            best = maxOf(best, cur)
            if (i == 0 || streak > 0) streak = cur
        } else if (i > 0) {
            cur = 0
        }
    }
    return streak to best
}
