package com.busycall.app.numbers

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.busycall.app.bridge.NumberDraft
import com.busycall.app.bridge.PlusRule
import com.busycall.app.bridge.SaveProblem
import com.busycall.app.bridge.SaveResult
import com.busycall.app.bridge.Transport
import com.busycall.app.keys.KeyRouter
import com.busycall.app.keys.LogicalKey
import com.busycall.app.ui.FlipBaseActivity
import com.busycall.app.ui.MenuItem
import com.busycall.app.ui.Menus
import com.busycall.app.ui.Words
import com.tutpro.baresip.R

/**
 * Number setup (PLAN.md S8): type a number's settings in, or edit an existing one
 * ([DraftExtras.EXTRA_EDIT_ID]), or finish a setup code ([DraftExtras] draft).
 * Nothing is saved until "Save and connect".
 */
class NumberSetupActivity : FlipBaseActivity() {

    private val viewModel = NumberSetupViewModel()
    private var editId: String? = null
    private var transport = Transport.UDP
    private var plusRule: PlusRule = PlusRule.DropPlus
    private var showPassword = false

    private lateinit var error: TextView
    private lateinit var name: EditText
    private lateinit var server: EditText
    private lateinit var port: EditText
    private lateinit var user: EditText
    private lateinit var password: EditText
    private lateinit var authUser: EditText
    private lateinit var voicemail: EditText
    private lateinit var transportValue: TextView
    private lateinit var plusValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bc_activity_number_setup)
        editId = intent.getStringExtra(DraftExtras.EXTRA_EDIT_ID)
        findViewById<TextView>(R.id.bc_title).setText(if (editId != null) R.string.bc_setup_edit_title else R.string.bc_setup_title)

        error = findViewById(R.id.bc_setup_error)
        name = findViewById(R.id.bc_setup_name)
        server = findViewById(R.id.bc_setup_server)
        port = findViewById(R.id.bc_setup_port)
        user = findViewById(R.id.bc_setup_user)
        password = findViewById(R.id.bc_setup_password)
        authUser = findViewById(R.id.bc_setup_auth_user)
        voicemail = findViewById(R.id.bc_setup_voicemail)
        transportValue = findViewById(R.id.bc_setup_transport_value)
        plusValue = findViewById(R.id.bc_setup_plus_value)

        val draft = viewModel.start(editId, DraftExtras.get(intent))
        name.setText(draft.name)
        server.setText(draft.server)
        port.setText(draft.port?.toString() ?: "")
        user.setText(draft.user)
        password.setText(draft.password)
        authUser.setText(draft.authUser)
        voicemail.setText(draft.voicemail)
        transport = draft.transport
        showTransport()
        plusRule = draft.plusRule
        showPlusRule()
        // Editing: an empty password means "keep the saved one".
        findViewById<View>(R.id.bc_setup_password_note).visibility =
            if (editId != null && draft.password.isEmpty()) View.VISIBLE else View.GONE

        // The keypad has no dot: in the server field `*` types "." (guide §3, IP fields).
        server.setOnKeyListener { _, _, event ->
            val key = KeyRouter.route(event.keyCode, event.scanCode)
            if (key == LogicalKey.Digit('*') && event.action == KeyEvent.ACTION_DOWN) {
                val at = server.selectionStart.coerceAtLeast(0)
                server.text.insert(at, ".")
                true
            } else key == LogicalKey.Digit('*') // swallow the matching UP
        }

        findViewById<View>(R.id.bc_setup_transport).setOnClickListener { chooseTransport() }
        findViewById<View>(R.id.bc_setup_plus).setOnClickListener { choosePlusRule() }
        findViewById<View>(R.id.bc_setup_save).setOnClickListener { save() }
        findViewById<View>(R.id.bc_setup_more).setOnClickListener { openOptions() }
        findViewById<TextView>(R.id.bc_setup_save).paint.isFakeBoldText = true

        // Starting focus: the first empty required field, else Save.
        val start = listOf(server, user).firstOrNull { it.text.isNullOrBlank() } ?: findViewById(R.id.bc_setup_save)
        start.post { if (currentFocus == null || currentFocus == name) start.requestFocus() }
    }

    override fun optionsItems(): List<MenuItem> = listOf(
        MenuItem(getString(R.string.bc_save_connect)) { save() },
        MenuItem(getString(if (showPassword) R.string.bc_hide_password else R.string.bc_show_password)) {
            showPassword = !showPassword
            val at = password.selectionStart
            password.inputType = InputType.TYPE_CLASS_TEXT or
                if (showPassword) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
            if (at >= 0) password.setSelection(at.coerceAtMost(password.text.length))
        },
        MenuItem(getString(R.string.bc_field_connection)) { chooseTransport() },
        MenuItem(getString(R.string.bc_field_plus)) { choosePlusRule() },
    )

    /** "Numbers starting with +": many phone systems don't route a leading + (docs/PBX-NOTES.md). */
    private fun choosePlusRule() {
        Menus.show(this, getString(R.string.bc_field_plus), listOf(
            MenuItem(getString(R.string.bc_plus_drop), getString(R.string.bc_plus_drop_sub)) {
                plusRule = PlusRule.DropPlus
                showPlusRule()
            },
            MenuItem(getString(R.string.bc_plus_keep), getString(R.string.bc_plus_keep_sub)) {
                plusRule = PlusRule.AsIs
                showPlusRule()
            },
            MenuItem(getString(R.string.bc_plus_replace), getString(R.string.bc_plus_replace_sub)) { askPlusPrefix() },
        ))
    }

    /** The prefix for "Replace + with…": digits only (the phone's keypad, no keyboard needed). */
    private fun askPlusPrefix() {
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
            setText((plusRule as? PlusRule.ReplaceWith)?.prefix ?: "")
            hint = getString(R.string.bc_plus_prefix_hint)
            setTextColor(getColor(R.color.bc_text))
            setHintTextColor(getColor(R.color.bc_text_muted))
        }
        AlertDialog.Builder(this, R.style.BcDialog)
            .setTitle(R.string.bc_plus_replace)
            .setView(field)
            .setPositiveButton(R.string.bc_ok) { _, _ ->
                val prefix = field.text.toString().filter { it.isDigit() }
                plusRule = if (prefix.isEmpty()) PlusRule.DropPlus else PlusRule.ReplaceWith(prefix)
                showPlusRule()
            }
            .setNegativeButton(R.string.bc_cancel, null)
            .show()
    }

    private fun showPlusRule() {
        plusValue.text = Words.plusRule(this, plusRule)
    }

    private fun chooseTransport() {
        Menus.show(this, getString(R.string.bc_field_connection), Transport.values().map { t ->
            MenuItem(Words.transport(this, t)) {
                transport = t
                showTransport()
            }
        })
    }

    private fun showTransport() {
        transportValue.text = Words.transport(this, transport)
    }

    private fun currentDraft() = NumberDraft(
        name = name.text.toString(),
        server = server.text.toString(),
        port = null, // parsed by the view model from the raw text
        user = user.text.toString(),
        authUser = authUser.text.toString(),
        password = password.text.toString(),
        transport = transport,
        voicemail = voicemail.text.toString(),
        plusRule = plusRule,
    )

    private fun save() {
        when (val result = viewModel.save(editId, currentDraft(), port.text.toString())) {
            is SaveResult.Saved -> {
                Toast.makeText(this, R.string.bc_saved_connecting, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            is SaveResult.Failed -> showProblem(result.problem)
        }
    }

    private fun showProblem(problem: SaveProblem) {
        error.text = getString(
            when (problem) {
                SaveProblem.ServiceNotRunning -> R.string.bc_problem_not_running
                SaveProblem.InvalidServer -> R.string.bc_problem_server
                SaveProblem.InvalidPort -> R.string.bc_problem_port
                SaveProblem.InvalidUser -> R.string.bc_problem_user
                SaveProblem.InvalidPassword -> R.string.bc_problem_password
                SaveProblem.InvalidName -> R.string.bc_problem_name
                SaveProblem.NameTaken -> R.string.bc_problem_name_taken
                SaveProblem.AlreadyExists -> R.string.bc_problem_exists
                SaveProblem.AllocationFailed -> R.string.bc_problem_allocation
            }
        )
        error.visibility = View.VISIBLE
        val field = when (viewModel.fieldOf(problem)) {
            SetupField.Name -> name
            SetupField.Server -> server
            SetupField.Port -> port
            SetupField.User -> user
            SetupField.AuthUser -> authUser
            SetupField.Password -> password
            SetupField.Voicemail -> voicemail
            SetupField.None -> findViewById<View>(R.id.bc_setup_save)
        }
        field.requestFocus()
        // The message sits at the top and may be scrolled away from the field: say it once more.
        Toast.makeText(this, error.text, Toast.LENGTH_LONG).show()
    }
}
