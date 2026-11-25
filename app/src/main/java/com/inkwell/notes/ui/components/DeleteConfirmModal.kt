package com.inkwell.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.R
import com.inkwell.notes.ui.theme.Ink

/** Web `#e06060` (hard-coded there for the delete UI, same in every theme). */
val DeleteRed = Color(0xFFE06060)

/**
 * Port of the `.modal-overlay.show` + `.select-delete-modal` block in Home.jsx:
 * bottom-anchored card over a dim scrim, max 390 wide, 2-column Cancel / Delete.
 */
@Composable
fun DeleteConfirmModal(
    count: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    /** Defaults are the Home multi-select copy; the Editor passes its own single-note text. */
    title: String = "Delete selected notes?",
    message: String = "$count selected note${if (count != 1) "s" else ""} will be deleted. " +
        "You can undo for 5 seconds after deleting.",
) {
    val c = Ink.colors
    // Sit above the keyboard too (Editor opens this with the keyboard up).
    val bottomInsets = WindowInsets.navigationBars.union(WindowInsets.ime)
    val cardShape = RoundedCornerShape(26.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f))
            .tap(onCancel) // tap outside = cancel
            .windowInsetsPadding(bottomInsets)
            .padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 390.dp)
                .fillMaxWidth()
                .clip(cardShape)
                .background(c.bg) // web: var(--surface)
                .border(1.dp, c.border, cardShape)
                .tap { /* swallow taps so the scrim doesn't dismiss */ }
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // .select-delete-icon
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(DeleteRed.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                FaIcon(R.drawable.ic_fa_trash_can, tint = DeleteRed, size = 24.dp)
            }
            Spacer(Modifier.height(12.dp))
            InkText(
                title,
                size = 24.sp,
                weight = FontWeight.Bold,
                family = Ink.serif,
                lineHeight = 26.4.sp,
                align = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            InkText(
                message,
                size = 13.sp,
                color = c.text2,
                lineHeight = 19.5.sp,
                align = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModalButton("Cancel", c.bgCard, c.text2, c.border, Modifier.weight(1f), onCancel)
                ModalButton("Delete", DeleteRed, Color.White, DeleteRed, Modifier.weight(1f), onConfirm)
            }
        }
    }
}

/** .btn (+ .btn-ghost / .btn-danger) inside .modal-actions-2col — 44dp min, radius 15. */
@Composable
private fun ModalButton(
    label: String,
    background: Color,
    textColor: Color,
    border: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        InkText(label, size = 14.sp, weight = FontWeight.ExtraBold, color = textColor)
    }
}
