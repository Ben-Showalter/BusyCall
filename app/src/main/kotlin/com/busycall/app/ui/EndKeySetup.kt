package com.busycall.app.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.busycall.app.keys.EndKeyService
import com.tutpro.baresip.R

/**
 * Turning on BusyCall's END-key service: Android's Accessibility settings, with a toast giving
 * the D-pad path (OEM pages for one app don't open, guide §5).
 */
object EndKeySetup {

    fun isOn(activity: Activity): Boolean = EndKeyService.isEnabled(activity)

    /** Explains, then opens Android's Accessibility settings. */
    fun offer(activity: Activity) {
        Menus.confirm(
            activity,
            activity.getString(R.string.bc_end_setup_title),
            activity.getString(R.string.bc_end_setup_message, activity.getString(R.string.app_name)),
            activity.getString(R.string.bc_end_setup_open),
        ) { open(activity) }
    }

    fun open(activity: Activity) {
        try {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            activity.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        Toast.makeText(activity, R.string.bc_end_setup_path, Toast.LENGTH_LONG).show()
    }
}
