package com.busycall.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.tutpro.baresip.R

/** Kinds of text colour a row line can take (named roles from bc_colors.xml). */
enum class RowTone { Normal, Muted, Alert, Accent }

/**
 * Focusable list rows for short lists (numbers, recents). The row is the focus and click
 * target (guide §2); the list container never takes focus. Lists here are short, so a
 * LinearLayout inside a ScrollView is enough: moving focus scrolls the row into view.
 */
object Rows {

    /** Removes all rows from [container] and adds one per item, in order. Returns the rows. */
    fun <T> fill(
        container: ViewGroup,
        items: List<T>,
        bind: (row: View, title: TextView, subtitle: TextView, trailing: TextView, item: T) -> Unit,
    ): List<View> {
        // Keep focus on the same position after a refresh, so a live update never jumps the user.
        val focusedIndex = (0 until container.childCount).firstOrNull { container.getChildAt(it).hasFocus() }
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        val rows = items.map { item ->
            val row = inflater.inflate(R.layout.bc_row, container, false)
            bind(
                row,
                row.findViewById(R.id.bc_row_title),
                row.findViewById(R.id.bc_row_subtitle),
                row.findViewById(R.id.bc_row_trailing),
                item,
            )
            container.addView(row)
            row
        }
        if (focusedIndex != null && rows.isNotEmpty()) rows[minOf(focusedIndex, rows.size - 1)].requestFocus()
        return rows
    }

    fun tone(view: TextView, tone: RowTone) {
        val color = when (tone) {
            RowTone.Normal -> R.color.bc_text
            RowTone.Muted -> R.color.bc_text_muted
            RowTone.Alert -> R.color.bc_text_alert
            RowTone.Accent -> R.color.bc_accent
        }
        view.setTextColor(view.context.getColor(color))
    }

    fun setText(view: TextView, text: CharSequence?) {
        view.text = text ?: ""
        view.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
}
