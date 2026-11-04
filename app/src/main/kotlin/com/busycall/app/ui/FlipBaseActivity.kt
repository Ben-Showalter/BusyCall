package com.busycall.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.CallState
import com.busycall.app.call.CallRouter
import com.busycall.app.call.CallText
import com.busycall.app.call.InCallActivity
import com.busycall.app.call.IncomingCallActivity
import com.busycall.app.data.Prefs
import com.busycall.app.keys.KeyRouter
import com.busycall.app.keys.LogicalKey
import com.tutpro.baresip.R

/**
 * Base for every BusyCall screen: the app-wide key scheme (AGENTS.md §3–§4,
 * Flip-DumbPhoneGuide §1), the footer and the system bar.
 *
 * - **Options is the only softkey.** Right by default. The first MENU press ever
 *   (Sonim's left MENU key) opens Options and moves the label to the left for good,
 *   with no prompt. After that, MENU and SOFT_LEFT open Options; SOFT_RIGHT does nothing.
 *   The other softkey has no label and no job, but its press is consumed.
 * - Every action fires on key **UP**, and only for a press whose DOWN this window saw.
 *   The UP of every consumed DOWN is consumed too.
 * - OK/CENTER is left to the focused view (a row's click). BACK keeps normal back navigation.
 * - Digits go to the focused text field when there is one, otherwise to [onDigit].
 * - Unknown keys are shown on screen so new phones can be learned (guide §7).
 * - END is never consumed: [onEndKeyDown] runs first, then the system does what END does
 *   (PLAN §3.4).
 * - While a call is active, the "On call · 03:42" bar (`bc_oncall`, if the layout has it)
 *   shows on every screen; OK or a tap returns to the call (AGENTS §4).
 *
 * Layouts include `bc_footer` (ids `bc_footer`, `bc_footer_hint`, `bc_footer_options`).
 * Plain [Activity], not AppCompat: no options panel, nothing that grabs MENU.
 */
abstract class FlipBaseActivity : Activity() {

    /** The Options menu for this screen; empty means Options does nothing here. */
    protected open fun optionsItems(): List<MenuItem> = emptyList()

    /** A digit, `*` or `#` released while no text field has focus. */
    protected open fun onDigit(char: Char) {}

    /** A digit, `*` or `#` pressed (first DOWN only), before [onDigit]: keypad tones, hold timers. */
    protected open fun onDigitDown(char: Char) {}

    /** END pressed. Runs before the system's own END handling, which always follows. */
    protected open fun onEndKeyDown() {}

    /**
     * True on screens that use CALL (Dialer, lists of people, call screens). Elsewhere CALL is
     * never touched, so it still reaches the system (guide §1, AGENTS §3).
     */
    protected open val handlesCallKey: Boolean = false

    /** CALL released, on screens where [handlesCallKey] is true. */
    protected open fun onCallKey() {}

    /** Keys a screen wants before the defaults, on UP. Return true if handled. */
    protected open fun onLogicalKeyUp(key: LogicalKey): Boolean = false

    // Keys whose DOWN this window saw and consumed, by keycode.
    private val pressed = HashSet<Int>()

    private val barHandler = Handler(Looper.getMainLooper())
    private var barRunning = false
    private val barTick = object : Runnable {
        override fun run() {
            updateOnCallBar()
            if (barRunning) barHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            // The system T9 keyboard shows its labels in the navigation bar (guide §4).
            if (newFocus is EditText) SystemBars.showNavigation(this) else SystemBars.hideNavigation(this)
        }
    }

    override fun onResume() {
        super.onResume()
        SystemBars.hideNavigation(this)
        placeOptionsLabel()
        if (findViewById<View>(R.id.bc_oncall) != null) {
            barRunning = true
            barHandler.removeCallbacks(barTick)
            barTick.run()
        }
    }

    override fun onPause() {
        pressed.clear()
        barRunning = false
        barHandler.removeCallbacks(barTick)
        super.onPause()
    }

