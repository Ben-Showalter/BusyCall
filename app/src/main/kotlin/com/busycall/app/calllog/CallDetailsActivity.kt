package com.busycall.app.calllog

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.CallDirection
import com.busycall.app.bridge.PeerName
import com.busycall.app.bridge.RecentUi
import com.busycall.app.call.Calling
import com.busycall.app.ui.FlipBaseActivity
import com.busycall.app.ui.MenuItem
import com.busycall.app.ui.Menus
import com.busycall.app.ui.PlaceMapView
import com.busycall.app.ui.RowTone
import com.busycall.app.ui.Rows
import com.busycall.app.ui.Words
import com.tutpro.baresip.R

/**
 * Call details (OK on a Home row): the caller, where the number is from with a small map, the
 * call, and Call back · Add to contacts (or Open contact) · Block this number · Delete.
 * CALL calls back. BACK returns to Home.
 */
class CallDetailsActivity : FlipBaseActivity() {

    private lateinit var viewModel: CallDetailsViewModel
    private lateinit var actions: LinearLayout
    private lateinit var callBack: TextView
    private var lastState: CallDetailsState? = null

    override val handlesCallKey = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bc_activity_call_details)
        findViewById<TextView>(R.id.bc_title).setText(R.string.bc_details_title)
        setFooterHint(getString(R.string.bc_details_footer))
        viewModel = CallDetailsViewModel(
            intent.getStringExtra(EXTRA_PEER_URI).orEmpty(),
            intent.getLongExtra(EXTRA_STOP_MILLIS, 0L),
        )
        actions = findViewById(R.id.bc_details_actions)
        callBack = findViewById(R.id.bc_details_call_back)
        callBack.paint.isFakeBoldText = true
        callBack.setOnClickListener { lastState?.let { callBack(it.recent) } }
    }

    override fun onResume() {
        super.onResume()
        // Back from the contact editor: the name may have changed.
        lastState = null
        render()
    }

    override fun onCallKey() {
        lastState?.let { callBack(it.recent) }
    }

    private fun render() {
        val state = viewModel.state()
        if (state == null) {
            finish() // deleted, or the log was cleared
            return
        }
        if (state == lastState) return
        lastState = state
        val r = state.recent

        findViewById<TextView>(R.id.bc_details_name).apply {
            text = r.peerName
            paint.isFakeBoldText = true
            Rows.tone(this, if (r.missed) RowTone.Alert else RowTone.Normal)
        }
        val number = PeerName.phoneNumber(r.peerUri)?.let { PeerName.format(it) }
        Rows.setText(findViewById(R.id.bc_details_number), number?.takeIf { it != r.peerName })
        val kind = getString(
            when {
                r.missed -> R.string.bc_recent_missed
                r.direction == CallDirection.In -> R.string.bc_recent_incoming
                else -> R.string.bc_recent_outgoing
            }
        )
        val onNumber = getString(if (r.direction == CallDirection.In) R.string.bc_recent_to else R.string.bc_recent_from, r.numberLabel)
        findViewById<TextView>(R.id.bc_details_call).text =
            getString(R.string.bc_recent_line, kind, onNumber, Words.time(this, state.time))

        val place = r.place
        Rows.setText(findViewById(R.id.bc_details_place), place?.let { getString(R.string.bc_details_from, it.name) })
        findViewById<PlaceMapView>(R.id.bc_details_map).apply {
            visibility = if (place?.regions?.isNotEmpty() == true) View.VISIBLE else View.GONE
            show(place?.regions ?: emptyList())
        }

        Rows.fill(actions, state.actions) { row, title, _, _, action ->
            Rows.setText(title, getString(labelOf(action)))
            row.setOnClickListener { perform(action, r) }
        }
        if (currentFocus == null) callBack.post { if (currentFocus == null) callBack.requestFocus() }
    }

    private fun labelOf(action: DetailAction) = when (action) {
        DetailAction.AddToContacts -> R.string.bc_details_add_contact
        DetailAction.OpenContact -> R.string.bc_details_open_contact
        DetailAction.Block -> R.string.bc_calllog_block
        DetailAction.Delete -> R.string.bc_calllog_delete
    }

    private fun callBack(r: RecentUi) {
        val (fromId, dial) = viewModel.callBackTarget(r)
        Calling.start(this, dial, r.peerName, held = false, callBackFrom = fromId, onPlaced = { finish() })
    }

    private fun perform(action: DetailAction, r: RecentUi) {
        when (action) {
            DetailAction.AddToContacts -> addToContacts(r)
            DetailAction.OpenContact -> openContact(r)
            DetailAction.Block -> {
                viewModel.block(r)
                Toast.makeText(this, R.string.bc_calllog_blocked, Toast.LENGTH_LONG).show()
            }
            DetailAction.Delete -> {
                viewModel.delete(r)
                finish()
            }
        }
    }

    /** New contact, or add the number to an existing one: both in Android's own contacts app. */
    private fun addToContacts(r: RecentUi) {
        if (!Bridges.current.hasContactsPermission(this)) {
            Toast.makeText(this, R.string.bc_contacts_off, Toast.LENGTH_LONG).show()
            return
        }
        val number = viewModel.contactNumber(r)
        Menus.show(
            this, getString(R.string.bc_details_add_contact),
            listOf(
                MenuItem(getString(R.string.bc_details_new_contact)) {
                    launch(Intent(ContactsContract.Intents.Insert.ACTION)
                        .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                        .putExtra(ContactsContract.Intents.Insert.PHONE, number))
                },
                MenuItem(getString(R.string.bc_details_existing_contact)) {
                    launch(Intent(Intent.ACTION_INSERT_OR_EDIT)
                        .setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                        .putExtra(ContactsContract.Intents.Insert.PHONE, number))
                },
            ),
        )
    }

    private fun openContact(r: RecentUi) {
        val uri = Bridges.current.contactUri(this, r.peerUri)
        if (uri == null) {
            Toast.makeText(this, R.string.bc_details_no_contact, Toast.LENGTH_LONG).show()
            return
        }
        launch(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.bc_details_no_contacts_app, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val EXTRA_PEER_URI = "com.busycall.app.details_peer"
        private const val EXTRA_STOP_MILLIS = "com.busycall.app.details_stop"

        fun open(activity: Activity, recent: RecentUi) {
            activity.startActivity(
                Intent(activity, CallDetailsActivity::class.java)
                    .putExtra(EXTRA_PEER_URI, recent.peerUri)
                    .putExtra(EXTRA_STOP_MILLIS, recent.stopMillis)
            )
        }
    }
}
