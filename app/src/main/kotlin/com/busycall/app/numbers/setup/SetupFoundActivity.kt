package com.busycall.app.numbers.setup

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.NumberDraft
import com.busycall.app.bridge.SaveResult
import com.busycall.app.numbers.DraftExtras
import com.busycall.app.numbers.NumberSetupActivity
import com.busycall.app.ui.FlipBaseActivity
import com.busycall.app.ui.MenuItem
import com.busycall.app.ui.Rows
import com.busycall.app.ui.Words
import com.tutpro.baresip.R

/**
 * "Setup code found" (PLAN.md S7a): shows what a pasted or scanned code contains, password
 * hidden. OK on a line opens Number setup with everything filled in; nothing is saved until
 * "Save and connect" (AGENTS.md §6a).
 */
class SetupFoundActivity : FlipBaseActivity() {

    private lateinit var draft: NumberDraft

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val found = DraftExtras.get(intent)
        if (found == null) {
            finish()
            return
        }
        draft = found
        setContentView(R.layout.bc_activity_list)
        findViewById<TextView>(R.id.bc_title).setText(R.string.bc_found_title)
        findViewById<TextView>(R.id.bc_list_hint).apply {
            setText(R.string.bc_found_hint)
            visibility = View.VISIBLE
        }
        setFooterHint(getString(R.string.bc_found_footer))

        val list = findViewById<LinearLayout>(R.id.bc_list)
        val server = draft.server + (draft.port?.let { ":$it" } ?: "")
        val lines = listOf(
            getString(R.string.bc_field_name) to draft.name.ifEmpty { getString(R.string.bc_found_no_name, draft.user) },
            getString(R.string.bc_field_server) to server,
            getString(R.string.bc_found_user_password) to getString(
                R.string.bc_found_user_password_value, draft.user,
                if (draft.password.isEmpty()) getString(R.string.bc_found_no_password) else "••••••••",
            ),
            getString(R.string.bc_field_connection) to Words.transport(this, draft.transport),
        )
        Rows.fill(list, lines) { row, title, subtitle, _, line ->
            // Caption above, value below (the row's title/subtitle order swapped for this screen).
            Rows.setText(title, line.second)
            Rows.setText(subtitle, line.first)
            row.setOnClickListener { editInFull() }
        }
        val save = layoutInflater.inflate(R.layout.bc_button_call, list, false) as TextView
        save.setText(R.string.bc_save_connect)
        save.paint.isFakeBoldText = true
        save.setOnClickListener { saveAndConnect() }
        list.addView(save)
        save.post { save.requestFocus() }
    }

    override fun optionsItems(): List<MenuItem> = listOf(
        MenuItem(getString(R.string.bc_save_connect)) { saveAndConnect() },
        MenuItem(getString(R.string.bc_found_edit)) { editInFull() },
    )

    private fun editInFull() {
        startActivity(DraftExtras.put(Intent(this, NumberSetupActivity::class.java), draft))
        finish()
    }

    private fun saveAndConnect() {
        when (val result = Bridges.current.addNumber(draft)) {
            is SaveResult.Saved -> {
                Toast.makeText(this, R.string.bc_saved_connecting, Toast.LENGTH_SHORT).show()
                finish()
            }
            // Show the problem where it can be fixed: the full form, with everything filled in.
            is SaveResult.Failed -> {
                Toast.makeText(this, R.string.bc_found_check, Toast.LENGTH_LONG).show()
                editInFull()
            }
        }
    }
}
