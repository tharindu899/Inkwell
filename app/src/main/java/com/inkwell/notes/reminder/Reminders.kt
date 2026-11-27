package com.inkwell.notes.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.inkwell.notes.MainActivity
import com.inkwell.notes.R
import com.inkwell.notes.data.NotesStore
import com.inkwell.notes.editor.HtmlCodec

/**
 * Note reminders: an alarm-clock style alarm (`AlarmManager.setAlarmClock` — exact, survives Doze, needs no
 * special permission, shows the alarm icon in the status bar) that posts a loud heads-up notification
 * with the phone's alarm sound, vibration, and Snooze / Dismiss buttons. Tap = open the note.
 */
object Reminders {
    const val CHANNEL_ID = "note_reminders_v1"
    const val EXTRA_NOTE_ID = "openNoteId"
    const val ACTION_FIRE = "com.inkwell.notes.REMINDER_FIRE"
    const val ACTION_SNOOZE = "com.inkwell.notes.REMINDER_SNOOZE"
    const val ACTION_DISMISS = "com.inkwell.notes.REMINDER_DISMISS"
    const val SNOOZE_MS = 10 * 60_000L
    private const val MISSED_WINDOW_MS = 24 * 60 * 60_000L

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val sound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ch = NotificationChannel(CHANNEL_ID, "Note reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alarms you set on notes"
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 800)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableLights(true)
        }
        nm.createNotificationChannel(ch)
    }

    fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    private fun receiverIntent(ctx: Context, action: String, noteId: String) =
        Intent(ctx, ReminderReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("inkwell://reminder/$noteId")
            putExtra(EXTRA_NOTE_ID, noteId)
        }

    private fun pending(ctx: Context, action: String, noteId: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, noteId.hashCode() + code, receiverIntent(ctx, action, noteId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openNote(ctx: Context, noteId: String): PendingIntent =
        PendingIntent.getActivity(
            ctx, noteId.hashCode() + 9,
            Intent(ctx, MainActivity::class.java).apply {
                putExtra(EXTRA_NOTE_ID, noteId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Sets (or replaces) the alarm for [noteId] at [atMillis]. */
    fun schedule(ctx: Context, noteId: String, atMillis: Long): Boolean {
        return try {
            ensureChannel(ctx)
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
            val fire = pending(ctx, ACTION_FIRE, noteId, 1)
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, openNote(ctx, noteId)), fire)
            } catch (_: SecurityException) {
                // Some ROMs (MIUI etc.) refuse alarm-clock alarms: fall back to an inexact alarm instead of crashing.
                am.set(AlarmManager.RTC_WAKEUP, atMillis, fire)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun cancel(ctx: Context, noteId: String) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pending(ctx, ACTION_FIRE, noteId, 1))
        cancelNotification(ctx, noteId)
    }

    fun cancelNotification(ctx: Context, noteId: String) {
        ctx.getSystemService(NotificationManager::class.java)?.cancel(noteId.hashCode())
    }

    /** App start / boot / after a restore: alarms live in memory, so put every future reminder back. */
    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        val store = NotesStore(ctx)
        for (n in store.getNotes()) {
            val at = n.reminderAt ?: continue
            when {
                at > now -> schedule(ctx, n.id, at)
                now - at <= MISSED_WINDOW_MS -> fire(ctx, n.id) // phone was off at that time
                else -> store.saveNote(n.copy(reminderAt = null))
            }
        }
    }

    fun snooze(ctx: Context, noteId: String) {
        val at = System.currentTimeMillis() + SNOOZE_MS
        val store = NotesStore(ctx)
        store.getNote(noteId)?.let { store.saveNote(it.copy(reminderAt = at)) }
        schedule(ctx, noteId, at)
    }

    /** The alarm went off: loud heads-up notification, then the note's reminder is spent. */
    fun fire(ctx: Context, noteId: String) {
        val store = NotesStore(ctx)
        val note = store.getNote(noteId) ?: return
        store.saveNote(note.copy(reminderAt = null))
        ensureChannel(ctx)
        if (!canNotify(ctx)) return

        val title = note.title.ifBlank { "Untitled" }
        val body = runCatching { HtmlCodec.previewText(note.content) }.getOrDefault("").trim()
            .replace(Regex("\\s+"), " ").take(140).ifEmpty { "Reminder" }

        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fa_bell)
            .setContentTitle("⏰ $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setTimeoutAfter(2 * 60_000L)
            .setContentIntent(openNote(ctx, noteId))
            .addAction(0, "Snooze 10 min", pending(ctx, ACTION_SNOOZE, noteId, 2))
            .addAction(0, "Dismiss", pending(ctx, ACTION_DISMISS, noteId, 3))
            .build()
        // Keep ringing until it is dealt with (like an alarm clock), not a single beep.
        n.flags = n.flags or Notification.FLAG_INSISTENT
        runCatching {
            ctx.getSystemService(NotificationManager::class.java)?.notify(noteId.hashCode(), n)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra(Reminders.EXTRA_NOTE_ID) ?: return
        when (intent.action) {
            Reminders.ACTION_SNOOZE -> { Reminders.cancelNotification(ctx, id); Reminders.snooze(ctx, id) }
            Reminders.ACTION_DISMISS -> Reminders.cancelNotification(ctx, id)
            else -> Reminders.fire(ctx, id)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.MY_PACKAGE_REPLACED") {
            Reminders.rescheduleAll(ctx)
        }
    }
}

/** Set by MainActivity when a reminder notification is tapped; InkwellApp opens that note and clears it. */
object PendingOpen {
    var noteId by mutableStateOf<String?>(null)
}
