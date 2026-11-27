package com.inkwell.notes.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inkwell.notes.data.Note
import com.inkwell.notes.data.Notebook

/** Plain 2-column grid of note cards (no selection) — used by Tags and Notebook detail. */
fun LazyListScope.noteGrid(
    notes: List<Note>,
    notebooks: Map<String, Notebook>,
    keyPrefix: String,
    onOpen: (String) -> Unit,
) {
    val rows = notes.chunked(2)
    itemsIndexed(rows, key = { _, r -> keyPrefix + r.first().id }) { rowIndex, row ->
        Row(
            Modifier
                .animateItem()
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp)
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            row.forEachIndexed { col, n ->
                NoteCard(
                    note = n,
                    notebook = n.notebookId?.let { notebooks[it] },
                    index = rowIndex * 2 + col,
                    wide = false,
                    selectMode = false,
                    selected = false,
                    onClick = { onOpen(n.id) },
                    onLongClick = {},
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
