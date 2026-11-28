package com.inkwell.notes.data

import com.inkwell.notes.model.Note
import com.inkwell.notes.util.countWords

/** Created on first launch, like the web app's welcome note. */
object WelcomeNote {
    fun create(now: Long = System.currentTimeMillis()): Note {
        val html = "<h1>Welcome to Inkwell</h1>" +
            "<p>Your notes live on this device and can be backed up to your own Google Drive.</p>" +
            "<h2>Quick tips</h2>" +
            "<ul><li>Tap the + button to write a note, create a tag or a notebook.</li>" +
            "<li>Pin important notes so they stay at the top of Home.</li>" +
            "<li>Use Search to find anything by title, content or tag.</li></ul>"
        return Note(
            id = "welcome-note",
            title = "Welcome to Inkwell",
            content = html,
            tags = listOf("welcome", "guide"),
            pinned = true,
            pinOrder = 0,
            wordCount = countWords(html),
            createdAt = now,
            updatedAt = now,
        )
    }
}
