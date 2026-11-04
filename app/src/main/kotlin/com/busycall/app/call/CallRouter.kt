package com.busycall.app.call

import android.app.Activity
import android.content.Intent
import com.busycall.app.bridge.Bridge
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.CallState
import com.busycall.app.home.HomeActivity

/**
 * Sends upstream's call events to BusyCall's screens.
 *
 * baresip-studio opens its `MainActivity` for every call event, with an `"action"` extra
 * (`call show` from the ring notification, `call answer`, `call missed`) or as MAIN from its
 * status notification. One hook at the top of `MainActivity.onCreate`/`onNewIntent` (UPSTREAM.md)
 * calls [route]; when it returns true, `MainActivity` finishes and BusyCall's screen is shown.
 * Anything else (messages, transfers, Classic screens) stays with upstream.
 */
object CallRouter {

    enum class Route { Incoming, InCall, Home, Upstream }

    /** Pure decision, unit-tested. [callState] is the state of the call the intent names, if any. */
    fun decide(extraAction: String?, intentAction: String?, callState: CallState?, anyActiveCall: Boolean): Route =
        when (extraAction) {
            "call show" -> when (callState) {
                CallState.Ringing -> Route.Incoming
                null, CallState.Ended -> Route.Home // gone already: it shows as missed in Recent
                else -> Route.InCall
            }
            "call answer" -> if (callState == null || callState == CallState.Ended) Route.Home else Route.InCall
            "call missed" -> Route.Home
            null -> if (intentAction == Intent.ACTION_MAIN) {
                if (anyActiveCall) Route.InCall else Route.Home
            } else Route.Upstream
            else -> Route.Upstream
        }

    /** Returns true if BusyCall took the intent (the caller then finishes). */
    @JvmStatic
    fun route(activity: Activity, intent: Intent?, bridge: Bridge = Bridges.current): Boolean {
        if (intent == null) return false
        val extraAction = intent.getStringExtra("action")
        val callId = intent.getLongExtra("callp", 0L)
        val calls = bridge.calls()
        val call = calls.firstOrNull { it.id == callId }
        val route = decide(extraAction, intent.action, call?.state, calls.any { it.state != CallState.Ended })
        if (route == Route.Upstream) return false

        if (extraAction == "call answer" && call?.state == CallState.Ringing) bridge.answer(activity, callId)
        val target = when (route) {
            Route.Incoming -> Intent(activity, IncomingCallActivity::class.java)
            Route.InCall -> Intent(activity, InCallActivity::class.java)
            else -> Intent(activity, HomeActivity::class.java)
        }
        if (call != null) target.putExtra(EXTRA_CALL_ID, call.id)
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        activity.startActivity(target)
        return true
    }

    const val EXTRA_CALL_ID = "com.busycall.app.call_id"
}
