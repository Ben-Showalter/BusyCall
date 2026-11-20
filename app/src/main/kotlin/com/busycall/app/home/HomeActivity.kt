package com.busycall.app.home

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.busycall.app.bridge.BaresipBridge
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.CallDirection
import com.busycall.app.bridge.NumberProblem
import com.busycall.app.bridge.NumberStatus
import com.busycall.app.bridge.Bridge
import com.busycall.app.call.Calling
import com.busycall.app.calllog.CallDetailsActivity
import com.busycall.app.data.Prefs
import com.busycall.app.contacts.ContactsActivity
import com.busycall.app.dialer.DialerActivity
import com.busycall.app.numbers.DraftExtras
import com.busycall.app.numbers.NumberSetupActivity
import com.busycall.app.numbers.NumbersActivity
import com.busycall.app.settings.SettingsActivity
import com.busycall.app.ui.EndKeySetup
import com.busycall.app.ui.FlipBaseActivity
import com.busycall.app.ui.RowList
import com.busycall.app.ui.MenuItem
import com.busycall.app.ui.RowTone
import com.busycall.app.ui.Rows
import com.busycall.app.ui.Words
import com.tutpro.baresip.R

/**
 * Home (PLAN.md S1) is the call log (owner, 2026-11-20): every call, newest first, with a green or
 * red phone icon, where the number is from and which of the user's numbers it was on. Above it
 * only numbers that can't connect (AGENTS §6). OK opens Call details; CALL (or holding OK) calls
 * back from the number the call was on. BusyCall's launcher screen.
 */
class HomeActivity : FlipBaseActivity() {

    private val viewModel = HomeViewModel()
    private val handler = Handler(Looper.getMainLooper())
    private var lastState: HomeState? = null
    private var resumed = false

    private lateinit var message: TextView
    private lateinit var retry: TextView
    private lateinit var list: RowList<HomeItem>
    private lateinit var callsEmpty: TextView

    // Status changes arrive from baresip asynchronously; a light refresh keeps the screen live.
    private val tick = object : Runnable {
        override fun run() {
            handleContacts()
            render(viewModel.state())
            if (resumed) handler.postDelayed(this, REFRESH_MS)
        }
    }

