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
Menus and confirmations are `AlertDialog`s drawn as a rounded dark card with an accent-red
title (and an optional question under it); the selected row gets the red focus ring.

## S1 Home (`home/HomeActivity`) — launcher
- **Shows:** "My numbers": each number as `Name · extension` with a status dot and either its
  voicemail count or status. "Recent": the last 8 calls, `Name` (red if missed) and
  `Missed/Incoming/Outgoing · to/from <number> · <time>`.
- **Problems:** "Starting the phone service…" until baresip is up; if permissions were refused,
  a plain message and **Retry**.
- **Keys:** 0–9 `*` `#` open the Dialer (S2) with that digit. CALL or OK on a recent calls back,
  from the number the call was on. OK on a number: Edit if the password was refused, Retry if
  it has no connection, else Numbers.
- **Why it can't connect (S10):** under a number with no connection, in red: "Wrong extension
  or password — OK to check it" (401/403/407), "The server isn't answering" (408/timeout),
  "Can't find the server: check its address" (DNS), "No internet connection", or "Can't
  connect: <server's reason>".
- **Options:** Contacts · Call log · Numbers · Classic screens (temporary) · Settings.
- **Empty:** one "+ Add a number" row.
- **Contacts:** once the phone service is up on first start, Android's contacts prompt appears
  by itself (asked once ever). Allowed: contacts are switched on. Refused: a toast with the
  Settings path; Contacts › Allow contacts remains.

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
  Keep the +, Replace + with… a digit prefix such as 011), Auth ID, Voicemail number; **Save and connect**;
  **More options…** (opens Options, since the T9 keyboard can take the softkeys).
- **Editing:** title "Edit number"; an empty password keeps the saved one (note shown).
- **Errors:** plain words at the top (and a toast), focus moves to the field.
- **Options:** Show/Hide password · Connection · Numbers starting with +.
- Every call from the number applies its + rule to `+` numbers (contacts store `+1 555…`);
  extensions and SIP addresses are never changed.

## S9 Settings (`settings/SettingsActivity`)
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

## S6 Call log (`calllog/CallLogActivity`)
- **Shows:** the last 200 calls, newest first: `Name` (red if missed) /
  `Missed/Incoming/Outgoing · to/from <number> · <time>`. "No calls yet." when empty.
- **Keys:** CALL calls back from the number the call was on (no picker). OK / tap: Call back ·
  Call from… (opens the Dialer with the number) · Delete.
- **Options:** Call back · Delete, for the focused call.

## S4 Incoming call (`call/IncomingCallActivity`)
- Opened from upstream's ring notification (via `call/CallRouter`), over the lock screen and a
  closed flip. **Shows:** "to Office · 104", caller name, number (`(378) 234-3189`); **Answer**
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
  ▼ **+ Call**, and the red **End** in the middle (focused). Lit petal = on. No End call bar.
- **Keys:** the arrows and OK act directly (focus never moves): ▲ speaker, ◀ mute, ▶ hold /
  resume / swap, ▼ + Call (holds this call, opens the Dialer as "Add a call"), OK = End.
  0–9 `*` `#` send keypad tones the moment they're pressed (once connected). END = hang up, then
  the system's own END action. Outer END / headset = hang up. Outer Speaker = speaker. BACK
  leaves the screen; the call goes on (the On-call bar shows elsewhere).
- The on-screen End stays in the app: back to the other call if there is one, else the screen
  before. Every petal is also tappable.
- If a placed call never appears within 10 s: "The call didn't start…" and the screen closes.
- **Softkey:** "Home" (the phone's home screen; the call carries on). No Options menu.
