package com.busycall.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import com.tutpro.baresip.R

/**
 * A recycled list for long lists (call log, contacts, blocked calls), guide §2's ListView
 * pattern. Only the rows on screen exist, and the ListView keeps focus and moves its *selection*
 * with the D-pad, so a step is not a focus change (on Kyocera every focus change also runs the
 * keyboard manager's failing `getShwoingNowFlag`, which made lists sluggish).
 *
 * [layoutOf] picks each item's row layout (bc_row, bc_call_row…); [bind] fills a row.
 */
class RowList<T>(
    private val list: ListView,
    private val layouts: List<Int>,
    private val layoutOf: (T) -> Int,
    private val bind: (row: View, item: T) -> Unit,
    onClick: (T) -> Unit,
) {
    private var items: List<T> = emptyList()

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int): T = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = layouts.size
        override fun getItemViewType(position: Int) = layouts.indexOf(layoutOf(items[position])).coerceAtLeast(0)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = items[position]
            val row = convertView ?: LayoutInflater.from(parent.context).inflate(layoutOf(item), parent, false).apply {
                // The ListView owns focus, clicks and the selection look (listSelector).
                isFocusable = false
                isClickable = false
                background = null
            }
            bind(row, item)
            return row
        }
    }

    init {
        list.adapter = adapter
        list.selector = list.context.getDrawable(R.drawable.bc_focus_row)
        // Behind the text: the gray focus tint is solid (as in menus).
        list.setDrawSelectorOnTop(false)
        list.divider = null
        list.dividerHeight = 0
        list.itemsCanFocus = false
        list.isFocusable = true
        list.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            items.getOrNull(position)?.let(onClick)
        }
    }

    /** New items; the selection stays on the same position (clamped). Only visible rows rebind. */
    fun submit(newItems: List<T>) {
        val keep = list.selectedItemPosition
        items = newItems
        adapter.notifyDataSetChanged()
        if (keep >= 0 && newItems.isNotEmpty()) list.setSelection(keep.coerceAtMost(newItems.size - 1))
    }

    /** The selected item (D-pad), or null. */
    fun selected(): T? = items.getOrNull(list.selectedItemPosition)

    fun select(position: Int) {
        if (position in items.indices) list.setSelection(position)
    }

    /** Starting focus (guide §2): only when nothing on screen has focus yet. */
    fun focusIfNothingFocused(activityFocus: View?) {
        if (activityFocus == null && items.isNotEmpty()) list.post { list.requestFocus(); if (list.selectedItemPosition < 0) list.setSelection(0) }
    }
}
