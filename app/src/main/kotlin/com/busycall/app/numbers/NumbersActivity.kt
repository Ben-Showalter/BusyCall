package com.busycall.app.numbers

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.busycall.app.numbers.setup.ScanActivity
import com.busycall.app.numbers.setup.SetupCodeParser
import com.busycall.app.numbers.setup.SetupFoundActivity
import com.busycall.app.numbers.setup.SetupHelpActivity
import com.busycall.app.settings.SettingsActivity
import com.busycall.app.ui.FlipBaseActivity
import com.busycall.app.ui.MenuItem
import com.busycall.app.ui.Menus
import com.busycall.app.ui.RowTone
import com.busycall.app.ui.Rows
import com.busycall.app.ui.Words
import com.tutpro.baresip.R

/**
 * Numbers (PLAN.md S7): every SIP number with its status, ★ the default, "Phone (SIM)"
 * (PLAN §5.2) and "+ Add a number" (Type it in / Paste setup link / Scan QR setup code).
 */
class NumbersActivity : FlipBaseActivity() {

    private val viewModel = NumbersViewModel()
    private val handler = Handler(Looper.getMainLooper())
    private var lastState: NumbersState? = null
    private var resumed = false
    private lateinit var list: LinearLayout

    private val tick = object : Runnable {
        override fun run() {
            render(viewModel.state(hasTelephony()))
            if (resumed) handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bc_activity_list)
        findViewById<TextView>(R.id.bc_title).setText(R.string.bc_numbers_title)
        findViewById<TextView>(R.id.bc_list_hint).apply {
            setText(R.string.bc_numbers_hint)
            visibility = View.VISIBLE
        }
        setFooterHint(getString(R.string.bc_numbers_footer))
        list = findViewById(R.id.bc_list)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        lastState = null
        handler.removeCallbacks(tick)
        tick.run()
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun optionsItems(): List<MenuItem> = listOf(
        MenuItem(getString(R.string.bc_numbers_add)) { showAddMenu() },
        MenuItem(getString(R.string.bc_menu_settings)) { startActivity(Intent(this, SettingsActivity::class.java)) },
    )

    private fun hasTelephony() = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    private sealed class Entry {
        data class Number(val row: NumbersRow) : Entry()
        object Sim : Entry()
        object Add : Entry()
    }

    private fun render(state: NumbersState) {
        if (state == lastState) return
        lastState = state
        val entries = buildList {
            state.numbers.forEach { add(Entry.Number(it)) }
            if (state.showSim) add(Entry.Sim)
            add(Entry.Add)
        }
        Rows.fill(list, entries) { row, title, subtitle, _, entry ->
            when (entry) {
                is Entry.Number -> {
                    val n = entry.row
                    val label = getString(R.string.bc_number_title, n.label, n.number)
                    Rows.setText(title, if (n.isDefault) getString(R.string.bc_number_default, label) else label)
                    Words.dot(title, n.status)
                    // While it can't connect, the reason in plain words replaces the details (PLAN S10).
                    Rows.setText(subtitle, n.problem?.let { Words.problem(this, it) } ?: getString(
                        R.string.bc_number_details, n.server, Words.transport(this, n.transport), Words.status(this, n.status),
                    ))
                    Rows.tone(subtitle, if (n.problem != null) RowTone.Alert else Words.statusTone(n.status))
                    row.setOnClickListener { showNumberMenu(n) }
                }
                Entry.Sim -> {
                    Rows.setText(title, getString(R.string.bc_sim_title))
                    Words.dot(title, null)
                    Rows.setText(subtitle, getString(R.string.bc_sim_subtitle))
                    Rows.tone(subtitle, RowTone.Muted)
                    row.setOnClickListener { openPhoneDialer() }
                }
                Entry.Add -> {
                    Rows.setText(title, getString(R.string.bc_numbers_add))
                    Words.dot(title, null)
                    Rows.tone(title, RowTone.Accent)
                    Rows.setText(subtitle, if (state.running) null else getString(R.string.bc_home_starting))
                    row.setOnClickListener { showAddMenu() }
                }
            }
        }
        if (currentFocus == null && list.childCount > 0) {
            val first = list.getChildAt(0)
            first.post { if (currentFocus == null) first.requestFocus() }
        }
    }

    private fun showNumberMenu(row: NumbersRow) {
        val items = viewModel.actionsFor(row).map { action ->
            when (action) {
                NumberAction.Edit -> MenuItem(getString(R.string.bc_number_edit)) {
                    startActivity(Intent(this, NumberSetupActivity::class.java).putExtra(DraftExtras.EXTRA_EDIT_ID, row.id))
                }
                NumberAction.MakeDefault -> MenuItem(getString(R.string.bc_number_make_default)) {
                    viewModel.perform(action, row)
                }
                NumberAction.Retry -> MenuItem(getString(R.string.bc_retry)) { viewModel.perform(action, row) }
                NumberAction.Delete -> MenuItem(getString(R.string.bc_number_delete)) {
                    Menus.confirm(
                        this,
                        getString(R.string.bc_number_delete_title),
                        getString(R.string.bc_number_delete_message, row.label),
                        getString(R.string.bc_number_delete),
                    ) {
                        viewModel.perform(action, row)
                        lastState = null
                        tick.run()
                    }
                }
            }
        }
        Menus.show(this, getString(R.string.bc_number_title, row.label, row.number), items)
    }

    /** "+ Add a number" (PLAN S7, as in the preview): icon, label and a short hint per row. */
    private fun showAddMenu() {
        Menus.show(this, getString(R.string.bc_numbers_add_title), buildList {
            add(MenuItem(getString(R.string.bc_add_type), getString(R.string.bc_add_type_sub), R.drawable.bc_ic_edit) {
                startActivity(Intent(this@NumbersActivity, NumberSetupActivity::class.java))
            })
            add(MenuItem(getString(R.string.bc_add_paste), getString(R.string.bc_add_paste_sub), R.drawable.bc_ic_link) {
                pasteSetupLink()
            })
            // Hidden on phones without a camera (AGENTS §6a).
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                add(MenuItem(getString(R.string.bc_add_scan), getString(R.string.bc_add_scan_sub), R.drawable.bc_ic_qr) {
                    askCodeReady()
                })
            }
        })
    }

