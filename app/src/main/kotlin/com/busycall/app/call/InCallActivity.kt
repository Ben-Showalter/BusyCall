package com.busycall.app.call

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.CallState
import com.busycall.app.dialer.DialerActivity
import com.busycall.app.keys.LogicalKey
import com.busycall.app.ui.FlipBaseActivity
import com.tutpro.baresip.R

/**
 * In a call (PLAN.md S5), with the D-pad ring (owner, 2026-11-20):
 * ▲ Speaker · ◀ Mute · ▶ Hold (Resume; Swap with two calls) · ▼ + Call · OK = End call.
 *
 * - The arrows and OK act directly (this screen claims them, so focus never moves); every
 *   petal is also a focusable, tappable button.
 * - 0–9 `*` `#` send keypad tones (DTMF) **the moment they're pressed**, straight to the call.
 * - END = hang up, then the system does what END does (PLAN §3.4). Outer END / headset = hang
 *   up; outer Speaker = speaker.
 * - The softkey is "Home": the phone's home screen, the call carries on (the On-call bar and
 *   notification bring it back). BACK leaves the screen the same way. There's no End call bar
 *   and no Options menu in a call: the red centre key ends it (AGENTS §0 rule 5).
 */
class InCallActivity : FlipBaseActivity() {

    private val viewModel = InCallViewModel()
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var lastState: InCallState? = null
    private var sawCall = false
    private var openedAt = android.os.SystemClock.elapsedRealtime()

    private lateinit var title: TextView
    private lateinit var to: TextView
    private lateinit var name: TextView
    private lateinit var number: TextView
    private lateinit var other: TextView
    private lateinit var sent: TextView
    private lateinit var speaker: TextView
    private lateinit var mute: TextView
    private lateinit var hold: TextView
    private lateinit var add: TextView
    private lateinit var end: TextView

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
        other = findViewById(R.id.bc_call_other)
        sent = findViewById(R.id.bc_call_sent)
        speaker = findViewById(R.id.bc_call_speaker)
        mute = findViewById(R.id.bc_call_mute)
        hold = findViewById(R.id.bc_call_hold)
        add = findViewById(R.id.bc_call_add)
        end = findViewById(R.id.bc_call_end)
        name.paint.isFakeBoldText = true
        end.paint.isFakeBoldText = true

        speaker.setOnClickListener { act(RingKey.Up) }
        mute.setOnClickListener { act(RingKey.Left) }
        hold.setOnClickListener { act(RingKey.Right) }
        add.setOnClickListener { act(RingKey.Down) }
        end.setOnClickListener { act(RingKey.Center) }
        setFooterHint(getString(R.string.bc_incall_footer))
        end.post { end.requestFocus() }
    }

    /** A second call placed from "+ Call" comes back here: show the newest call. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.callId = intent.getLongExtra(CallRouter.EXTRA_CALL_ID, 0L).takeIf { it != 0L }
        sawCall = false
        openedAt = android.os.SystemClock.elapsedRealtime()
        lastState = null
        refresh()
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

    override fun softkeyOverride(): Pair<String, () -> Unit> = getString(R.string.bc_softkey_home) to { goToPhoneHome() }

    /** The ring's keys act directly; focus never moves on them. */
    override fun claimsKey(key: LogicalKey): Boolean = when (key) {
        LogicalKey.Up, LogicalKey.Down, LogicalKey.Left, LogicalKey.Right, LogicalKey.Ok -> true
        else -> false
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
        LogicalKey.Up -> { act(RingKey.Up); true }
        LogicalKey.Down -> { act(RingKey.Down); true }
        LogicalKey.Left -> { act(RingKey.Left); true }
        LogicalKey.Right -> { act(RingKey.Right); true }
        LogicalKey.Ok -> { act(RingKey.Center); true }
        LogicalKey.OuterEnd, LogicalKey.Headset -> { act(RingKey.Center); true }
        LogicalKey.OuterSpeaker -> { act(RingKey.Up); true }
        else -> false
    }

    private fun act(key: RingKey) {
        when (viewModel.actionFor(key, lastState ?: viewModel.state())) {
            InCallAction.Speaker -> viewModel.toggleSpeaker()
            InCallAction.Mute -> viewModel.toggleMute()
            InCallAction.Hold -> viewModel.hold()
            InCallAction.Resume -> viewModel.resume()
            InCallAction.Swap -> viewModel.swap()
            InCallAction.AddCall -> {
                viewModel.prepareAddCall()
                startActivity(Intent(this, DialerActivity::class.java).putExtra(DialerActivity.EXTRA_ADD_CALL, true))
            }
            InCallAction.HangUp -> {
                viewModel.callId?.let { Bridges.current.hangup(this, it) }
                // The on-screen End stays in the app: back to the other call, else the screen before.
                if (!viewModel.followOther()) finish()
            }
            InCallAction.Nothing -> {}
        }
        lastState = null
        refresh()
    }

    private fun render(state: InCallState) {
        if (state == lastState) return
        lastState = state
        val call = state.call
        if (call == null || call.state == CallState.Ended) {
            if (viewModel.followOther()) {
                lastState = null
                return
            }
            if (sawCall || call?.state == CallState.Ended) {
                finish()
                return
            }
            // Just placed: the call may take a moment to appear. Give up after a while, in plain words.
            if (android.os.SystemClock.elapsedRealtime() - openedAt > START_TIMEOUT_MS) {
                Toast.makeText(this, R.string.bc_call_did_not_start, Toast.LENGTH_LONG).show()
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
        val shownNumber = CallText.number(call.peerUri)
        number.text = if (shownNumber == call.peerName) "" else shownNumber
        number.visibility = if (number.text.isEmpty()) View.GONE else View.VISIBLE
        val o = state.other
        other.text = if (o == null) "" else getString(
            R.string.bc_call_other, o.peerName,
            if (o.state == CallState.OnHold) getString(R.string.bc_call_state_on_hold) else CallText.duration(o.durationSec),
        )
        other.visibility = if (o == null) View.GONE else View.VISIBLE
        sent.text = if (state.sentDigits.isEmpty()) "" else getString(R.string.bc_call_sent, state.sentDigits)

        // Lit petals show what's on (owner's preview: lit = on).
        speaker.isActivated = state.speaker
        speaker.setText(if (state.speaker) R.string.bc_call_speaker_is_on else R.string.bc_call_speaker)
        mute.isActivated = state.muted
        mute.setText(if (state.muted) R.string.bc_call_muted else R.string.bc_call_mute)
        val right = viewModel.actionFor(RingKey.Right, state)
        hold.isActivated = call.state == CallState.OnHold
        hold.setText(
            when (right) {
                InCallAction.Swap -> R.string.bc_call_swap
                InCallAction.Resume -> R.string.bc_call_resume
                else -> R.string.bc_call_hold
            }
        )
        hold.setCompoundDrawablesRelativeWithIntrinsicBounds(
            0, if (right == InCallAction.Resume) R.drawable.bc_ic_resume else R.drawable.bc_ic_hold, 0, 0,
        )
        hold.isEnabled = right != InCallAction.Nothing
        add.isEnabled = viewModel.actionFor(RingKey.Down, state) == InCallAction.AddCall
        add.alpha = if (add.isEnabled) 1f else 0.4f
        hold.alpha = if (hold.isEnabled) 1f else 0.4f
    }

    companion object {
        private const val START_TIMEOUT_MS = 10_000L
    }
}
