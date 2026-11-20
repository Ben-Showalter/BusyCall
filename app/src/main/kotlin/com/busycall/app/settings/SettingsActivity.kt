package com.busycall.app.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.busycall.app.bridge.Bridge
import com.busycall.app.bridge.Bridges
import android.widget.LinearLayout
import android.widget.TextView
import com.busycall.app.data.Prefs
import com.busycall.app.keys.EndKeyService
import com.busycall.app.ui.EndKeySetup
import com.busycall.app.ui.FlipBaseActivity
import com.busycall.app.ui.Menus
import com.busycall.app.ui.RowTone
import com.busycall.app.ui.Rows
import com.tutpro.baresip.R

/**
 * Settings (PLAN.md S9): Use for phone (SIM) calls, the END key (and staying ready without a
 * notification), which side the Options key is on, and About/licenses.
 * Audio and update settings come later.
 */
class SettingsActivity : FlipBaseActivity() {

    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bc_activity_list)
        findViewById<TextView>(R.id.bc_title).setText(R.string.bc_settings_title)
        list = findViewById(R.id.bc_list)
        render()
        list.getChildAt(0)?.let { first -> first.post { if (currentFocus == null) first.requestFocus() } }
    }

    private enum class Item { PhoneDialer, EndKey, EndKeySeen, OptionsSide, About }

    override fun onResume() {
        super.onResume()
        render() // the default dialer may have changed in Android's settings
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != Bridge.DIALER_REQUEST) return
        if (Bridges.current.isDefaultPhoneApp(this)) {
            if (Bridges.current.startSimCalls(this)) Toast.makeText(this, R.string.bc_dialer_on, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, R.string.bc_dialer_not_set, Toast.LENGTH_LONG).show()
        }
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != Bridge.PHONE_REQUEST) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            if (Bridges.current.startSimCalls(this)) Toast.makeText(this, R.string.bc_dialer_on, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, R.string.bc_dialer_no_permission, Toast.LENGTH_LONG).show()
        }
        render()
    }

    /** "Use for phone (SIM) calls": on asks Android; off goes to Android's default-apps screen. */
    private fun togglePhoneDialer() {
        val bridge = Bridges.current
        if (!bridge.isDefaultPhoneApp(this)) {
            bridge.askToBeDefaultPhoneApp(this)
            return
        }
        Menus.confirm(this, getString(R.string.bc_dialer_off_title), getString(R.string.bc_dialer_off_message),
            getString(R.string.bc_dialer_off_open)) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    private fun render() {
        // Only phones with a SIM can use BusyCall for phone calls.
        val items = Item.values().filter {
            (it != Item.PhoneDialer || packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) &&
                (it != Item.EndKeySeen || EndKeySetup.isOn(this))
        }
        Rows.fill(list, items) { row, title, subtitle, _, item ->
            when (item) {
                Item.PhoneDialer -> {
                    val on = Bridges.current.isDefaultPhoneApp(this)
                    Rows.setText(title, getString(R.string.bc_settings_phone_dialer))
                    Rows.setText(subtitle, getString(if (on) R.string.bc_settings_phone_dialer_on else R.string.bc_settings_phone_dialer_off))
                    Rows.tone(subtitle, RowTone.Muted)
                    row.setOnClickListener { togglePhoneDialer() }
                }
                Item.EndKey -> {
                    val on = EndKeySetup.isOn(this)
                    Rows.setText(title, getString(R.string.bc_settings_end_key))
                    Rows.setText(subtitle, getString(if (on) R.string.bc_settings_end_key_on else R.string.bc_settings_end_key_off))
                    Rows.tone(subtitle, RowTone.Muted)
                    // On or off, both happen in Android's Accessibility settings.
                    row.setOnClickListener { if (on) EndKeySetup.open(this) else EndKeySetup.offer(this) }
                }
                Item.EndKeySeen -> {
                    // What this phone sends for END (SPIKES S8): HOME on Kyocera, ENDCALL elsewhere.
                    val seen = EndKeyService.lastKey
                    Rows.setText(title, getString(R.string.bc_settings_end_seen))
                    Rows.setText(subtitle, seen?.let { getString(R.string.bc_settings_end_seen_value, it.name, it.keyCode, it.scanCode) }
                        ?: getString(R.string.bc_settings_end_seen_none))
                    Rows.tone(subtitle, RowTone.Muted)
                    row.setOnClickListener { render() }
                }
                Item.OptionsSide -> {
                    Rows.setText(title, getString(R.string.bc_settings_options_side))
                    Rows.setText(subtitle, getString(
                        if (Prefs.isOptionsOnLeft(this)) R.string.bc_settings_side_left else R.string.bc_settings_side_right,
                    ))
                    Rows.tone(subtitle, RowTone.Muted)
                    row.setOnClickListener {
                        Prefs.setOptionsOnLeft(this, !Prefs.isOptionsOnLeft(this))
                        placeOptionsLabel()
                        render()
                    }
                }
                Item.About -> {
                    Rows.setText(title, getString(R.string.bc_settings_about))
                    Rows.setText(subtitle, getString(R.string.bc_settings_about_sub))
                    Rows.tone(subtitle, RowTone.Muted)
                    row.setOnClickListener {
                        Menus.notice(this, getString(R.string.bc_settings_about), getString(R.string.bc_about_text))
                    }
                }
            }
        }
    }
}
