package com.busycall.app.call

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import com.busycall.app.keys.LogicalKey
import com.busycall.app.ui.FlipBaseActivity
import com.busycall.app.ui.MenuItem
import com.busycall.app.bridge.Bridges
import com.tutpro.baresip.R

/**
 * Incoming call (PLAN.md S4). Opened by [CallRouter] from upstream's ring notification
 * (full-screen over a closed flip / lock screen: manifest showWhenLocked + turnScreenOn).
 *
 * Keys: CALL or headset = answer. END = decline, then the system does what END does
 * (PLAN §3.4). Outer END = decline. BACK = silence the ringer only, never decline.
 */
class IncomingCallActivity : FlipBaseActivity() {

    private val viewModel = IncomingViewModel()
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var lastState: IncomingState? = null

    private lateinit var to: TextView
    private lateinit var name: TextView
    private lateinit var number: TextView

    private val tick = object : Runnable {
        override fun run() {
            render(viewModel.state())
            if (resumed) handler.postDelayed(this, 500L)
        }
    }

    override val handlesCallKey = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bc_activity_incoming)
        findViewById<TextView>(R.id.bc_title).setText(R.string.bc_incoming_title)
        viewModel.callId = intent.getLongExtra(CallRouter.EXTRA_CALL_ID, 0L).takeIf { it != 0L }
        to = findViewById(R.id.bc_call_to)
        name = findViewById(R.id.bc_call_name)
        number = findViewById(R.id.bc_call_number)
        name.paint.isFakeBoldText = true
        val answer = findViewById<TextView>(R.id.bc_call_answer)
        val decline = findViewById<TextView>(R.id.bc_call_decline)
        answer.paint.isFakeBoldText = true
        decline.paint.isFakeBoldText = true
        answer.setOnClickListener { answer() }
        decline.setOnClickListener { decline() }
        answer.post { answer.requestFocus() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.callId = intent.getLongExtra(CallRouter.EXTRA_CALL_ID, 0L).takeIf { it != 0L }
        lastState = null
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        handler.removeCallbacks(tick)
        tick.run()
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun optionsItems(): List<MenuItem> = listOf(
        MenuItem(getString(R.string.bc_call_silence)) { Bridges.current.silenceRinger() },
        MenuItem(getString(R.string.bc_call_answer)) { answer() },
        MenuItem(getString(R.string.bc_call_decline)) { decline() },
    )

    override fun onCallKey() = answer()

    override fun onEndKeyDown() {
        // Decline first; the base class then lets END reach the system.
        viewModel.callId?.let { Bridges.current.decline(this, it) }
    }

    override fun onLogicalKeyUp(key: LogicalKey): Boolean = when (key) {
        LogicalKey.Back -> { Bridges.current.silenceRinger(); true } // never declines (AGENTS §4)
        LogicalKey.Headset -> { answer(); true }
        LogicalKey.OuterEnd -> { decline(); true }
        else -> false
    }

    private fun answer() {
        val id = viewModel.callId ?: return
        Bridges.current.answer(this, id)
        startActivity(Intent(this, InCallActivity::class.java).putExtra(CallRouter.EXTRA_CALL_ID, id))
        finish()
    }

    private fun decline() {
        viewModel.callId?.let { Bridges.current.decline(this, it) }
        finish()
    }

    private fun render(state: IncomingState) {
        if (state == lastState) return
        lastState = state
        if (state.answered) {
            // Answered from elsewhere (headset, notification): go to the call.
            startActivity(Intent(this, InCallActivity::class.java).putExtra(CallRouter.EXTRA_CALL_ID, viewModel.callId ?: 0L))
            finish()
            return
        }
        val call = state.call
        if (call == null) {
            // Cancelled by the caller or already handled: it shows as missed in Recent.
            finish()
            return
        }
        to.text = getString(R.string.bc_call_to, call.numberLabel)
        name.text = call.peerName
        val shownNumber = com.busycall.app.bridge.SipAddress.parse(call.peerUri).user
        number.text = if (shownNumber == call.peerName) "" else shownNumber
        number.visibility = if (number.text.isEmpty()) View.GONE else View.VISIBLE
    }
}
