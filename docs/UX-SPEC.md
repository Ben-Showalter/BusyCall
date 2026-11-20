# UX spec — BusyCall screens as built

The contract for each screen that exists in code. `PLAN.md §5` is the full list (built or
not); the previews are the visual reference. Update this file in the same change as any
screen, key or label (AGENTS.md §12).

Look (owner, 2026-11-20): black with gray accents; green Call/Answer, red End/Decline. Options
holds only what isn't on screen. Callers that are phone numbers show as the number
(`(378) 234-3189`) or the contact's name, whatever server they came through (`bridge/PeerName`).

Everywhere (`ui/FlipBaseActivity`): while a call is active, an **"On call · 03:42 · Name"** bar
(or "Incoming call · Name") shows under the title; OK or a tap returns to the call. Options is the only softkey (right; left after the first
MENU press, or Settings › Options key). The footer's "Options" label is also tappable.
OK activates the focused row or button. BACK goes back (Clear is BACK on Kyocera). Unknown
keys show "Unhandled key N (scan M)". Actions fire on key UP.
Menus and confirmations are `AlertDialog`s drawn as a rounded dark card with an accent title
(and an optional question under it); the selected row gets the focus ring, drawn behind its text.

## S1 Home (`home/HomeActivity`) — launcher, and the call log
Home **is** the call log (owner, 2026-11-20); there is no separate Call log screen.
- **Shows:** every call (the newest 200), newest first. Each row (min 52 dp): a **phone icon with
  an arrow**, green ↙ incoming, green ↗ outgoing, red ↙ missed (the name is red too); the name;
  `Ohio · to Office · 9:42` (where the number is from, which of your numbers, when). "No calls
  yet. Type a number to call." when empty.
- **Above the log, only when needed:** a red row for each number that **can't connect**
  ("Office · 104 can't connect" / the reason in plain words, or "OK to try again"): OK opens Edit
  for a refused password, else retries. No numbers at all: one "+ Add a number" row.
- **Problems:** "Starting the phone service…" until baresip is up; if permissions were refused,
  a plain message and **Retry**.
- **Keys:** 0–9 `*` `#` open the Dialer (S2) with that digit. **OK on a call → Call details.
  CALL, or holding OK, calls back** from the number the call was on (no picker).
