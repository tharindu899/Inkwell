package com.inkwell.notes

import android.app.Application
import com.inkwell.notes.data.NotesRepository
import com.inkwell.notes.ui.components.ToastData
import kotlinx.coroutines.flow.MutableSharedFlow

class InkwellApplication : Application() {
    /** Single source of truth for notes/notebooks, shared by every screen. */
    val repo: NotesRepository by lazy { NotesRepository(this) }

    /** Any screen can emit a toast here; MainShell shows it. */
    val toasts = MutableSharedFlow<ToastData>(extraBufferCapacity = 8)
}
