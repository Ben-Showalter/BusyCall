package com.busycall.app.calllog

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.CallDirection
import com.busycall.app.bridge.PlaceResult
import com.busycall.app.bridge.SipAddress
import com.busycall.app.call.InCallActivity
import com.busycall.app.dialer.DialerActivity
import com.busycall.app.ui.FlipBaseActivity
import com.busycall.app.ui.MenuItem
import com.busycall.app.ui.Menus
import com.busycall.app.ui.RowTone
import com.busycall.app.ui.Rows
import com.busycall.app.ui.Words
import com.tutpro.baresip.R

/**
 * Call log (PLAN.md S6): every call, newest first, missed in red, each with the number it was on.
 * CALL calls back from that number; OK opens Call back · Call from… · Delete.
 */
class CallLogActivity : FlipBaseActivity() {

    private val viewModel = CallLogViewModel()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var list: LinearLayout
    private lateinit var hint: TextView
    private var lastState: CallLogState? = null
    private var resumed = false

    // New calls and "5 min ago" labels change while the screen is open.
    private val tick = object : Runnable {
        override fun run() {
            render(viewModel.state())
            if (resumed) handler.postDelayed(this, REFRESH_MS)
        }
    }

    override val handlesCallKey = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bc_activity_list)
        findViewById<TextView>(R.id.bc_title).setText(R.string.bc_calllog_title)
        hint = findViewById(R.id.bc_list_hint)
        list = findViewById(R.id.bc_list)
        setFooterHint(getString(R.string.bc_calllog_footer))
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        lastState = null
        handler.removeCallbacks(tick)
        tick.run()
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun focusedRow(): CallLogRow? = currentFocus?.getTag(R.id.bc_list) as? CallLogRow

    override fun optionsItems(): List<MenuItem> {
        val row = focusedRow() ?: return emptyList()
        return rowActions(row)
    }

    override fun onCallKey() {
        focusedRow()?.let { callBack(it) }
    }

    private fun rowActions(row: CallLogRow) = listOf(
        MenuItem(getString(R.string.bc_calllog_call_back)) { callBack(row) },
        MenuItem(getString(R.string.bc_dialer_from_menu)) {
            val number = SipAddress.parse(row.recent.peerUri).user.ifEmpty { row.recent.peerUri }
            startActivity(Intent(this, DialerActivity::class.java).putExtra(DialerActivity.EXTRA_NUMBER, number))
        },
        MenuItem(getString(R.string.bc_calllog_delete)) {
            viewModel.delete(row)
            lastState = null
            render(viewModel.state())
        },
    )

    private fun callBack(row: CallLogRow) {
        val (fromId, dial) = viewModel.callBackTarget(row)
        when (Bridges.current.placeCall(this, fromId, dial)) {
            PlaceResult.Placed -> startActivity(Intent(this, InCallActivity::class.java))
            is PlaceResult.Failed -> Toast.makeText(this, R.string.bc_dialer_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun render(state: CallLogState) {
        if (state == lastState) return
        lastState = state
        val message = when {
            !state.running -> getString(R.string.bc_home_starting)
            state.rows.isEmpty() -> getString(R.string.bc_calllog_empty)
            else -> null
        }
        hint.text = message ?: ""
        hint.visibility = if (message == null) View.GONE else View.VISIBLE

        val rows = Rows.fill(list, state.rows) { row, title, subtitle, _, item ->
            val r = item.recent
            Rows.setText(title, r.peerName)
            Rows.tone(title, if (r.missed) RowTone.Alert else RowTone.Normal)
            val kind = getString(
                when {
                    r.missed -> R.string.bc_recent_missed
                    r.direction == CallDirection.In -> R.string.bc_recent_incoming
                    else -> R.string.bc_recent_outgoing
                }
            )
            val onNumber = getString(
                if (r.direction == CallDirection.In) R.string.bc_recent_to else R.string.bc_recent_from,
                r.numberLabel,
            )
            Rows.setText(subtitle, getString(R.string.bc_recent_line, kind, onNumber, Words.time(this, item.time)))
            row.setTag(R.id.bc_list, item)
            row.setOnClickListener { Menus.show(this, r.peerName, rowActions(item)) }
        }
        placeOptionsLabel()
        if (currentFocus == null) rows.firstOrNull()?.let { first -> first.post { if (currentFocus == null) first.requestFocus() } }
    }

    companion object {
        private const val REFRESH_MS = 30_000L
    }
}
