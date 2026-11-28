package com.inkwell.notes.data

import com.inkwell.notes.model.Note
import com.inkwell.notes.model.Notebook

/** Temporary demo data for the UI. Replace with your real storage layer later. */
object SampleData {
    private val now get() = System.currentTimeMillis()
    private const val H = 3_600_000L

    val notebooks = listOf(
        Notebook("nb1", "Work", "#c9a96e", "briefcase", now - 400 * H),
        Notebook("nb2", "Personal", "#60ad82", "heart", now - 300 * H),
        Notebook("nb3", "Study", "#9b7de8", "graduation-cap", now - 200 * H),
    )

    fun notes(): List<Note> = listOf(
        Note("n1", "App launch plan",
            "<p>Finalize onboarding, record the demo and ship the beta build to the testers this Friday.</p>",
            listOf("work"), "nb1", pinned = true, pinOrder = 0, wordCount = 420, createdAt = now - 48 * H, updatedAt = now - 2 * H),
        Note("n2", "Checklist", "<p>Design review, update docs, reply to Sam, push the build.</p>",
            listOf("work"), "nb1", pinned = true, pinOrder = 1, wordCount = 60, createdAt = now - 30 * H, updatedAt = now - 5 * H),
        Note("n3", "Ideas", "<p>Voice notes with auto titles. Quick capture widget. Better tag suggestions while typing.</p>",
            listOf("ideas"), null, wordCount = 120, createdAt = now - 70 * H, updatedAt = now - 26 * H),
        Note("n4", "Journal", "<p>A calm Sunday evening. Slow start, strong finish.</p>",
            listOf("journal"), "nb2", wordCount = 310, createdAt = now - 100 * H, updatedAt = now - 50 * H),
        Note("n5", "Reading list", "<p>Three books to finish before December. Start with the shortest one.</p>",
            listOf("study"), "nb3", wordCount = 80, createdAt = now - 120 * H, updatedAt = now - 72 * H),
        Note("n6", "Late night thoughts", "<p>Write shorter. Ship sooner. Keep the interface quiet and let the words lead the way. Quiet apps win.</p>",
            listOf("ideas"), null, wordCount = 200, createdAt = now - 200 * H, updatedAt = now - 150 * H),
        Note("n7", "Groceries", "<p>Rice, eggs, lemons, coffee.</p>",
            emptyList(), "nb2", wordCount = 6, createdAt = now - 220 * H, updatedAt = now - 190 * H),
    )
}
