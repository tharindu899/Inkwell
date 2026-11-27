package com.inkwell.notes

import android.app.Application
import com.inkwell.notes.data.NotesRepository

class InkwellApplication : Application() {
    /** Single source of truth for notes/notebooks, shared by every screen. */
    val repo: NotesRepository by lazy { NotesRepository(this) }
}
