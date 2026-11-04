package com.busycall.app.numbers

import android.content.Intent
import com.busycall.app.bridge.NumberDraft
import com.busycall.app.bridge.Transport

/** Passes a [NumberDraft] between BusyCall's own screens (in-process only; never logged). */
object DraftExtras {
    private const val P = "com.busycall.app.draft."
    const val EXTRA_EDIT_ID = "com.busycall.app.edit_id"

    fun put(intent: Intent, draft: NumberDraft): Intent = intent
        .putExtra(P + "name", draft.name)
        .putExtra(P + "server", draft.server)
        .putExtra(P + "port", draft.port ?: -1)
        .putExtra(P + "user", draft.user)
        .putExtra(P + "auth", draft.authUser)
        .putExtra(P + "pass", draft.password)
        .putExtra(P + "transport", draft.transport.param)
        .putExtra(P + "vm", draft.voicemail)

    fun get(intent: Intent): NumberDraft? {
        if (!intent.hasExtra(P + "server")) return null
        return NumberDraft(
            name = intent.getStringExtra(P + "name").orEmpty(),
            server = intent.getStringExtra(P + "server").orEmpty(),
            port = intent.getIntExtra(P + "port", -1).takeIf { it > 0 },
            user = intent.getStringExtra(P + "user").orEmpty(),
            authUser = intent.getStringExtra(P + "auth").orEmpty(),
            password = intent.getStringExtra(P + "pass").orEmpty(),
            transport = Transport.fromParam(intent.getStringExtra(P + "transport")) ?: Transport.UDP,
            voicemail = intent.getStringExtra(P + "vm").orEmpty(),
        )
    }
}
