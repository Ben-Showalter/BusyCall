package com.busycall.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.busycall.app.keys.KeyRouter
import com.busycall.app.keys.LogicalKey
import com.tutpro.baresip.R

/**
 * One entry of a menu. [subtitle] is a grey line under the label; [icon] a drawable shown on
 * the left (both optional, as in the Add a number preview).
 */
data class MenuItem(
    val label: String,
    val subtitle: String? = null,
    val icon: Int? = null,
    val action: () -> Unit,
)

/**
 * Menus and confirmations as `AlertDialog`s: they work with the D-pad as they are, and BACK
 * closes them (guide §2). Never a touch-only dialog. All of them look like the previews: a
 * rounded dark card, an accent-red title, and the app's focus ring on the selected row.
 */
object Menus {

    /**
     * A list menu. [message] is an optional question shown under the title. With [onHold],
     * holding OK or CALL on an entry runs `onHold(index)` instead of the entry (own timer,
     * guide §1); a short CALL acts like OK.
     */
    fun show(
        activity: Activity,
        title: String,
        items: List<MenuItem>,
        message: String? = null,
        onHold: ((Int) -> Unit)? = null,
    ) {
        if (activity.isFinishing) return
        val dialog = AlertDialog.Builder(activity, R.style.BcDialog)
            .setCustomTitle(titleView(activity, title, message))
            .setAdapter(Adapter(activity, items)) { d, which ->
                d.dismiss()
                items[which].action()
            }
            .create()
        if (onHold != null) dialog.setOnKeyListener(HoldListener(dialog, items, onHold))
        dialog.show()
        dialog.listView?.apply {
            selector = activity.getDrawable(R.drawable.bc_focus_row)
            // Behind the text: the gray focus tint is solid (owner, 2026-11-20: it hid the words).
            setDrawSelectorOnTop(false)
            divider = null
            dividerHeight = 0
        }
        card(dialog)
    }

    fun confirm(activity: Activity, title: String, message: String, yesLabel: String, onYes: () -> Unit) {
        if (activity.isFinishing) return
        val dialog = AlertDialog.Builder(activity, R.style.BcDialog)
            .setCustomTitle(titleView(activity, title, message))
            .setPositiveButton(yesLabel) { _, _ -> onYes() }
            .setNegativeButton(R.string.bc_cancel, null)
            .create()
        dialog.show()
        card(dialog)
    }

    fun notice(activity: Activity, title: String, message: String) {
        if (activity.isFinishing) return
        val dialog = AlertDialog.Builder(activity, R.style.BcDialog)
            .setCustomTitle(titleView(activity, title, message))
            .setPositiveButton(R.string.bc_ok, null)
            .create()
        dialog.show()
        card(dialog)
    }

    /** OK/CALL press vs hold inside a menu. Keys are read through KeyRouter (AGENTS §2). */
    private class HoldListener(
        private val dialog: AlertDialog,
        private val items: List<MenuItem>,
        private val onHold: (Int) -> Unit,
    ) : DialogInterface.OnKeyListener {
        private val handler = Handler(Looper.getMainLooper())
        private var fired = false
        private var downSeen = false
        private val hold = Runnable {
            fired = true
            val index = selected()
            dialog.dismiss()
            onHold(index)
        }

        private fun selected(): Int =
            (dialog.listView?.selectedItemPosition ?: 0).coerceIn(0, (items.size - 1).coerceAtLeast(0))

        override fun onKey(d: DialogInterface, keyCode: Int, event: KeyEvent): Boolean {
            val key = KeyRouter.route(keyCode, event.scanCode)
            if (key != LogicalKey.Ok && key != LogicalKey.Call) return false
            val isCall = key == LogicalKey.Call
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    fired = false
                    downSeen = true
                    handler.removeCallbacks(hold)
                    handler.postDelayed(hold, FlipBaseActivity.CALL_HOLD_MS)
                }
                return isCall // OK's DOWN still reaches the list (pressed state)
            }
            if (event.action == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(hold)
                val wasDown = downSeen
                downSeen = false
                if (fired) return true // the hold already acted: swallow the UP
                if (isCall && wasDown && items.isNotEmpty()) {
                    val index = selected()
                    dialog.dismiss()
                    items[index].action()
                    return true
                }
                return isCall // a short OK clicks the entry as usual
            }
            return isCall
        }
    }

    private fun card(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawableResource(R.drawable.bc_dialog_card)
    }

    private fun titleView(activity: Activity, title: String, message: String?): View {
        val view = LayoutInflater.from(activity).inflate(R.layout.bc_menu_title, null)
        view.findViewById<TextView>(R.id.bc_menu_title).apply {
            text = title
            paint.isFakeBoldText = true // textStyle=bold doesn't render with the stock font (guide §4)
        }
        view.findViewById<TextView>(R.id.bc_menu_message).apply {
            text = message ?: ""
            visibility = if (message.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        return view
    }

    private class Adapter(private val activity: Activity, private val items: List<MenuItem>) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(activity).inflate(R.layout.bc_menu_row, parent, false)
            val item = items[position]
            view.findViewById<TextView>(R.id.bc_menu_label).text = item.label
            view.findViewById<TextView>(R.id.bc_menu_subtitle).apply {
                text = item.subtitle ?: ""
                visibility = if (item.subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            view.findViewById<ImageView>(R.id.bc_menu_icon).apply {
                if (item.icon != null) {
                    setImageResource(item.icon)
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }
            return view
        }
    }
}
