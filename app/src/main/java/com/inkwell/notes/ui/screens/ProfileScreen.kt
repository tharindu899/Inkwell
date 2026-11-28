package com.inkwell.notes.ui.screens

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.inkwell.notes.data.InkStore
import com.inkwell.notes.ui.components.ActivityGrid
import com.inkwell.notes.ui.components.ConfirmDialog
import com.inkwell.notes.ui.components.GoogleLogo
import com.inkwell.notes.ui.components.InkTopBar
import com.inkwell.notes.ui.components.SettingsGroup
import com.inkwell.notes.ui.components.SettingsRow
import com.inkwell.notes.ui.components.StatCard
import com.inkwell.notes.ui.components.staggeredEntrance
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.util.activityLevels
import com.inkwell.notes.util.writingStreaks

@Composable
fun ProfileScreen(
    store: InkStore,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onNotebooks: () -> Unit,
    onSignOut: () -> Unit,
) {
    val c = Ink.colors
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val user = store.user
    val (streak, best) = writingStreaks(store.notes)
    val levels = activityLevels(store.notes)
    var confirmSignOut by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        InkTopBar("Profile", Modifier.padding(horizontal = 18.dp), onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = bottom + 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hero") {
                Column(Modifier.fillMaxWidth().staggeredEntrance(0), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        Box(
                            Modifier.size(88.dp).clip(CircleShape).background(c.inverse),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                (user?.name?.firstOrNull() ?: 'U').uppercase(),
                                color = c.onInverse,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(28.dp).clip(CircleShape).background(c.surface).border(1.dp, c.border, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { GoogleLogo(14.dp) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(user?.name ?: "Inkwell User", color = c.text, style = MaterialTheme.typography.headlineSmall)
                    Text(user?.email ?: "", color = c.subText, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.clip(CircleShape).background(c.surface).border(1.dp, c.border, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GoogleLogo(12.dp)
                        Text("Google Account", color = c.subText, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            item(key = "stats") {
                Row(Modifier.fillMaxWidth().staggeredEntrance(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(store.notes.size, "Notes", Modifier.weight(1f))
                    StatCard(store.notebooks.size, "Books", Modifier.weight(1f))
                    StatCard(store.totalWords, "Words", Modifier.weight(1f))
                }
            }
            item(key = "streak") { StreakCard(streak, best, Modifier.staggeredEntrance(2)) }
            item(key = "activity") {
                val shape = RoundedCornerShape(24.dp)
                Column(
                    Modifier.fillMaxWidth().staggeredEntrance(3).clip(shape).background(c.surface).border(1.dp, c.border, shape).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Notes written — last 14 weeks", color = c.subText, style = MaterialTheme.typography.labelMedium)
                    ActivityGrid(levels)
                }
            }
            item(key = "actions") {
                SettingsGroup("Quick actions", Modifier.staggeredEntrance(4)) {
                    SettingsRow(
                        Icons.Rounded.Settings, "Settings", "Theme, font size, Drive sync", onClick = onSettings,
                        trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = c.subText) },
                    )
                    com.inkwell.notes.ui.components.SettingsDivider()
                    SettingsRow(
                        Icons.Rounded.MenuBook, "My notebooks", "View and manage notebooks", onClick = onNotebooks,
                        trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = c.subText) },
                    )
                }
            }
            item(key = "signout") {
                SettingsGroup("Account", Modifier.staggeredEntrance(5)) {
                    SettingsRow(
                        Icons.AutoMirrored.Rounded.Logout, "Sign out", "Signed in as ${user?.email ?: ""}",
                        danger = true, onClick = { confirmSignOut = true },
                    )
                }
            }
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            text = "Your notes are saved on this device. Sign back in anytime to sync with Google Drive.",
            confirmLabel = "Sign out",
            onConfirm = { confirmSignOut = false; onSignOut() },
            onDismiss = { confirmSignOut = false },
        )
    }
}

@Composable
private fun StreakCard(streak: Int, best: Int, modifier: Modifier = Modifier) {
    val p = Ink.colors.pastels[0]
    val shownStreak by animateIntAsState(streak, tween(900), label = "streak")
    val shownBest by animateIntAsState(best, tween(900), label = "best")
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(p.container).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(p.title.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.LocalFireDepartment, null, tint = p.title, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(shownStreak.toString(), color = p.title, style = MaterialTheme.typography.headlineMedium)
            Text("day writing streak", color = p.body, style = MaterialTheme.typography.bodyMedium)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Longest", color = p.body, style = MaterialTheme.typography.labelMedium)
            Text(shownBest.toString(), color = p.title, style = MaterialTheme.typography.titleLarge)
        }
    }
}
