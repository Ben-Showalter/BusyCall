package com.busycall.app.blocking

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.PlaceResult
import com.busycall.app.call.Calling
import com.busycall.app.call.InCallActivity
import com.busycall.app.ui.FlipBaseActivity
import com.busycall.app.ui.MenuItem
import com.busycall.app.ui.Menus
import com.busycall.app.ui.RowTone
import com.busycall.app.ui.Rows
import com.busycall.app.ui.Words
import com.tutpro.baresip.R

/**
 * Blocked calls (owner, 2026-11-20): every call BusyCall's blocking rejected, newest first,
 * with why and which number it was to. OK: Unblock · Call back · Delete. A rejected caller
 * normally reached voicemail (the phone system's or carrier's busy setting).
 */
class BlockedActivity : FlipBaseActivity() {

    private val viewModel = BlockedViewModel()
    private lateinit var list: LinearLayout
    private lateinit var hint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bc_activity_list)
        findViewById<TextView>(R.id.bc_title).setText(R.string.bc_blocked_title)
        hint = findViewById(R.id.bc_list_hint)
        list = findViewById(R.id.bc_list)
        setFooterHint(getString(R.string.bc_blocked_footer))
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val rows = viewModel.rows()
        hint.text = if (rows.isEmpty()) getString(R.string.bc_blocked_empty) else ""
        hint.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        val views = Rows.fill(list, rows) { row, title, subtitle, _, item ->
            Rows.setText(title, item.item.peerName)
            Rows.setText(subtitle, getString(
                R.string.bc_blocked_line,
                Words.blockReason(this, item.item.reason),
                getString(R.string.bc_recent_to, item.item.numberLabel),
                Words.time(this, item.time),
            ))
            Rows.tone(subtitle, RowTone.Muted)
            row.setOnClickListener { showActions(item) }
        }
        if (currentFocus == null) views.firstOrNull()?.let { first -> first.post { if (currentFocus == null) first.requestFocus() } }
    }

    private fun showActions(row: BlockedRow) {
        Menus.show(this, row.item.peerName, listOf(
            MenuItem(getString(R.string.bc_blocked_unblock), getString(R.string.bc_blocked_unblock_sub)) {
                viewModel.unblock(row)
                Toast.makeText(this, R.string.bc_blocked_unblocked, Toast.LENGTH_LONG).show()
                render()
            },
            MenuItem(getString(R.string.bc_calllog_call_back)) {
                when (val result = Bridges.current.placeCall(this, row.item.numberId, row.item.peerUri)) {
                    PlaceResult.Placed -> startActivity(android.content.Intent(this, InCallActivity::class.java))
                    is PlaceResult.Failed -> Toast.makeText(this, Calling.problemText(result.problem), Toast.LENGTH_LONG).show()
                }
            },
            MenuItem(getString(R.string.bc_calllog_delete)) {
                viewModel.delete(row)
                render()
            },
        ))
    }
}
