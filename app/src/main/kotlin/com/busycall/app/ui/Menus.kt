package com.busycall.app.ui

import android.app.Activity
import android.app.AlertDialog
import com.tutpro.baresip.R

/** One entry of an Options menu. */
data class MenuItem(val label: String, val action: () -> Unit)

/**
 * Menus and confirmations as `AlertDialog`s: they work with the D-pad as they are,
 * and BACK closes them (guide §2). Never a touch-only dialog.
 */
object Menus {

    fun show(activity: Activity, title: String, items: List<MenuItem>) {
        if (activity.isFinishing) return
        AlertDialog.Builder(activity, R.style.BcDialog)
            .setTitle(title)
            .setItems(items.map { it.label }.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                items[which].action()
            }
            .show()
    }

    fun confirm(activity: Activity, title: String, message: String, yesLabel: String, onYes: () -> Unit) {
        if (activity.isFinishing) return
        AlertDialog.Builder(activity, R.style.BcDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(yesLabel) { _, _ -> onYes() }
            .setNegativeButton(R.string.bc_cancel, null)
            .show()
    }

    fun notice(activity: Activity, title: String, message: String) {
        if (activity.isFinishing) return
        AlertDialog.Builder(activity, R.style.BcDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.bc_ok, null)
            .show()
    }
}
