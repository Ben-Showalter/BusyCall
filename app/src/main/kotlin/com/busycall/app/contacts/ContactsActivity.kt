package com.busycall.app.contacts

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.busycall.app.bridge.Bridge
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.ContactNumberUi
import com.busycall.app.bridge.ContactUi
import com.busycall.app.bridge.PlaceResult
import com.busycall.app.call.InCallActivity
import com.busycall.app.dialer.DialerActivity
import com.busycall.app.ui.FlipBaseActivity
import com.busycall.app.ui.MenuItem
import com.busycall.app.ui.Menus
import com.busycall.app.ui.RowTone
import com.busycall.app.ui.Rows
import com.tutpro.baresip.R

/**
 * Contacts (PLAN.md S3): the phone's contacts, read through upstream's Contact (AGENTS §7).
 * 2–9 jump to the first name starting with that key's letters; OK shows a contact's numbers;
 * CALL calls its first number from the ★ number. Without the permission: "Contacts are off"
 * and an **Allow contacts** row.
 */
class ContactsActivity : FlipBaseActivity() {

    private val viewModel = ContactsViewModel()
    private lateinit var list: LinearLayout
    private lateinit var hint: TextView
    private var lastState: ContactsState? = null
    private var shown: List<ContactUi> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    // Only while baresip is still starting: contacts appear once it's up.
    private val waitForService = Runnable { render(viewModel.state()) }

    override val handlesCallKey = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bc_activity_list)
        findViewById<TextView>(R.id.bc_title).setText(R.string.bc_contacts_title)
        hint = findViewById(R.id.bc_list_hint)
        list = findViewById(R.id.bc_list)
        setFooterHint(getString(R.string.bc_contacts_footer))
    }

    override fun onResume() {
        super.onResume()
        render(viewModel.state())
    }

    override fun onPause() {
        handler.removeCallbacks(waitForService)
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != Bridge.CONTACTS_REQUEST) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Bridges.current.turnContactsOn(this)
        } else {
            Toast.makeText(this, R.string.bc_contacts_refused, Toast.LENGTH_LONG).show()
        }
        render(viewModel.state())
    }

    override fun optionsItems(): List<MenuItem> = buildList {
        val focused = focusedContact()
        if (focused != null) {
            add(MenuItem(getString(R.string.bc_dialer_call)) { showNumbers(focused) })
            add(MenuItem(getString(R.string.bc_dialer_from_menu)) { openInDialer(focused.numbers.first()) })
        }
        if (lastState?.on == false) add(MenuItem(getString(R.string.bc_contacts_allow)) { allow() })
    }

    private fun focusedContact(): ContactUi? = currentFocus?.getTag(R.id.bc_list) as? ContactUi

    override fun onDigit(char: Char) {
        val index = viewModel.jumpIndex(shown, char) ?: return
        list.getChildAt(index)?.requestFocus()
    }

    override fun onCallKey() {
        val contact = focusedContact() ?: return
        viewModel.firstNumber(contact)?.let { call(it) }
    }

    private fun allow() {
        if (Bridges.current.turnContactsOn(this)) render(viewModel.state())
    }

    private fun render(state: ContactsState) {
        handler.removeCallbacks(waitForService)
        if (!state.running) handler.postDelayed(waitForService, 1000L)
        if (state == lastState) return
        lastState = state
        placeOptionsLabel()
        val message = when {
            !state.running -> getString(R.string.bc_home_starting)
            !state.on -> getString(R.string.bc_contacts_off)
            state.contacts.isEmpty() -> getString(R.string.bc_contacts_empty)
            else -> null
        }
        hint.text = message ?: ""
        hint.visibility = if (message == null) View.GONE else View.VISIBLE

        shown = state.contacts
        val rows = Rows.fill(list, state.contacts) { row, title, subtitle, _, contact ->
            Rows.setText(title, contact.name)
            Rows.setText(subtitle, summary(contact))
            Rows.tone(subtitle, RowTone.Muted)
            row.setTag(R.id.bc_list, contact)
            row.setOnClickListener { showNumbers(contact) }
        }
        if (state.running && !state.on) {
            // Something to press with OK or a tap (guide §2: every screen has a starting focus).
            Rows.fill(list, listOf(Unit)) { row, title, subtitle, _, _ ->
                Rows.setText(title, getString(R.string.bc_contacts_allow))
                Rows.tone(title, RowTone.Accent)
                Rows.setText(subtitle, null)
                row.setOnClickListener { allow() }
            }.forEach { it.post { if (currentFocus == null) it.requestFocus() } }
        } else if (currentFocus == null) {
            rows.firstOrNull()?.let { first -> first.post { if (currentFocus == null) first.requestFocus() } }
        }
    }

    /** "Mobile · +15550198 · +1 more". */
    private fun summary(contact: ContactUi): String {
        val first = contact.numbers.first()
        val line = numberLine(first)
        val more = contact.numbers.size - 1
        return if (more > 0) getString(R.string.bc_contacts_more, line, more) else line
    }

    private fun numberLine(n: ContactNumberUi): String =
        if (n.label.isEmpty()) n.display else getString(R.string.bc_contacts_number_line, n.label, n.display)

    private fun showNumbers(contact: ContactUi) {
        val items = contact.numbers.map { n ->
            MenuItem(getString(R.string.bc_contacts_call_number, numberLine(n))) { call(n) }
        } + MenuItem(getString(R.string.bc_dialer_from_menu)) { openInDialer(contact.numbers.first()) }
        Menus.show(this, contact.name, items)
    }

    /** Calls from the ★ number (AGENTS §6). */
    private fun call(n: ContactNumberUi) {
        when (Bridges.current.placeCall(this, null, n.uri)) {
            PlaceResult.Placed -> startActivity(Intent(this, InCallActivity::class.java))
            is PlaceResult.Failed -> Toast.makeText(this, R.string.bc_dialer_failed, Toast.LENGTH_LONG).show()
        }
    }

    /** "Call from…": the Dialer with the number filled in, to pick which number to call from. */
    private fun openInDialer(n: ContactNumberUi) {
        startActivity(Intent(this, DialerActivity::class.java).putExtra(DialerActivity.EXTRA_NUMBER, n.display))
    }
}
