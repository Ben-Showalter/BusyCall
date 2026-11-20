package com.busycall.app.dialer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.content.Intent
import android.widget.Toast
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.EmergencyRoute
import com.busycall.app.bridge.PlaceResult
import com.busycall.app.call.Calling
import com.busycall.app.call.InCallActivity
import com.busycall.app.keys.HoldTimer
import com.busycall.app.keys.LogicalKey
import com.busycall.app.ui.FlipBaseActivity
import com.busycall.app.ui.MenuItem
import com.busycall.app.ui.Rows
import com.tutpro.baresip.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Dialer (PLAN.md S2). Digits type the number; hold 0 for `+`; Clear (BACK) deletes one digit,
 * and leaves when the number is empty.
 *
 * Calling (owner, 2026-11-20): a short press of OK or CALL opens **Call from…**; holding OK or
 * CALL calls from the ★ number at once (call/CallFrom). With an empty number, OK/CALL fills in
 * the last number called.
 *
 * Under the number, up to 3 contacts whose number contains the digits or whose name the keys
 * spell (PLAN §3.3), the matched part underlined. ▼ moves into them; OK or CALL on one calls it.
 * The index is built once and queried off the main thread.
 */
class DialerActivity : FlipBaseActivity() {

    private val viewModel = DialerViewModel()
    private val holdTimer = HoldTimer()
    private val handler = Handler(Looper.getMainLooper())
    private val holdFired = Runnable {
        holdTimer.fire()?.let { viewModel.type(it); render() }
    }

    private lateinit var number: TextView
    private lateinit var error: TextView
    private lateinit var matchList: LinearLayout

    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    @Volatile private var matcher: KeypadMatcher? = null
    private var shownFor: String? = null

    override val usesCallHold = true