    /** S7b: always ask first; "No" opens Make a setup code, never the camera (AGENTS §6a). */
    private fun askCodeReady() {
        Menus.show(this, getString(R.string.bc_add_scan), listOf(
            MenuItem(getString(R.string.bc_scan_ready_yes)) { startActivity(Intent(this, ScanActivity::class.java)) },
            MenuItem(getString(R.string.bc_scan_ready_no)) { startActivity(Intent(this, SetupHelpActivity::class.java)) },
        ), message = getString(R.string.bc_scan_ready_title))
    }

    private fun pasteSetupLink() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        when (val outcome = viewModel.paste(text)) {
            is PasteOutcome.Found -> {
                // The code holds a password: don't leave it on the clipboard (AGENTS §6a).
                try {
                    clipboard.clearPrimaryClip()
                } catch (e: Exception) {
                    // Some OEM builds refuse; nothing else to do.
                }
                startActivity(DraftExtras.put(Intent(this, SetupFoundActivity::class.java), outcome.draft))
            }
            PasteOutcome.NothingCopied -> Menus.notice(this, getString(R.string.bc_add_paste), getString(R.string.bc_paste_nothing))
            PasteOutcome.NotACode -> Menus.notice(this, getString(R.string.bc_add_paste), getString(R.string.bc_paste_not_a_code))
            PasteOutcome.TooNew -> Menus.notice(this, getString(R.string.bc_add_paste), getString(R.string.bc_paste_too_new))
            is PasteOutcome.Incomplete -> Menus.notice(this, getString(R.string.bc_add_paste), getString(
                if (outcome.missing == SetupCodeParser.Field.Server) R.string.bc_paste_no_server else R.string.bc_paste_no_user,
            ))
        }
    }

    /** "Phone (SIM)": hands off to the phone's own dialer (ACTION_DIAL, no permission; PLAN §5.2). */
    private fun openPhoneDialer() {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.bc_sim_no_dialer, Toast.LENGTH_LONG).show()
        }
    }
}
