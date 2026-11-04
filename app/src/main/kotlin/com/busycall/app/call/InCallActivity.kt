package com.busycall.app.call

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.CallState
import com.busycall.app.bridge.SipAddress
import com.busycall.app.keys.LogicalKey
import com.busycall.app.ui.FlipBaseActivity
import com.busycall.app.ui.MenuItem
import com.tutpro.baresip.R

/**
 * In a call (PLAN.md S5).
 *
 * - 0–9 `*` `#` send keypad tones (DTMF) **the moment they're pressed**, straight to the call:
 *   no text field to focus first (the reason upstream's screen failed on the E4810).
 * - END = hang up, then the system does what END does (PLAN §3.4). Outer END = hang up;
 *   outer Speaker = speaker; headset button = hang up.
 * - BACK leaves the screen; the call goes on and the "On call" bar shows elsewhere.
 * - The on-screen **End call** button hangs up and stays in the app (AGENTS §13).
 */
class InCallActivity : FlipBaseActivity() {

    private val viewModel = InCallViewModel()
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var lastState: InCallState? = null
    private var sawCall = false
    private val openedAt = android.os.SystemClock.elapsedRealtime()

    private lateinit var title: TextView
    private lateinit var to: TextView
    private lateinit var name: TextView
    private lateinit var number: TextView
    private lateinit var sent: TextView
    private lateinit var speaker: TextView
    private lateinit var mute: TextView
    private lateinit var hold: TextView

    private val tick = object : Runnable {
        override fun run() {
            render(viewModel.state())
            if (resumed) handler.postDelayed(this, 500L)
        }
    }

    override val handlesCallKey = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bc_activity_incall)
        viewModel.callId = intent.getLongExtra(CallRouter.EXTRA_CALL_ID, 0L).takeIf { it != 0L }
        title = findViewById(R.id.bc_title)
        to = findViewById(R.id.bc_call_to)
        name = findViewById(R.id.bc_call_name)
        number = findViewById(R.id.bc_call_number)
        sent = findViewById(R.id.bc_call_sent)
        speaker = findViewById(R.id.bc_call_speaker)
        mute = findViewById(R.id.bc_call_mute)
        hold = findViewById(R.id.bc_call_hold)
        name.paint.isFakeBoldText = true
        val end = findViewById<TextView>(R.id.bc_call_end)
        end.paint.isFakeBoldText = true

        speaker.setOnClickListener { viewModel.toggleSpeaker(); refresh() }
        mute.setOnClickListener { viewModel.toggleMute(); refresh() }
        hold.setOnClickListener {
            if (lastState?.call?.state == CallState.OnHold) viewModel.resume() else viewModel.hold()
            refresh()
        }
        end.setOnClickListener { hangUp(leaveApp = false) }
        setFooterHint(getString(R.string.bc_incall_footer))
        end.post { end.requestFocus() }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        refresh()
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun refresh() {
        handler.removeCallbacks(tick)
        tick.run()
    }

    override fun optionsItems(): List<MenuItem> {
        val state = lastState ?: viewModel.state()
        return viewModel.actions(state).map { action ->
            when (action) {
                InCallAction.Speaker -> MenuItem(getString(if (state.speaker) R.string.bc_call_speaker_off else R.string.bc_call_speaker_on)) {
                    viewModel.toggleSpeaker(); refresh()
                }
                InCallAction.Mute -> MenuItem(getString(if (state.muted) R.string.bc_call_unmute else R.string.bc_call_mute)) {
                    viewModel.toggleMute(); refresh()
                }
                InCallAction.Hold -> MenuItem(getString(R.string.bc_call_hold)) { viewModel.hold(); refresh() }
                InCallAction.Resume -> MenuItem(getString(R.string.bc_call_resume)) { viewModel.resume(); refresh() }
                InCallAction.HangUp -> MenuItem(getString(R.string.bc_call_hang_up)) { hangUp(leaveApp = false) }
            }
        }
    }

    /** Keypad tones go out on DOWN, so a held key or quick PIN entry isn't delayed. */
    override fun onDigitDown(char: Char) {
        if (viewModel.digit(char)) refresh()
    }

    override fun onEndKeyDown() {
        // Hang up first; the base class then lets END reach the system (usually Home).
        viewModel.callId?.let { Bridges.current.hangup(this, it) }
    }

    override fun onLogicalKeyUp(key: LogicalKey): Boolean = when (key) {
        LogicalKey.OuterEnd, LogicalKey.Headset -> { hangUp(leaveApp = false); true }
        LogicalKey.OuterSpeaker -> { viewModel.toggleSpeaker(); refresh(); true }
        else -> false
    }

    private fun hangUp(leaveApp: Boolean) {
        viewModel.callId?.let { Bridges.current.hangup(this, it) }
        if (!leaveApp) finish() // back to the screen before the call
    }

    private fun render(state: InCallState) {
        if (state == lastState) return
        lastState = state
        val call = state.call
        if (call == null || call.state == CallState.Ended) {
            if (sawCall || call?.state == CallState.Ended) {
                finish()
                return
            }
            // Just placed: the call may take a moment to appear. Give up after a while, in plain words.
            if (android.os.SystemClock.elapsedRealtime() - openedAt > START_TIMEOUT_MS) {
                android.widget.Toast.makeText(this, R.string.bc_call_did_not_start, android.widget.Toast.LENGTH_LONG).show()
                finish()
                return
            }
            lastState = null // keep checking
            title.setText(R.string.bc_call_dialing)
            return
        }
        sawCall = true
        title.text = when (call.state) {
            CallState.Dialing -> getString(R.string.bc_call_dialing)
            CallState.Connecting -> getString(R.string.bc_call_connecting)
            CallState.OnHold -> getString(R.string.bc_call_on_hold, state.timer)
            else -> getString(R.string.bc_call_on_call, state.timer)
        }
        to.text = getString(if (call.incoming) R.string.bc_call_to else R.string.bc_call_from, call.numberLabel)
        name.text = call.peerName
        val shownNumber = SipAddress.parse(call.peerUri).user
        number.text = if (shownNumber == call.peerName) "" else shownNumber
        number.visibility = if (number.text.isEmpty()) View.GONE else View.VISIBLE
        sent.text = if (state.sentDigits.isEmpty()) "" else getString(R.string.bc_call_sent, state.sentDigits)
        speaker.text = getString(if (state.speaker) R.string.bc_call_speaker_is_on else R.string.bc_call_speaker)
        mute.text = getString(if (state.muted) R.string.bc_call_muted else R.string.bc_call_mute)
        hold.text = getString(if (call.state == CallState.OnHold) R.string.bc_call_resume else R.string.bc_call_hold)
        hold.isEnabled = call.state == CallState.Connected || call.state == CallState.OnHold
        placeOptionsLabel()
    }

    companion object {
        private const val START_TIMEOUT_MS = 10_000L
    }
}
