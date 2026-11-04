package com.busycall.app.dialer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.PlaceProblem
import com.busycall.app.call.InCallActivity
import com.busycall.app.keys.HoldTimer
import com.busycall.app.keys.LogicalKey
import com.busycall.app.ui.FlipBaseActivity
import com.busycall.app.ui.MenuItem
import com.busycall.app.ui.Menus
import com.busycall.app.ui.Rows
import com.tutpro.baresip.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Dialer (PLAN.md S2, basic). Digits type the number; hold 0 for `+`; Clear (BACK) deletes one
 * digit, and leaves when the number is empty. ◀ ▶ on "Call from" pick the number to call from,
 * including Phone (SIM). CALL or the Call button places the call; with an empty number CALL
 * fills in the last number called.
 *
 * Under the number, up to 3 contacts whose number contains the digits or whose name the keys
 * spell (PLAN §3.3), the matched part underlined. ▼ moves into them; OK or CALL on one calls it.
 * The index is built once and queried off the main thread.
 */
class DialerActivity : FlipBaseActivity() {

    private lateinit var viewModel: DialerViewModel
    private val holdTimer = HoldTimer()
    private val handler = Handler(Looper.getMainLooper())
    private val holdFired = Runnable {
        holdTimer.fire()?.let { viewModel.type(it); render() }
    }

    private lateinit var number: TextView
    private lateinit var from: View
    private lateinit var fromValue: TextView
    private lateinit var error: TextView
    private lateinit var matchList: LinearLayout

    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    @Volatile private var matcher: KeypadMatcher? = null
    private var shownFor: String? = null

    override val handlesCallKey = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bc_activity_dialer)
        findViewById<TextView>(R.id.bc_title).setText(R.string.bc_dialer_title)
        setFooterHint(getString(R.string.bc_dialer_footer))
        viewModel = DialerViewModel(hasTelephony = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
        number = findViewById(R.id.bc_dialer_number)
        from = findViewById(R.id.bc_dialer_from)
        fromValue = findViewById(R.id.bc_dialer_from_value)
        error = findViewById(R.id.bc_dialer_error)
        matchList = findViewById(R.id.bc_dialer_matches)
        number.setOnClickListener { placeCall() }
        val call = findViewById<TextView>(R.id.bc_dialer_call)
        call.paint.isFakeBoldText = true
        call.setOnClickListener { placeCall() }
        from.setOnClickListener { chooseFrom() }
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

    override fun optionsItems(): List<MenuItem> = listOf(
        MenuItem(getString(R.string.bc_dialer_call)) { placeCall() },
        MenuItem(getString(R.string.bc_dialer_from_menu)) { chooseFrom() },
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

    override fun onCallKey() {
        val match = currentFocus?.getTag(R.id.bc_dialer_matches) as? DialMatch
        if (match != null) callMatch(match) else placeCall()
    }

    override fun onLogicalKeyUp(key: LogicalKey): Boolean = when (key) {
        // Clear arrives as BACK on Kyocera: delete a digit; leave only when empty (AGENTS §4).
        LogicalKey.Back, LogicalKey.Delete -> if (viewModel.delete()) { render(); true } else false
        LogicalKey.Left -> if (currentFocus == from) { viewModel.previousFrom(); render(); true } else false
        LogicalKey.Right -> if (currentFocus == from) { viewModel.nextFrom(); render(); true } else false
        else -> false
    }

    private fun chooseFrom() {
        val choices = viewModel.fromChoices()
        if (choices.isEmpty()) return
        Menus.show(this, getString(R.string.bc_dialer_from_menu), choices.mapIndexed { i, c ->
            MenuItem(fromLabel(c)) { viewModel.chooseFrom(i); render() }
        })
    }

    private fun fromLabel(c: FromChoice): String =
        if (c.id == null) getString(R.string.bc_sim_title) else getString(R.string.bc_number_title, c.label, c.number)

    private fun placeCall() = handle(viewModel.call { fromId, dial -> Bridges.current.placeCall(this, fromId, dial) })

    private fun callMatch(match: DialMatch) =
        handle(viewModel.callMatch(match) { fromId, dial -> Bridges.current.placeCall(this, fromId, dial) })

    private fun handle(outcome: DialOutcome) {
        when (outcome) {
            DialOutcome.Placed -> {
                startActivity(Intent(this, InCallActivity::class.java))
                finish()
            }
            is DialOutcome.PhoneDialer -> try {
                // Phone (SIM): the phone's own dialer, number filled in (PLAN §5.2). On key UP.
                startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", outcome.number, null)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.bc_sim_no_dialer, Toast.LENGTH_LONG).show()
            }
            DialOutcome.Redialed -> render()
            DialOutcome.NothingToCall -> showError(getString(R.string.bc_dialer_type_number))
            is DialOutcome.Failed -> showError(getString(
                when (outcome.problem) {
                    PlaceProblem.ServiceNotRunning -> R.string.bc_problem_not_running
                    PlaceProblem.NoNumber -> R.string.bc_dialer_no_numbers
                    PlaceProblem.InvalidAddress -> R.string.bc_dialer_invalid
                    PlaceProblem.PhoneBusy -> R.string.bc_dialer_phone_busy
                    PlaceProblem.Failed -> R.string.bc_dialer_failed
                }
            ))
        }
    }

    private fun showError(text: String) {
        error.text = text
        error.visibility = View.VISIBLE
    }

    private fun render() {
        val state = viewModel.state()
        number.text = state.number
        val choice = state.from
        val label = if (choice == null) getString(R.string.bc_dialer_no_numbers) else fromLabel(choice)
        fromValue.text = if (state.canChangeFrom) getString(R.string.bc_dialer_from_arrows, label) else label
        updateMatches()
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
            row.setOnClickListener { callMatch(m) }
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
    }
}
