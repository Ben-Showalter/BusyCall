package com.busycall.app.home

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.busycall.app.bridge.BaresipBridge
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.CallDirection
import com.busycall.app.bridge.NumberStatus
import com.busycall.app.bridge.PlaceResult
import com.busycall.app.call.InCallActivity
import com.busycall.app.calllog.CallLogActivity
import com.busycall.app.contacts.ContactsActivity
import com.busycall.app.dialer.DialerActivity
import com.busycall.app.numbers.NumbersActivity
import com.busycall.app.settings.SettingsActivity
import com.busycall.app.ui.FlipBaseActivity
import com.busycall.app.ui.MenuItem
import com.busycall.app.ui.RowTone
import com.busycall.app.ui.Rows
import com.busycall.app.ui.Words
import com.tutpro.baresip.R

/**
 * Home (PLAN.md S1): the user's numbers with live status and voicemail, then recent calls.
 * BusyCall's launcher screen. Renders [HomeState]; logic is in [HomeViewModel].
 */
class HomeActivity : FlipBaseActivity() {

    private val viewModel = HomeViewModel()
    private val handler = Handler(Looper.getMainLooper())
    private var lastState: HomeState? = null
    private var resumed = false

    private lateinit var message: TextView
    private lateinit var retry: TextView
    private lateinit var numbers: LinearLayout
    private lateinit var recents: LinearLayout
    private lateinit var recentsEmpty: TextView

    // Status changes arrive from baresip asynchronously; a light refresh keeps the screen live.
    private val tick = object : Runnable {
        override fun run() {
            render(viewModel.state())
            if (resumed) handler.postDelayed(this, REFRESH_MS)
        }
    }

    override val handlesCallKey = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bc_activity_home)
        findViewById<TextView>(R.id.bc_title).setText(R.string.app_name)
        setFooterHint(getString(R.string.bc_home_hint))
        message = findViewById(R.id.bc_home_message)
        retry = findViewById(R.id.bc_home_retry)
        numbers = findViewById(R.id.bc_home_numbers)
        recents = findViewById(R.id.bc_home_recents)
        recentsEmpty = findViewById(R.id.bc_home_recents_empty)
        retry.setOnClickListener {
            viewModel.permissionsDenied = false
            Bridges.current.startService(this)
            render(viewModel.state())
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (!viewModel.permissionsDenied && !Bridges.current.isRunning()) Bridges.current.startService(this)
        handler.removeCallbacks(tick)
        tick.run()
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != BaresipBridge.PERMISSIONS_REQUEST) return
        val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        viewModel.permissionsDenied = !allGranted
        if (allGranted) Bridges.current.startService(this)
        render(viewModel.state())
    }

    override fun optionsItems(): List<MenuItem> = listOf(
        MenuItem(getString(R.string.bc_menu_contacts)) { startActivity(Intent(this, ContactsActivity::class.java)) },
        MenuItem(getString(R.string.bc_menu_calllog)) { startActivity(Intent(this, CallLogActivity::class.java)) },
        MenuItem(getString(R.string.bc_menu_numbers)) { startActivity(Intent(this, NumbersActivity::class.java)) },
        MenuItem(getString(R.string.bc_menu_classic)) { Bridges.current.openClassicScreens(this) },
        MenuItem(getString(R.string.bc_menu_settings)) { startActivity(Intent(this, SettingsActivity::class.java)) },
    )

    /** A digit opens the Dialer with it typed (on key UP, guide §1). */
    override fun onDigit(char: Char) {
        startActivity(Intent(this, DialerActivity::class.java).putExtra(DialerActivity.EXTRA_NUMBER, char.toString()))
    }

    /** CALL on a recent call calls back, from the number it was on (AGENTS §6). */
    override fun onCallKey() {
        val recent = currentFocus?.getTag(R.id.bc_row_title) as? HomeRecentRow ?: return
        callBack(recent)
    }

    private fun callBack(recent: HomeRecentRow) {
        when (Bridges.current.placeCall(this, recent.numberId, recent.peerUri)) {
            PlaceResult.Placed -> startActivity(Intent(this, InCallActivity::class.java))
            is PlaceResult.Failed -> Toast.makeText(this, R.string.bc_dialer_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun render(state: HomeState) {
        if (state == lastState) return
        lastState = state

        when (state.problem) {
            HomeProblem.Starting -> showMessage(getString(R.string.bc_home_starting), retryVisible = false)
            HomeProblem.PermissionsDenied -> showMessage(getString(R.string.bc_home_permissions_denied), retryVisible = true)
            null -> showMessage(null, retryVisible = false)
        }

        Rows.fill(numbers, state.numbers) { row, title, subtitle, trailing, item ->
            Rows.setText(title, getString(R.string.bc_number_title, item.label, item.number))
            Words.dot(title, item.status)
            Rows.setText(subtitle, null)
            val voicemail = if (item.voicemailNew > 0)
                resources.getQuantityString(R.plurals.bc_voicemail_count, item.voicemailNew, item.voicemailNew) else null
            Rows.setText(trailing, voicemail ?: Words.status(this, item.status))
            Rows.tone(trailing, if (voicemail != null) RowTone.Accent else Words.statusTone(item.status))
            row.setOnClickListener {
                if (item.status == NumberStatus.NoConnection) Bridges.current.retry(item.id)
                else startActivity(Intent(this, NumbersActivity::class.java))
            }
        }
        if (state.numbers.isEmpty() && state.problem == null) {
            // An empty home still has one focusable thing to do.
            Rows.fill(numbers, listOf(Unit)) { row, title, subtitle, _, _ ->
                Rows.setText(title, getString(R.string.bc_numbers_add))
                Rows.tone(title, RowTone.Accent)
                Rows.setText(subtitle, getString(R.string.bc_home_no_numbers))
                row.setOnClickListener { startActivity(Intent(this, NumbersActivity::class.java)) }
            }
        }

        Rows.fill(recents, state.recents) { row, title, subtitle, _, item ->
            Rows.setText(title, item.peerName)
            Rows.tone(title, if (item.missed) RowTone.Alert else RowTone.Normal)
            val kind = getString(
                when {
                    item.missed -> R.string.bc_recent_missed
                    item.direction == CallDirection.In -> R.string.bc_recent_incoming
                    else -> R.string.bc_recent_outgoing
                }
            )
            val onNumber = getString(
                if (item.direction == CallDirection.In) R.string.bc_recent_to else R.string.bc_recent_from,
                item.numberLabel,
            )
            val time = Words.time(this, item.time)
            Rows.setText(subtitle, getString(R.string.bc_recent_line, kind, onNumber, time))
            row.setTag(R.id.bc_row_title, item)
            row.setOnClickListener { callBack(item) }
        }
        recentsEmpty.visibility = if (state.recents.isEmpty() && state.problem == null) View.VISIBLE else View.GONE

        // Deterministic starting focus (guide §2): the first row, only if nothing has focus yet.
        if (currentFocus == null) {
            val first = (0 until numbers.childCount).map { numbers.getChildAt(it) }.firstOrNull()
                ?: (if (retry.visibility == View.VISIBLE) retry else null)
            first?.post { if (currentFocus == null) first.requestFocus() }
        }
    }

    private fun showMessage(text: String?, retryVisible: Boolean) {
        message.text = text ?: ""
        message.visibility = if (text == null) View.GONE else View.VISIBLE
        retry.visibility = if (retryVisible) View.VISIBLE else View.GONE
    }

    companion object {
        private const val REFRESH_MS = 1000L
    }
}
