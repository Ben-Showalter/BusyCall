# SIM calls and call blocking: message for review (draft, not sent yet)

Saved 2026-11-20 for later. A copy-ready message for the author of the Pure Speech Headwind MDM
fork (UncleAndy123/pure-speech-hmdm-android), who runs his own dialer on a Kyocera E4610.
The longer version is the "BusyCall SIM and Blocking Plan" page. Nothing here is decided or
built yet.

```text
Hey! I'm building BusyCall, a SIP softphone for keypad flip phones (testing on a Kyocera E4810, Android 9). It's a fork of baresip-studio: the backend (SIP, audio, calls) is upstream's, I only build the UI. VoIP calling works now. Next I want SIM calls and call blocking, and I'd love your advice since you've shipped a dialer on the E4610.

GROUND RULES
- Upstream releases must keep merging in. Any change to upstream files stays tiny, lives only in my fork (nothing pushed upstream), and is tracked.
- Minimum Android stays 9. Going down to Android 7 would mean (1) a second call path, because upstream uses a self-managed ConnectionService (Android 8+), and (2) rebuilding the native audio library with OpenSL ES instead of AAudio. Both are big upstream changes I'd have to redo on every release.

PART 1: CALL BLOCKING (first)
Upstream already has, per number: block callers not in contacts, block hidden numbers, pattern rules, and a log of blocked calls. I just need screens for those.
New:
- Spam rejection with a second chance: unknown callers are rejected unless they already called in the last 5 minutes, so a real person who calls back gets through.
- MDM allow/block lists through standard Android managed configuration (RestrictionsManager), the way Headwind already sends app settings. Proposed keys: call_filter_mode (off / allowlist / blocklist), allowed_numbers, blocked_numbers (comma-separated, * = everyone). Numbers match on digits, US/Canada without the leading 1. The MDM list wins over the user's settings.
- Outgoing calls to blocked numbers get stopped with a plain message.
This needs one small hook (about 6 lines) in upstream's service; the logic lives in my own code.

PART 2: SIM CALLS (second)
Upstream already handles SIM calls when it's the default phone app (its InCallService sends them into its call list, history and blocking), but it switches that off below Android 10 because it uses RoleManager.
Plan:
1. A small fork-only patch (about 20 lines) so Android 9 checks the default dialer with TelecomManager.getDefaultDialerPackage().
2. Ask to become the default dialer from Settings (ACTION_CHANGE_DEFAULT_DIALER on Android 9), with an easy way to switch back.
3. SIM calls show on BusyCall's screens, and "Phone (SIM)" becomes a real choice in the Call from picker.
4. Test on the E4810 first: 911, CALL/END keys, USSD/voicemail, and what happens if the app crashes mid-call.

YOUR DIALER
I can't drop your InCallService in, since only one app can be the default dialer and upstream already handles SIM calls. A second call stack would also block upstream updates. But I'd like to reuse your ideas (and credit you, Apache-2.0):
- the Android 9 default-dialer request
- your allow-list rules (* = all, empty = none, US/Canada number matching)
- Headwind sending settings through managed configuration
- your Kyocera END-key notes (END as HOME/BACK, hanging up in onUserLeaveHint). My app keeps the call going when you leave the call screen, so I'll test that on the E4810.

QUESTIONS FOR YOU
1. On the E4610 as the default dialer, did emergency calls, the CALL key from the home screen, and END all behave?
2. Which key does END actually send on your phone (HOME, BACK or ENDCALL)? Did onUserLeaveHint ever hang up by mistake (flip closed, a notification opened)?
3. Did ACTION_CHANGE_DEFAULT_DIALER stick on Kyocera, or did you need Device Owner?
4. Do those managed-config keys work for your Headwind setup, or would you name them differently so one config can serve both apps?
5. Is "second call within 5 minutes rings" a sensible spam default?
6. Any Kyocera quirks with speaker/Bluetooth audio or the outer display during SIM calls?

Thanks!
```
