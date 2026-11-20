package com.busycall.app.call

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.busycall.app.bridge.Bridge
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.CallState
import com.busycall.app.dialer.DialerActivity
import com.busycall.app.home.HomeActivity
import com.busycall.app.ui.CallScreens

/**
 * Sends upstream's call events to BusyCall's screens.
 *
 * baresip-studio opens its `MainActivity` for every call event, with an `"action"` extra
 * (`call show` from the ring notification, `call answer`, `call missed`) or as MAIN from its
 * status notification. One hook at the top of `MainActivity.onCreate`/`onNewIntent` (UPSTREAM.md)
 * calls [route]; when it returns true, `MainActivity` finishes and BusyCall's screen is shown.
 * The phone's own dial requests (DIAL / CALL / VIEW with `tel:` or `sip:`) open BusyCall's Dialer,
 * and the call-log VIEW opens its Call log. Anything else (messages, Classic screens) stays
 * with upstream.
 */
object CallRouter {

    enum class Route { Incoming, InCall, Home, Dialer, Upstream }

    /** Pure decision, unit-tested. [callState] is the state of the call the intent names, if any. */
    fun decide(
        extraAction: String?,
        intentAction: String?,
        callState: CallState?,
        anyActiveCall: Boolean,
        dataScheme: String? = null,
        mimeType: String? = null,
    ): Route =
        when (extraAction) {
            "call show" -> when (callState) {
                CallState.Ringing -> Route.Incoming
                null, CallState.Ended -> Route.Home // gone already: it shows as missed in Recent
                else -> Route.InCall
            }
            "call answer" -> if (callState == null || callState == CallState.Ended) Route.Home else Route.InCall
            "call missed" -> Route.Home
            null -> when {
                intentAction == Intent.ACTION_MAIN -> if (anyActiveCall) Route.InCall else Route.Home
                // The phone's own dial requests (home-screen digits, tel: links, other apps' "call"):
                // BusyCall's Dialer with the number filled in. Never calls by itself.
                intentAction == Intent.ACTION_DIAL && dataScheme == null -> Route.Dialer
                intentAction in DIAL_ACTIONS && (dataScheme == "tel" || dataScheme == "sip") -> Route.Dialer
                // "Show the call log" (other apps, the stock launcher): Home is the call log.
                intentAction == Intent.ACTION_VIEW && mimeType == CALLS_MIME -> Route.Home
                else -> Route.Upstream
            }
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
        val route = decide(extraAction, intent.action, call?.state, calls.any { it.state != CallState.Ended },
            intent.data?.scheme?.lowercase(), intent.type)
        if (route == Route.Upstream) return false

        if (extraAction == "call answer" && call?.state == CallState.Ringing) bridge.answer(activity, callId)
        val target = when (route) {
            Route.Incoming -> Intent(activity, IncomingCallActivity::class.java)
            Route.InCall -> Intent(activity, InCallActivity::class.java)
            Route.Dialer -> Intent(activity, DialerActivity::class.java).also { dial ->
                dialNumber(intent)?.let { dial.putExtra(DialerActivity.EXTRA_NUMBER, it) }
            }
            else -> Intent(activity, HomeActivity::class.java)
        }
        if (call != null) target.putExtra(EXTRA_CALL_ID, call.id)
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        activity.startActivity(target)
        return true
    }

    /**
     * An incoming call has just started ringing (one hook line in upstream's
     * `BaresipService.handleIncomingCall`, UPSTREAM.md): show BusyCall's Incoming screen in front
     * of any app (owner, 2026-11-20). Android 9 lets the service start it; on Android 10+ it needs
     * "Appear on top", and otherwise upstream's full-screen notification remains the way in.
     */
    @JvmStatic
    fun onIncomingCall(context: Context, callp: Long, bridge: Bridge = Bridges.current) {
        // Only BusyCall's own (SIP) calls; Phone (SIM) calls stay with the phone (PLAN §5.2).
        if (bridge.calls().none { it.id == callp && it.state == CallState.Ringing }) return
        if (Build.VERSION.SDK_INT >= 29 && !Settings.canDrawOverlays(context)) return
        val intent = Intent(context, IncomingCallActivity::class.java)
            .putExtra(EXTRA_CALL_ID, callp)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // The notification is still there; nothing else to do.
        }
    }

    /**
     * A new outgoing call (one hook line in upstream's `"call outgoing"` event and in
     * `handleExternalCall`, UPSTREAM.md): every new call opens its call screen (owner,
     * 2026-11-20), however it started (the phone's voicemail key, another app). Nothing to do
     * when the call screen is already in front (BusyCall placed it itself).
     */
    @JvmStatic
    fun onOutgoingCall(context: Context, callp: Long) {
        if (!shouldShowOutgoing(CallScreens.inCallInFront, Build.VERSION.SDK_INT >= 29 && !Settings.canDrawOverlays(context))) return
        // CLEAR_TOP: reuse the call screen BusyCall may have just opened itself, never a second one.
        val intent = Intent(context, InCallActivity::class.java)
            .putExtra(EXTRA_CALL_ID, callp)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // The call notification still leads there.
        }
    }

    /** Pure: open the call screen unless it's in front, or Android won't let a service open it. */
    fun shouldShowOutgoing(callScreenInFront: Boolean, blockedFromBackground: Boolean): Boolean =
        !callScreenInFront && !blockedFromBackground

    /** What a dial request asks for: `tel:` → the number, `sip:` → the address. */
    private fun dialNumber(intent: Intent): String? {
        val data = intent.data ?: return null
        return when (data.scheme?.lowercase()) {
            "tel" -> data.schemeSpecificPart?.takeIf { it.isNotBlank() }
            "sip" -> data.toString()
            else -> null
        }
    }

    private val DIAL_ACTIONS = setOf(Intent.ACTION_DIAL, Intent.ACTION_CALL, Intent.ACTION_VIEW)
    private const val CALLS_MIME = "vnd.android.cursor.dir/calls"

    const val EXTRA_CALL_ID = "com.busycall.app.call_id"
}