    /** Shows or hides the "On call" bar; OK or a tap on it returns to the call. */
    private fun updateOnCallBar() {
        val bar = findViewById<TextView>(R.id.bc_oncall) ?: return
        val call = Bridges.current.calls().lastOrNull { it.state != CallState.Ended }
        if (call == null) {
            if (bar.visibility != View.GONE) bar.visibility = View.GONE
            return
        }
        val ringing = call.state == CallState.Ringing
        bar.text = if (ringing) getString(R.string.bc_oncall_ringing, call.peerName)
        else getString(R.string.bc_oncall_bar, CallText.duration(call.durationSec), call.peerName)
        bar.setOnClickListener {
            val target = if (ringing) IncomingCallActivity::class.java else InCallActivity::class.java
            startActivity(Intent(this, target).putExtra(CallRouter.EXTRA_CALL_ID, call.id))
        }
        if (bar.visibility != View.VISIBLE) bar.visibility = View.VISIBLE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) SystemBars.hideNavigation(this) else pressed.clear()
    }

    override fun onContentChanged() {
        super.onContentChanged()
        findViewById<TextView>(R.id.bc_footer_options)?.let {
            it.setOnClickListener { openOptions() }
            it.paint.isFakeBoldText = true // textStyle=bold doesn't render with the stock font (guide §4)
        }
        findViewById<TextView>(R.id.bc_title)?.paint?.isFakeBoldText = true
        placeOptionsLabel()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = KeyRouter.route(event.keyCode, event.scanCode)
        val down = event.action == KeyEvent.ACTION_DOWN
        val up = event.action == KeyEvent.ACTION_UP
        val typing = currentFocus is EditText

        if (key == LogicalKey.End && down && event.repeatCount == 0) onEndKeyDown()

        when (key) {
            LogicalKey.Menu -> {
                // Consume the whole press (DOWN, UP, repeats): AppCompat-style panels must never open.
                if (down && event.repeatCount == 0) pressed.add(event.keyCode)
                if (up && pressed.remove(event.keyCode) && !event.isCanceled) {
                    if (!Prefs.hasSeenMenuKey(this)) {
                        Prefs.setSeenMenuKey(this)
                        Prefs.setOptionsOnLeft(this, true)
                        placeOptionsLabel()
                    }
                    openOptions()
                }
                return true
            }
            LogicalKey.SoftLeft, LogicalKey.SoftRight -> {
                if (down && event.repeatCount == 0) pressed.add(event.keyCode)
                if (up && pressed.remove(event.keyCode) && !event.isCanceled) {
                    val optionsKey = if (Prefs.isOptionsOnLeft(this)) LogicalKey.SoftLeft else LogicalKey.SoftRight
                    if (key == optionsKey) openOptions()
                }
                return true
            }
            is LogicalKey.Unknown -> {
                if (down && event.repeatCount == 0) {
                    Toast.makeText(this, getString(R.string.bc_unknown_key, key.keyCode, key.scanCode),
                        Toast.LENGTH_SHORT).show()
                }
                return super.dispatchKeyEvent(event)
            }
            else -> {}
        }

        // Digits (outside text fields) and, on screens that use it, CALL:
        // consume the whole press and act on UP for a DOWN this window saw.
        val ownsKey = when (key) {
            is LogicalKey.Digit -> !typing
            LogicalKey.Call -> handlesCallKey
            else -> false
        }
        if (ownsKey) {
            if (down) {
                if (event.repeatCount == 0) {
                    pressed.add(event.keyCode)
                    if (key is LogicalKey.Digit) onDigitDown(key.char)
                }
                return true
            }
            if (up && pressed.remove(event.keyCode) && !event.isCanceled) {
                if (!onLogicalKeyUp(key)) {
                    when (key) {
                        is LogicalKey.Digit -> onDigit(key.char)
                        LogicalKey.Call -> onCallKey()
                        else -> {}
                    }
                }
            }
            return true // includes the UP of a press that started in another window
        }

        // Everything else keeps its normal behaviour (focus moves, OK clicks, BACK goes back,
        // unhandled CALL/END reach the system); a screen may still claim a key on UP.
        if (up && key != LogicalKey.Ok && key != LogicalKey.End && onLogicalKeyUp(key)) return true
        return super.dispatchKeyEvent(event)
    }

    /** Opens this screen's Options menu (also the footer label's tap action). */
    protected fun openOptions() {
        val items = optionsItems()
        if (items.isEmpty()) return
        Menus.show(this, getString(R.string.bc_options), items)
    }

    /** Puts the footer's "Options" label at the end matching the Options softkey. */
    protected fun placeOptionsLabel() {
        val label = findViewById<View>(R.id.bc_footer_options) ?: return
        val footer = label.parent as? ViewGroup ?: return
        val wanted = if (Prefs.isOptionsOnLeft(this)) 0 else footer.childCount - 1
        if (footer.indexOfChild(label) != wanted) {
            footer.removeView(label)
            footer.addView(label, wanted)
        }
        label.visibility = if (optionsItems().isEmpty()) View.INVISIBLE else View.VISIBLE
    }

    /** Sets the footer's key hint (or hides it). */
    protected fun setFooterHint(text: CharSequence?) {
        val hint = findViewById<TextView>(R.id.bc_footer_hint) ?: return
        hint.text = text ?: ""
        hint.visibility = if (text.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
    }
}