- **Options:** Contacts · Blocked calls · Numbers · Classic screens (temporary) · Settings.
- **At first start, one at a time:** the microphone, then contacts (Android's prompt, asked once
  ever; refused: a toast with the Settings path), then **END key and staying ready** (a card
  "Let the END key end calls, and keep BusyCall ready without a notification?" · Open settings →
  Android's Accessibility settings with "Choose Call END key, then switch it on"). Asked once.
- Android's "show call log" (other apps, the stock launcher) opens Home.

## S7 Numbers (`numbers/NumbersActivity`)
- **Shows:** hint "★ = the number the Dialer calls from"; each number `★ Name · ext` (★ on the
  default) with a status dot and `server · connection · status` (the S10 reason instead, in red,
  while it can't connect); **Phone (SIM)** "Opens the
  phone's dialer" (only with telephony); **+ Add a number**.
- **OK on a number:** menu Edit · Make default ★ (not on the default) · Retry (when not
  registered) · Delete (confirm).
- **OK on Phone (SIM):** the phone's dialer (`ACTION_DIAL`), nothing else.
- **+ Add a number:** menu "Add a number", each row with an icon and a grey hint:
  ✎ Type it in "Server, extension, password" · 🔗 Paste setup link "Copied from a text or
  email" · ▦ Scan QR setup code "Uses the camera" (only on phones with a camera).
- **Scan QR setup code** first asks (S7b): menu "Scan QR setup code", question "Do you have a
  QR setup code ready?", then **Yes, scan it** (scan screen) · **No, help me set it up** (S7c).
- **Paste setup link:** reads the clipboard, clears it, opens S7a; or a plain message: nothing
  copied / not a BusyCall setup link / newer version / missing server or extension.
- **Options:** Settings.

## Scan setup code (`numbers/setup/ScanActivity`)
- **Shows:** the camera preview at its own shape (black bars where it doesn't fill); a status
  line laid over the bottom of the picture ("Point the camera at the QR setup code…");
  **Scan again** (focused; **Retry** when the camera isn't open).
- **Keys:** OK / tap = scan again (refocus, clear the message). BACK cancels.
- A setup code opens S7a and closes the scanner. Anything else: "That QR code isn't a BusyCall
  setup code…" (or newer version / missing server or extension), and scanning goes on; the same
  rejected code doesn't repeat the message.
- **Problems:** camera refused → how to allow it + Retry; camera busy/missing → plain words + Retry.
- Frames are decoded on a background thread (ZXing core) and never saved.
- **Options:** Scan again · Help me make a code (S7c).

## S7c Make a setup code (`numbers/setup/SetupHelpActivity`)
- **Shows:** 1. Save the setup page to this phone + **Save setup page to phone** (focused) + result
  line ("Saved: Download › busycall-setup.html", or the failure); 2. Move busycall-setup.html to a
  computer over USB, Bluetooth, or SD card; 3. Open it in a browser and fill it in (works
  offline); 4. Come back and scan + **Scan the code**.
- Saving replaces an earlier copy. API 29+: MediaStore Download; API 28: asks for storage first.
- **Options:** Save again · Save to SD card (only with a card in; BusyCall's folder on the card) ·
  Send by Bluetooth (the Bluetooth app, else the share list).

## S7a Setup code found (`numbers/setup/SetupFoundActivity`)
- **Shows:** Name, Server, Extension · Password (hidden), Connection; **Save and connect**
  (starting focus). OK on a line opens S8 with everything filled in.
- **Save fails:** toast "Check the details, then press Save again." and S8 opens filled in.
- **Options:** Change details.

## S8 Number setup (`numbers/NumberSetupActivity`)
- **Fields:** Name, Server (`*` types a dot), Port, Extension or username, Password,
  Connection (row → menu UDP/TCP/TLS), **Numbers starting with +** (row → menu: Drop the + (default),
  Keep the +, Replace + with… a digit prefix such as 011), **Network help (NAT)** (row → menu: Off (default), STUN, ICE;
  when on, a **STUN server** field, `*` types a dot, hint `nat-a.gdms.cloud`), Auth ID, Voicemail number; **Save and connect**;
  **More options…** (opens Options, since the T9 keyboard can take the softkeys).
- **Editing:** title "Edit number"; an empty password keeps the saved one (note shown).
- **Errors:** plain words at the top (and a toast), focus moves to the field.
- **Park number** field (default `700`, hint `700`; empty hides Park in calls).
- **Block calls** row → menu (note: "Blocked callers are turned away as busy, which usually sends
  them to voicemail"): **Off** · **Known spam** (marked as spam by the carrier/phone system, hidden
  or fake numbers, your blocked list) · **Not in contacts** (unknown numbers blocked; a second call
  from the same number within 5 minutes rings). Phone (SIM) gets the same choice from its row in
  Numbers while SIM calls use BusyCall.
- **Options:** Show/Hide password · Connection · Numbers starting with + · Network help (NAT).
- Every call from the number applies its + rule to `+` numbers (contacts store `+1 555…`);
  extensions and SIP addresses are never changed.

## S9 Settings (`settings/SettingsActivity`)
- **Use for phone (SIM) calls** (only on phones with a SIM): "On: SIM calls use BusyCall" / "Off:
  the phone's own dialer handles SIM calls". OK when off: Android's "make BusyCall the phone app"
  prompt, then the Phone permission; done: "SIM calls now use BusyCall". OK when on: a card
  "Switch back to the phone's dialer" → Android's default apps screen.
- While on: Call from… lists **Phone (SIM)** ("Mobile network" / "No mobile service") as a real
  number; SIM calls ring on S4 and run on S5 (no Call options on ▼); the phone's own dial requests
  (home-screen digits, `tel:` links) open S2 with the number, and the call-log view opens S1.
- **END key and staying ready:** "On: END ends calls; no notification" / "Off: END only goes home
  during a call". OK opens Android's Accessibility settings (off: after the card explaining it).
  While on, END declines a ringing call, hangs up on the In call screen, and brings the In call
  screen back from any other app; BusyCall shows no status notification.
- Options key: Right/Left softkey (OK toggles). About: baresip-studio and license notice.

## S2 Dialer (`dialer/DialerActivity`)
- **Shows:** Number or name (a display: keys type into it, no keyboard; focused), up to 3
  **matches** under it, **Call** (green). No Call-from row (owner, 2026-11-20). Title "Add a call"
  when opened from a call's ▼ + Call.
- **Keys:** digits type; hold 0 = `+`; Clear/BACK deletes one digit, leaves when empty.
  **OK or CALL, short press → Call from… picker** (SIP numbers with status, ★ first, Phone (SIM)
  last; tip "hold OK or CALL to call from ★"); **held (700 ms) → calls from ★ at once**. With
  only one way to call, a short press calls at once. A focused match is called instead of the
  typed digits. Empty number + OK/CALL fills in the last number called. From Phone (SIM): the
  phone's own dialer with the number (`ACTION_DIAL`).
- **Errors:** plain words under the button (service starting, no number, not callable, a phone
  call in progress, couldn't start).
- **Matches (PLAN §3.3):** contacts whose name has a word starting with the keys' letters
  (2=ABC … 9=WXYZ, accents folded), or whose number contains 3+ typed digits; call-log numbers
  too. First-name matches first, then later words, then numbers. Each number is its own row:
  `Name` / `Mobile · +15550198`, the matched part underlined. ▼ moves into them.
- **Emergency numbers** (Android's list for the SIM's country, e.g. 911, 112): the button reads
  **Emergency call**, with a line under it: "On the phone network", or with no cell service at
  all "No cell service: over Office · 104. Tell them where you are." OK/CALL (short or held) then
  skips the picker and ★: the phone network (BusyCall's call screen when it is the phone's
  dialer, else the phone's dialer with the number filled in), or at once over that VoIP number.
  Transfer to an emergency number is refused; "Add a call" to one follows the same rule.
- **Options:** Clear number.

## S3 Contacts (`contacts/ContactsActivity`)
- **Shows:** the phone's contacts with a number, by name: `Name` / `Mobile · +15550198 · +1 more`.
- **Keys:** 2–9 jump to the first name starting with that key's letters (or the next after).
  OK / tap = the contact's numbers card (tip "OK: choose where to call from · hold OK/CALL: ★");
  OK on a number → Call from… picker; holding OK or CALL on a number calls it from ★. On the list:
  CALL = Call from… for the first number; holding OK or CALL calls the first number from ★.
- **Off:** "Contacts are off — Options › Allow contacts" and an **Allow contacts** row. Allowing
  asks for contacts access (upstream needs read and write), then reads them (upstream's contacts
  mode "both"). Refused: a toast with the Settings path. Also asked once at first start (S1).
- **Options:** Allow contacts (when off).

## S6 Call details (`calllog/CallDetailsActivity`) — OK on a Home call
- **Shows:** name (red if missed), number `(378) 234-3189`, `Missed · to Office · Mon 9:42`,
  **"Number from Ohio"** and a **small map** (US + Canada outlines, Ohio filled, a dot at its
  centre; other countries: the name only, no map). The area code says where the number was
  issued, not where the caller is.
- **Call back** (green, focused; CALL does the same) · **Add to contacts** (phone numbers not in
  contacts: **New contact** = Android's contact editor with the number filled in · **Add to a
  contact…** = pick a contact, the number is added) or **Open contact** (already a contact) ·
  **Block this number** · **Delete**. BACK returns to Home. Contacts off: "Contacts are off —
  Options › Allow contacts".

## Blocked calls (`blocking/BlockedActivity`) — Home › Options › Blocked calls
- **Shows:** each blocked call, newest first: name/number / `reason · to <number> · <time>`
  (Spam, Hidden number, Fake number, Your blocked list, Not in contacts). "No blocked calls." when empty.
- **OK:** Unblock (this caller always rings from now on) · Call back · Delete.
- A blocked call doesn't ring, isn't a missed call, and has no notification (upstream may show a
  short "blocked" toast).

## S4 Incoming call (`call/IncomingCallActivity`)
- Opens by itself in front of any app when a call rings (one hook line in upstream's
  `handleIncomingCall` → `CallRouter.onIncomingCall`; Android 10+ also needs "Appear on top"),
  and from upstream's ring notification, over the lock screen and a closed flip. **Shows:** "to Office · 104", caller name, number (`(378) 234-3189`), where the number
  is from ("Ohio") with a 72 dp map (portrait and normal font sizes only); **Answer**
  (green, focused), **Decline** (red); hint "CALL answers · END declines · BACK silences".
- **Keys:** CALL / headset = answer → S5. END = decline, then the system's own END action.
  Outer END = decline. BACK = silence the ringer only.
- Closes itself if the call stops ringing (cancelled → Recent shows it missed; answered elsewhere → S5).
- **Softkey:** "Home" (the phone's home screen; the call keeps ringing). No Options menu.

## S5 In call (`call/InCallActivity`)
- **Shows:** title "Calling…" / "Connecting…" / "On call · 03:42" / "On hold · 03:42";
  "from/to Office · 104"; name; number (`(378) 234-3189`); with two calls, the other one
  ("Anna Becker · On hold"); "Sent: 1234#" once tones are sent; the **D-pad ring** (a rounded
  square): ▲ **Speaker**, ◀ **Mute**, ▶ **Hold** (Resume when on hold; **Swap** with two calls),
  ▼ **+ Call** (**Transfer** with two calls), and the red **End** in the middle (focused; **Park**
  on a call to the park number). Lit petal = on. No End call bar.
- **Keys:** the arrows and OK act directly (focus never moves): ▲ speaker, ◀ mute, ▶ hold /
  resume / swap, OK = End. **▼ with one call** opens "Call options": **Add a call** (this call
  holds; the Dialer opens as "Add a call", calling from the same number) · **Transfer…** (the
  Dialer opens as "Transfer to"; OK/CALL sends the caller there: "Transferred", or "The transfer
  didn't go through. The call is still on.") · **Park** (when the number has a park number: the
  caller holds, BusyCall calls the park number, the phone system announces the slot, OK =
  **Park** leaves the caller there). **▼ with two calls** = **Transfer**: connects them, you drop off.
  0–9 `*` `#` send keypad tones the moment they're pressed (once connected). END = hang up, then
  the system's own END action. Outer END / headset = hang up. Outer Speaker = speaker. BACK
  leaves the screen; the call goes on (the On-call bar shows elsewhere).
- The on-screen End stays in the app: back to the other call if there is one, else the screen
  before. Every petal is also tappable.
- An emergency call over VoIP (no cell service) shows "Tell them where you are" in red under the name.
- If a placed call never appears within 10 s: "The call didn't start…" and the screen closes.
- **Softkey:** "Home" (the phone's home screen; the call carries on). No Options menu.