    /** "Add a call" from a call: the new call goes out on the held call's number, linked to it. */
    private var holdCallId: Long? = null
    /** "Transfer to": OK/CALL sends that call to the number instead of calling. */
    private var transferCallId: Long? = null
    private lateinit var callButton: TextView
    private lateinit var emergencyHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bc_activity_dialer)
        holdCallId = intent.getLongExtra(EXTRA_HOLD_CALL_ID, 0L).takeIf { it != 0L }
        transferCallId = intent.getLongExtra(EXTRA_TRANSFER_CALL_ID, 0L).takeIf { it != 0L }
        findViewById<TextView>(R.id.bc_title).setText(
            when {
                transferCallId != null -> R.string.bc_dialer_transfer_title
                intent.getBooleanExtra(EXTRA_ADD_CALL, false) -> R.string.bc_dialer_add_title
                else -> R.string.bc_dialer_title
            }
        )
        setFooterHint(getString(R.string.bc_dialer_footer))
        number = findViewById(R.id.bc_dialer_number)
        error = findViewById(R.id.bc_dialer_error)
        matchList = findViewById(R.id.bc_dialer_matches)
        number.setOnClickListener { call(null, held = false) }
        callButton = findViewById(R.id.bc_dialer_call)
        emergencyHint = findViewById(R.id.bc_dialer_emergency)
        callButton.paint.isFakeBoldText = true
        callButton.setOnClickListener { call(null, held = false) }
        intent.getStringExtra(EXTRA_NUMBER)?.let { viewModel.setNumber(it) }
        render()
        number.post { if (currentFocus == null) number.requestFocus() }
        worker.execute {
            val built = viewModel.buildMatcher()
            matcher = built
            handler.post { shownFor = null; updateMatches() }
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** Options holds only what isn't on screen (owner, 2026-11-20). */
    override fun optionsItems(): List<MenuItem> = listOf(
        MenuItem(getString(R.string.bc_dialer_clear)) { viewModel.setNumber(""); render() },
    )

    override fun onDigitDown(char: Char) {
        if (holdTimer.press(char)) handler.postDelayed(holdFired, holdTimer.holdMs)
    }

    override fun onDigit(char: Char) {
        handler.removeCallbacks(holdFired)
        holdTimer.release(char)?.let { viewModel.type(it) }
        error.visibility = View.GONE
        render()
    }

    /** OK or CALL: short = Call from…, held = ★. A focused match is called instead of the typed number. */
    override fun onCallOrOk(held: Boolean, callKey: Boolean): Boolean {
        call(currentFocus?.getTag(R.id.bc_dialer_matches) as? DialMatch, held)
        return true
    }

    override fun onLogicalKeyUp(key: LogicalKey): Boolean = when (key) {
        // Clear arrives as BACK on Kyocera: delete a digit; leave only when empty (AGENTS §4).
        LogicalKey.Back, LogicalKey.Delete -> if (viewModel.delete()) { render(); true } else false
        else -> false
    }

    private fun call(match: DialMatch?, held: Boolean) {
        val target = viewModel.target(match)
        val transferId = transferCallId
        val holdId = holdCallId
        val emergency = target is DialTarget.Dial && Bridges.current.isEmergencyNumber(this, target.dial)
        if (emergency && transferId != null) {
            showError(getString(R.string.bc_emergency_no_transfer))
            return
        }
        if (target is DialTarget.Dial && transferId != null) {
            // Blind transfer (owner, 2026-11-20): no Call from… picker; the call ends when it's done.
            if (Bridges.current.transfer(transferId, target.dial)) {
                Toast.makeText(this, R.string.bc_transfer_done, Toast.LENGTH_LONG).show()
                finish()
            } else {
                showError(getString(R.string.bc_transfer_failed))
            }
            return
        }
        // An emergency number from "Add a call" follows the emergency rules, not the held call's number.
        if (target is DialTarget.Dial && holdId != null && !emergency) {
            when (val result = Bridges.current.placeCall(this, null, target.dial, holdCallId = holdId)) {
                PlaceResult.Placed -> {
                    startActivity(Intent(this, InCallActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    finish()
                }
                is PlaceResult.Failed -> showError(getString(Calling.problemText(result.problem)))
            }
            return
        }
        when (target) {
            is DialTarget.Dial -> Calling.start(
                this, target.dial, target.shown, held,
                onPlaced = { finish() },
                onProblem = { showError(it) },
            )
            DialTarget.Redialed -> render()
            DialTarget.NothingToCall -> showError(getString(R.string.bc_dialer_type_number))
        }
    }

    private fun showError(text: String) {
        error.text = text
        error.visibility = View.VISIBLE
    }

    private fun render() {
        number.text = viewModel.state().number
        renderEmergency()
        updateMatches()
    }

    /** While the typed number is an emergency number: "Emergency call" and where it goes out. */
    private fun renderEmergency() {
        val typed = viewModel.number
        val emergency = typed.isNotEmpty() && Bridges.current.isEmergencyNumber(this, typed)
        callButton.setText(if (emergency) R.string.bc_emergency_call else R.string.bc_dialer_call)
        emergencyHint.visibility = if (emergency) View.VISIBLE else View.GONE
        if (!emergency) return
        emergencyHint.text = when (val route = Calling.emergencyRoute(this)) {
            is EmergencyRoute.Voip -> getString(R.string.bc_emergency_hint_voip, route.number.label, route.number.number)
            EmergencyRoute.PhoneNetwork -> getString(R.string.bc_emergency_hint_phone)
        }
    }

    /** Asks the worker for the matches of the current number; shows them if it hasn't changed since. */
    private fun updateMatches() {
        val text = viewModel.number
        if (text == shownFor) return
        shownFor = text
        val current = matcher
        if (current == null || text.isEmpty()) {
            showMatches(emptyList())
            return
        }
        worker.execute {
            val found = viewModel.matchesFor(current, text)
            handler.post { if (viewModel.number == text) showMatches(found) }
        }
    }

    private fun showMatches(matches: List<DialMatch>) {
        val hadFocus = matchList.hasFocus()
        Rows.fill(matchList, matches) { row, title, subtitle, _, m ->
            title.text = underline(m.name, m.nameHighlight)
            title.visibility = View.VISIBLE
            val prefix = if (m.label.isEmpty()) "" else getString(R.string.bc_match_label_prefix, m.label)
            subtitle.text = underline(prefix + m.display, m.numberHighlight?.let { it.first + prefix.length..it.last + prefix.length })
            subtitle.visibility = View.VISIBLE
            row.setTag(R.id.bc_dialer_matches, m)
            row.setOnClickListener { call(m, held = false) }
        }
        matchList.visibility = if (matches.isEmpty()) View.GONE else View.VISIBLE
        if (hadFocus && !matchList.hasFocus()) number.requestFocus()
    }

    private fun underline(text: String, range: IntRange?): CharSequence {
        if (range == null || range.first < 0 || range.last >= text.length) return text
        return SpannableString(text).apply {
            setSpan(UnderlineSpan(), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    companion object {
        const val EXTRA_NUMBER = "com.busycall.app.dial_number"
        /** Opened from a call's "Add a call": the first call is already on hold. */
        const val EXTRA_ADD_CALL = "com.busycall.app.add_call"
        /** With EXTRA_ADD_CALL: the held call the new one is linked to. */
        const val EXTRA_HOLD_CALL_ID = "com.busycall.app.hold_call_id"
        /** "Transfer to": the call to transfer. */
        const val EXTRA_TRANSFER_CALL_ID = "com.busycall.app.transfer_call_id"
    }
}
