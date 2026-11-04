package com.busycall.app.settings

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.busycall.app.data.Prefs
import com.busycall.app.ui.FlipBaseActivity
import com.busycall.app.ui.Menus
import com.busycall.app.ui.RowTone
import com.busycall.app.ui.Rows
import com.tutpro.baresip.R

/**
 * Settings (PLAN.md S9), first part: which side the Options key is on, and About/licenses.
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

    private enum class Item { OptionsSide, About }

    private fun render() {
        Rows.fill(list, Item.values().toList()) { row, title, subtitle, _, item ->
            when (item) {
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