    override val usesCallHold = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bc_activity_home)
        findViewById<TextView>(R.id.bc_title).setText(R.string.app_name)
        setFooterHint(getString(R.string.bc_home_hint))
        message = findViewById(R.id.bc_home_message)
        retry = findViewById(R.id.bc_home_retry)
        callsEmpty = findViewById(R.id.bc_home_calls_empty)
        list = RowList(
            findViewById(R.id.bc_home_list),
            listOf(R.layout.bc_row, R.layout.bc_call_row),
            layoutOf = { if (it is HomeItem.Call) R.layout.bc_call_row else R.layout.bc_row },
            bind = ::bindRow,
            onClick = ::open,
        )
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

    /** Asks for contacts once at startup, and switches them on whenever they're allowed (AGENTS §7). */
    private fun handleContacts() {
        val bridge = Bridges.current
        when (viewModel.contactsStep(Prefs.wereContactsAsked(this), bridge.hasContactsPermission(this))) {
            ContactsStep.Ask -> {
                Prefs.setContactsAsked(this)
                bridge.turnContactsOn(this) // shows Android's prompt; the answer comes back below
            }
            ContactsStep.SwitchOn -> bridge.turnContactsOn(this)
            ContactsStep.Nothing -> offerEndKey()
            ContactsStep.Wait -> {}
        }
    }

    /** After contacts (guide §5: one item at a time): offer the END-key service once ever. */
    private fun offerEndKey() {
        if (Prefs.wasEndKeyAsked(this) || EndKeySetup.isOn(this)) return
        Prefs.setEndKeyAsked(this)
        EndKeySetup.offer(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Bridge.CONTACTS_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Bridges.current.turnContactsOn(this)
            } else {
                Toast.makeText(this, R.string.bc_contacts_refused, Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode != BaresipBridge.PERMISSIONS_REQUEST) return
        val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        viewModel.permissionsDenied = !allGranted
        if (allGranted) Bridges.current.startService(this)
        render(viewModel.state())
    }

    override fun optionsItems(): List<MenuItem> = listOf(
        MenuItem(getString(R.string.bc_menu_contacts)) { startActivity(Intent(this, ContactsActivity::class.java)) },
        MenuItem(getString(R.string.bc_menu_blocked)) { startActivity(Intent(this, com.busycall.app.blocking.BlockedActivity::class.java)) },
        MenuItem(getString(R.string.bc_menu_numbers)) { startActivity(Intent(this, NumbersActivity::class.java)) },
        MenuItem(getString(R.string.bc_menu_classic)) { Bridges.current.openClassicScreens(this) },
        MenuItem(getString(R.string.bc_menu_settings)) { startActivity(Intent(this, SettingsActivity::class.java)) },
    )

    /** A digit opens the Dialer with it typed (on key UP, guide §1). */
    override fun onDigit(char: Char) {
        startActivity(Intent(this, DialerActivity::class.java).putExtra(DialerActivity.EXTRA_NUMBER, char.toString()))
    }

    private fun focusedCall(): HomeCallRow? = (list.selected() as? HomeItem.Call)?.row

    /** CALL or a held OK on a call calls back; a short OK opens Call details (the row's click). */
    override fun onCallOrOk(held: Boolean, callKey: Boolean): Boolean {
        val row = focusedCall() ?: return false
        if (!held && !callKey) return false
        callBack(row)
        return true
    }

    /** From the number the call was on, no picker (AGENTS §6); emergency numbers follow their rule. */
    private fun callBack(row: HomeCallRow) {
        val (fromId, dial) = viewModel.callBackTarget(row)
        Calling.start(this, dial, row.recent.peerName, held = false, callBackFrom = fromId)
    }

    private fun render(state: HomeState) {
        if (state == lastState) return
        lastState = state

        when (state.problem) {
            HomeProblem.Starting -> showMessage(getString(R.string.bc_home_starting), retryVisible = false)
            HomeProblem.PermissionsDenied -> showMessage(getString(R.string.bc_home_permissions_denied), retryVisible = true)
            null -> showMessage(null, retryVisible = false)
        }

        // Only what can't connect, with its reason (AGENTS §6: never hidden; PLAN S10), then the log.
        val items = buildList<HomeItem> {
            state.trouble.forEach { add(HomeItem.Trouble(it)) }
            if (state.noNumbers && state.problem == null) add(HomeItem.AddNumber)
            state.calls.forEach { add(HomeItem.Call(it)) }
        }
        list.submit(items)
        callsEmpty.visibility = if (state.calls.isEmpty() && state.problem == null) View.VISIBLE else View.GONE

        // Deterministic starting focus (guide §2): the list, only if nothing has focus yet.
        if (items.isNotEmpty()) list.focusIfNothingFocused(currentFocus)
        else if (currentFocus == null && retry.visibility == View.VISIBLE) retry.post { retry.requestFocus() }
    }

    /** OK or a tap on a row. */
    private fun open(item: HomeItem) {
        when (item) {
            is HomeItem.Call -> CallDetailsActivity.open(this, item.row.recent)
            HomeItem.AddNumber -> startActivity(Intent(this, NumbersActivity::class.java))
            is HomeItem.Trouble -> {
                val n = item.row
                if (n.problem == NumberProblem.WrongPassword) {
                    startActivity(Intent(this, NumberSetupActivity::class.java).putExtra(DraftExtras.EXTRA_EDIT_ID, n.id))
                } else if (n.status == NumberStatus.NoConnection) Bridges.current.retry(n.id)
                else startActivity(Intent(this, NumbersActivity::class.java))
            }
        }
    }

    private fun bindRow(row: View, item: HomeItem) {
        when (item) {
            is HomeItem.Call -> bindCall(row, item.row)
            is HomeItem.Trouble -> {
                val n = item.row
                val title = row.findViewById<TextView>(R.id.bc_row_title)
                val subtitle = row.findViewById<TextView>(R.id.bc_row_subtitle)
                Rows.setText(title, getString(R.string.bc_home_trouble, getString(R.string.bc_number_title, n.label, n.number)))
                Words.dot(title, n.status)
                Rows.tone(title, RowTone.Alert)
                Rows.setText(subtitle, n.problem?.let { Words.problem(this, it) } ?: getString(R.string.bc_home_trouble_retry))
                Rows.tone(subtitle, RowTone.Alert)
                Rows.setText(row.findViewById(R.id.bc_row_trailing), null)
            }
            HomeItem.AddNumber -> {
                val title = row.findViewById<TextView>(R.id.bc_row_title)
                Rows.setText(title, getString(R.string.bc_numbers_add))
                title.setCompoundDrawables(null, null, null, null)
                Rows.tone(title, RowTone.Accent)
                Rows.setText(row.findViewById(R.id.bc_row_subtitle), getString(R.string.bc_home_no_numbers))
                Rows.tone(row.findViewById(R.id.bc_row_subtitle), RowTone.Muted)
                Rows.setText(row.findViewById(R.id.bc_row_trailing), null)
            }
        }
    }

    // Built once: rows share these instead of inflating a vector per row.
    private val callIcons by lazy {
        mapOf(
            CallIcon.In to getDrawable(R.drawable.bc_ic_call_in),
            CallIcon.Out to getDrawable(R.drawable.bc_ic_call_out),
            CallIcon.Missed to getDrawable(R.drawable.bc_ic_call_missed),
        )
    }

    /** A call row (bc_call_row): icon, name, "Ohio · to Office · 9:42". */
    private fun bindCall(row: View, item: HomeCallRow) {
        val r = item.recent
        row.findViewById<ImageView>(R.id.bc_call_row_icon).apply {
            setImageDrawable(callIcons[item.icon])
            contentDescription = getString(
                when (item.icon) {
                    CallIcon.In -> R.string.bc_recent_incoming
                    CallIcon.Out -> R.string.bc_recent_outgoing
                    CallIcon.Missed -> R.string.bc_recent_missed
                }
            )
        }
        row.findViewById<TextView>(R.id.bc_call_row_name).apply {
            text = r.peerName
            Rows.tone(this, if (r.missed) RowTone.Alert else RowTone.Normal)
        }
        val onNumber = getString(
            if (r.direction == CallDirection.In) R.string.bc_recent_to else R.string.bc_recent_from,
            r.numberLabel,
        )
        val time = Words.time(this, item.time)
        row.findViewById<TextView>(R.id.bc_call_row_line).text = r.place?.let {
            getString(R.string.bc_recent_line, it.name, onNumber, time)
        } ?: getString(R.string.bc_recent_line_short, onNumber, time)
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

/** One row of Home's list: a number that can't connect, "+ Add a number", or a call. */
sealed class HomeItem {
    data class Trouble(val row: HomeNumberRow) : HomeItem()
    object AddNumber : HomeItem()
    data class Call(val row: HomeCallRow) : HomeItem()
}
