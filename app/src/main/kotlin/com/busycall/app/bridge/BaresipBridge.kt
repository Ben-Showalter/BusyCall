package com.busycall.app.bridge

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import com.tutpro.baresip.Account
import com.tutpro.baresip.Api
import com.tutpro.baresip.BaresipService
import com.tutpro.baresip.BlockRule
import com.tutpro.baresip.Blocked
import com.tutpro.baresip.Call
import com.tutpro.baresip.CallHistory
import com.tutpro.baresip.Message
import com.tutpro.baresip.REGISTRATION_INTERVAL
import com.tutpro.baresip.UserAgent
import com.tutpro.baresip.Utils

/**
 * The only BusyCall code that touches baresip-studio's backend (AGENTS.md §2).
 * Everything here calls upstream functions the way upstream's own screens do;
 * no SIP or audio logic of our own. Never logs numbers, names or passwords.
 */
object BaresipBridge : Bridge {

    private const val TAG = "BusyCall"
    const val PERMISSIONS_REQUEST = 4201

    @Volatile
    private var startRequested = false

    // ---- Service ----

    override fun isRunning(): Boolean = BaresipService.isServiceRunning

    /** The same runtime permissions upstream's MainActivity asks for before starting the service. */
    fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        Build.VERSION.SDK_INT >= 31 -> arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT)
        Build.VERSION.SDK_INT < 29 -> arrayOf(
            Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
        else -> arrayOf(Manifest.permission.RECORD_AUDIO)
    }

    fun missingPermissions(ctx: Context): List<String> =
        requiredPermissions().filter { ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    override fun startService(activity: Activity) {
        val missing = missingPermissions(activity)
        if (missing.isNotEmpty()) {
            // The activity calls startService() again from onRequestPermissionsResult.
            activity.requestPermissions(missing.toTypedArray(), PERMISSIONS_REQUEST)
            return
        }
        // Once per process: the permission result and onResume can both get here.
        if (BaresipService.isStartReceived || startRequested) return
        startRequested = true
        // Same intent as upstream's MainActivity.
        val intent = Intent(activity, BaresipService::class.java)
        intent.action = "Start"
        try {
            activity.startForegroundService(intent)
        } catch (e: Exception) {
            startRequested = false
            Log.e(TAG, "Failed to start service: ${e.javaClass.simpleName}")
        }
    }

    // ---- Reading ----

    private fun sipAgents(): List<UserAgent> = BaresipService.uas.value.filter { !it.account.isMobile }

    private fun agentOf(id: String): UserAgent? = sipAgents().firstOrNull { it.account.aor == id }

    override fun numbers(): List<NumberUi> {
        val agents = sipAgents()
        val defaultAor = BaresipService.uas.value.firstOrNull()?.account?.aor
        return agents.map { ua ->
            val acc = ua.account
            val parts = SipAddress.parse(acc.luri.ifEmpty { acc.aor })
            NumberUi(
                id = acc.aor,
                label = acc.nickName.ifEmpty { parts.user },
                number = parts.user,
                server = parts.host,
                transport = parts.transport ?: Transport.UDP,
                status = statusOf(ua),
                voicemailNew = acc.vmNew,
                isDefault = acc.aor == defaultAor,
            )
        }
    }

    private fun statusOf(ua: UserAgent): NumberStatus = when {
        ua.account.regint == 0 -> NumberStatus.NotRegistering
        ua.status in BaresipService.circleGreen.values -> NumberStatus.Registered
        ua.status in BaresipService.circleRed.values -> NumberStatus.NoConnection
        else -> NumberStatus.Connecting // yellow, or white before the first attempt
    }

    override fun recents(limit: Int): List<RecentUi> {
        val history = synchronized(BaresipService.callHistory) { BaresipService.callHistory.toList() }
        return history.asReversed().take(limit).map { h ->
            val ua = UserAgent.ofAor(h.aor)
            // friendlyUri: contact name if known, else the number (upstream's own display rule).
            val name = if (ua != null) Utils.friendlyUri(h.peerUri, ua.account) else Utils.uriUserPart(h.peerUri)
            val incoming = h.direction == "in"
            RecentUi(
                peerName = name,
                peerUri = h.peerUri,
                numberLabel = ua?.account?.nickName?.ifEmpty { null } ?: SipAddress.parse(h.aor).user,
                numberId = h.aor,
                direction = if (incoming) CallDirection.In else CallDirection.Out,
                missed = incoming && h.startTime == null,
                timeMillis = (h.startTime ?: h.stopTime).timeInMillis,
            )
        }
    }

    override fun draftOf(id: String): NumberDraft? {
        val acc = agentOf(id)?.account ?: return null
        val parts = SipAddress.parse(acc.luri.ifEmpty { acc.aor })
        val vm = acc.vmUri
        val vmParts = SipAddress.parse(vm)
        return NumberDraft(
            name = acc.nickName,
            server = parts.host,
            port = parts.port,
            user = parts.user,
            authUser = if (acc.authUser == parts.user) "" else acc.authUser,
            password = "",
            transport = parts.transport ?: Transport.UDP,
            voicemail = when {
                vm.isEmpty() -> ""
                vmParts.host == parts.host -> vmParts.user
                else -> vm
            },
        )
    }

    // ---- Writing ----

    private fun validate(draft: NumberDraft, ownId: String? = null): SaveProblem? {
        if (!isRunning()) return SaveProblem.ServiceNotRunning
        if (!SipAddress.isValidHost(draft.server.trim())) return SaveProblem.InvalidServer
        if (!SipAddress.isValidUser(draft.user.trim())) return SaveProblem.InvalidUser
        if (draft.authUser.isNotBlank() && !Account.checkAuthUser(draft.authUser.trim())) return SaveProblem.InvalidUser
        if (!SipAddress.isValidPort(draft.port)) return SaveProblem.InvalidPort
        if (draft.password.isNotEmpty() && !Account.checkAuthPass(draft.password)) return SaveProblem.InvalidPassword
        val name = draft.name.trim()
        if (!Account.checkDisplayName(name)) return SaveProblem.InvalidName
        if (name.isNotEmpty() && sipAgents().any { it.account.nickName == name && it.account.aor != ownId }) {
            return SaveProblem.NameTaken
        }
        return null
    }

    override fun addNumber(draft: NumberDraft): SaveResult {
        validate(draft)?.let { return SaveResult.Failed(it) }
        val aor = SipAddress.aorOf(draft)
        if (BaresipService.uas.value.any { Utils.uriMatch(it.account.aor, aor) }) {
            return SaveResult.Failed(SaveProblem.AlreadyExists)
        }

        // The account line baresip parses, as upstream's Account.print() writes it.
        // The password is set afterwards, so it never appears in an allocation-failure log line.
        val line = buildString {
            append("<").append(aor).append(">")
            val authUser = draft.authUser.trim()
            if (authUser.isNotEmpty()) append(";auth_user=\"").append(authUser).append("\"")
            val vm = SipAddress.voicemailUri(draft)
            if (vm.isNotEmpty()) append(";mwi=yes;vm_uri=\"").append(vm).append("\"") else append(";mwi=no")
            append(";regint=").append(REGISTRATION_INTERVAL).append(";regq=0.5;pubint=0")
            val name = draft.name.trim()
            if (name.isNotEmpty()) append(";extra=\"nickname=").append(name).append(";last=empty\"")
        }
        val uap = UserAgent.uaAlloc(line)
        if (uap == 0L) return SaveResult.Failed(SaveProblem.AllocationFailed)

        val ua = UserAgent(uap)
        ua.add()
        val acc = ua.account
        acc.nickName = draft.name.trim()
        if (draft.password.isNotEmpty() && Api.account_set_auth_pass(acc.accp, draft.password) == 0) {
            acc.authPass = Api.account_auth_pass(acc.accp)
        }
        BaresipService.aorPasswords.remove(acc.aor)
        Account.saveAccounts()
        if (acc.regint > 0) ua.reRegister()
        return SaveResult.Saved(acc.aor)
    }

    override fun updateNumber(id: String, draft: NumberDraft): SaveResult {
        val ua = agentOf(id) ?: return addNumber(draft)
        validate(draft, ownId = id)?.let { return SaveResult.Failed(it) }

        val acc = ua.account
        val old = draftOf(id)
        val addressChanged = old == null ||
            SipAddress.aorOf(draft) != SipAddress.aorOf(old) ||
            SipAddress.voicemailUri(draft) != SipAddress.voicemailUri(old)
        if (addressChanged) {
            // baresip has no setter for these, so the account is recreated (upstream does the same
            // by delete + new). Keep the old password if none was typed.
            val wasDefault = BaresipService.uas.value.firstOrNull()?.account?.aor == id
            val password = draft.password.ifEmpty { acc.authPass.takeIf { it != com.tutpro.baresip.NO_AUTH_PASS } ?: "" }
            deleteNumber(id)
            val result = addNumber(draft.copy(password = password))
            if (wasDefault && result is SaveResult.Saved) setDefault(result.id)
            return result
        }

        var reRegister = false
        acc.nickName = draft.name.trim()
        val authUser = draft.authUser.trim().ifEmpty { SipAddress.parse(acc.aor).user }
        if (authUser != acc.authUser && Api.account_set_auth_user(acc.accp, authUser) == 0) {
            acc.authUser = Api.account_auth_user(acc.accp)
            reRegister = true
        }
        if (draft.password.isNotEmpty() && draft.password != acc.authPass &&
            Api.account_set_auth_pass(acc.accp, draft.password) == 0
        ) {
            acc.authPass = Api.account_auth_pass(acc.accp)
            BaresipService.aorPasswords.remove(acc.aor)
            reRegister = true
        }
        Account.saveAccounts()
        if (reRegister && acc.regint > 0) ua.reRegister()
        return SaveResult.Saved(acc.aor)
    }

    override fun deleteNumber(id: String) {
        val ua = agentOf(id) ?: return
        // As upstream's Accounts screen deletes an account.
        CallHistory.clear(id)
        Message.clearMessagesOfAor(id)
        Blocked.clear(id)
        BlockRule.clear(id)
        ua.remove()
        Api.ua_destroy(ua.uap)
        Account.saveAccounts()
    }

    override fun setDefault(id: String) {
        val ua = agentOf(id) ?: return
        ua.makeDefault()
        Account.saveAccounts()
    }

    override fun retry(id: String) {
        agentOf(id)?.reRegister()
    }

    // ---- Calls ----

    // Phone (SIM) calls stay with the phone's own dialer (PLAN §5.2), so they're left out.
    override fun calls(): List<CallUi> = Call.calls().filter { !it.ua.account.isMobile }.map { c ->
        val acc = c.ua.account
        val parts = SipAddress.parse(acc.luri.ifEmpty { acc.aor })
        val label = acc.nickName.ifEmpty { parts.user }
        CallUi(
            id = c.callp,
            peerName = Utils.friendlyUri(c.peerUri, acc),
            peerUri = c.peerUri,
            numberLabel = if (label == parts.user) parts.user else "$label · ${parts.user}",
            incoming = c.dir == "in",
            state = stateOf(c),
            durationSec = if (c.status.value == "connected") c.duration() else 0,
        )
    }

    /** Upstream's Call.status strings, as BusyCall states. */
    private fun stateOf(c: Call): CallState = when {
        c.terminated.value || c.status.value == "closed" -> CallState.Ended
        c.status.value == "incoming" -> CallState.Ringing
        c.status.value == "outgoing" -> CallState.Dialing
        c.status.value == "answered" -> CallState.Connecting
        c.onhold -> CallState.OnHold
        else -> CallState.Connected // "connected", "transferring"
    }

    override fun placeCall(activity: Activity, fromId: String?, dial: String): PlaceResult {
        if (!isRunning()) return PlaceResult.Failed(PlaceProblem.ServiceNotRunning)
        val ua = (fromId?.let { agentOf(it) } ?: sipAgents().firstOrNull())
            ?: return PlaceResult.Failed(PlaceProblem.NoNumber)
        val text = dial.trim()
        // Same number-to-address rules as upstream's dialer (MainScreen.makeCall), SIP accounts only.
        val peerUri = if (Utils.isTelNumber(text)) "tel:$text" else text
        val uri = if (Utils.isTelUri(peerUri)) Utils.telToSip(peerUri, ua.account) else Utils.uriComplete(peerUri, ua.account.aor)
        if (!Utils.checkUri(uri)) return PlaceResult.Failed(PlaceProblem.InvalidAddress)
        // A phone (SIM) call is in progress and none of ours: upstream refuses too.
        if (Utils.isAudioMode(activity, AudioManager.MODE_IN_CALL) && Call.calls().none { it.ua.uap == ua.uap }) {
            return PlaceResult.Failed(PlaceProblem.PhoneBusy)
        }
        val extras = Bundle()
        extras.putParcelable(
            TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
            BaresipService.getPhoneAccountHandle(activity, BaresipService.SIP_ACCOUNT_ID),
        )
        val callExtras = Bundle()
        callExtras.putBoolean("conferenceCall", false)
        callExtras.putLong("uap", ua.uap)
        extras.putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, callExtras)
        return try {
            val tm = activity.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.placeCall(Uri.parse(uri), extras)
            PlaceResult.Placed
        } catch (e: SecurityException) {
            Log.e(TAG, "placeCall failed: ${e.javaClass.simpleName}")
            PlaceResult.Failed(PlaceProblem.Failed)
        }
    }

    /** Upstream's service commands for calls (they also stop ringing and update notifications). */
    private fun serviceCommand(activity: Activity, action: String, callId: Long) {
        val call = Call.ofCallp(callId) ?: return
        val intent = Intent(activity, BaresipService::class.java)
        intent.action = action
        intent.putExtra("uap", call.ua.uap)
        intent.putExtra("callp", callId)
        try {
            if (isRunning()) activity.startService(intent) else activity.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "$action failed: ${e.javaClass.simpleName}")
        }
    }

    override fun answer(activity: Activity, callId: Long) = serviceCommand(activity, "Call Answer", callId)
    override fun decline(activity: Activity, callId: Long) = serviceCommand(activity, "Call Reject", callId)
    override fun hangup(activity: Activity, callId: Long) = serviceCommand(activity, "Call Hangup", callId)

    override fun sendDigit(callId: Long, digit: Char) {
        Call.ofCallp(callId)?.sendDigit(digit)
    }

    override fun isMuted(): Boolean = BaresipService.isMicMuted
    override fun setMuted(muted: Boolean) = BaresipService.setMicMute(muted)
    override fun isSpeakerOn(): Boolean = BaresipService.speakerPhone
    override fun toggleSpeaker() {
        BaresipService.instance?.toggleSpeakerphone()
    }

    override fun hold(callId: Long) {
        Call.ofCallp(callId)?.hold()
    }

    override fun resume(callId: Long) {
        Call.ofCallp(callId)?.resume()
    }

    override fun silenceRinger() {
        BaresipService.rt?.stop()
    }

    override fun openClassicScreens(activity: Activity, dial: String?) {
        val intent = Intent(activity, com.tutpro.baresip.MainActivity::class.java)
        if (!dial.isNullOrEmpty()) {
            intent.action = Intent.ACTION_DIAL
            intent.data = android.net.Uri.parse(if (dial.startsWith("sip:") || dial.startsWith("tel:")) dial else "tel:$dial")
        }
        activity.startActivity(intent)
    }
}
