package com.inkwell.notes.editor

/**
 * Editor Part 2 — undo / redo stack of whole-document snapshots.
 *
 * Typing is coalesced: keystrokes closer than [BURST_MS] apart share one undo step.
 * Every formatting action (bold, heading, align …) is its own step.
 */
class EditorHistory(private val limit: Int = 100) {

    data class Snap(val doc: Doc, val selStart: Int, val selEnd: Int)

    private val undoStack = ArrayList<Snap>()
    private val redoStack = ArrayList<Snap>()
    private var lastTypingAt = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    private fun push(s: Snap) {
        val top = undoStack.lastOrNull()
        if (top != null && top.doc == s.doc) return
        undoStack.add(s)
        if (undoStack.size > limit) undoStack.removeAt(0)
    }

    /** Call with the state BEFORE a keystroke's edit is applied. */
    fun beforeTyping(now: Long, before: Snap) {
        if (undoStack.isEmpty() || now - lastTypingAt > BURST_MS) push(before)
        lastTypingAt = now
        redoStack.clear()
    }

    /** Call with the state BEFORE a formatting action is applied. */
    fun beforeFormat(before: Snap) {
        push(before)
        lastTypingAt = 0L
        redoStack.clear()
    }

    fun undo(current: Snap): Snap? {
        if (undoStack.isEmpty()) return null
        redoStack.add(current)
        lastTypingAt = 0L
        return undoStack.removeAt(undoStack.size - 1)
    }

    fun redo(current: Snap): Snap? {
        if (redoStack.isEmpty()) return null
        undoStack.add(current)
        lastTypingAt = 0L
        return redoStack.removeAt(redoStack.size - 1)
    }

    companion object {
        const val BURST_MS = 900L
    }
}
