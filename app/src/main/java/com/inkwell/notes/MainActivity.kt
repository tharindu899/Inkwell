package com.inkwell.notes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.inkwell.notes.reminder.PendingOpen
import com.inkwell.notes.reminder.Reminders
import com.inkwell.notes.ui.InkwellApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Reminders.ensureChannel(this)
        // Alarms are kept in memory by the system: put every future reminder back (cheap, idempotent).
        Thread { runCatching { Reminders.rescheduleAll(applicationContext) } }.start()
        handleIntent(intent)
        setContent { InkwellApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** A tapped reminder notification carries the note id; InkwellApp opens it. */
    private fun handleIntent(intent: Intent?) {
        val id = intent?.getStringExtra(Reminders.EXTRA_NOTE_ID) ?: return
        intent.removeExtra(Reminders.EXTRA_NOTE_ID)
        PendingOpen.noteId = id
    }
}
