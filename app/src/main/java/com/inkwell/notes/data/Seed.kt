package com.inkwell.notes.data

import com.inkwell.notes.util.countWords
import com.inkwell.notes.util.nowIso
import com.inkwell.notes.util.stripHtml

object Seed {
    fun welcomeNote(): Note {
        val now = nowIso()
        val content = "<h1>Welcome to Inkwell</h1>" +
            "<p>This is your first note. Tap the + button to write your own. " +
            "Long-press any note to select, pin or delete it.</p>" +
            "<h2>Good to know</h2>" +
            "<ul><li>Pin your favourite notes to keep them on top</li>" +
            "<li>Sort by modified, created, title or tag</li>" +
            "<li>Long-press to select several notes at once</li></ul>"
        return Note(
            id = "welcome-note",
            title = "Welcome to Inkwell",
            content = content,
            tags = listOf("welcome", "guide"),
            pinned = true,
            pinOrder = 0,
            createdAt = now,
            updatedAt = now,
            wordCount = countWords(stripHtml(content)),
        )
    }
}
