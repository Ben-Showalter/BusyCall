# UX spec — BusyCall screens as built

The contract for each screen that exists in code. `PLAN.md §5` is the full list (built or
not); the previews are the visual reference. Update this file in the same change as any
screen, key or label (AGENTS.md §12).

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
  from the number the call was on. OK on a number: Retry if it has no connection, else Numbers.
- **Options:** Contacts · Call log · Numbers · Classic screens (temporary) · Settings.
- **Empty:** one "+ Add a number" row.

## S7 Numbers (`numbers/NumbersActivity`)
- **Shows:** hint "★ = the number the Dialer calls from"; each number `★ Name · ext` (★ on the
  default) with a status dot and `server · connection · status`; **Phone (SIM)** "Opens the
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
- **Options:** + Add a number · Settings.

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
- **Options:** Save and connect · Change details.

## S8 Number setup (`numbers/NumberSetupActivity`)
- **Fields:** Name, Server (`*` types a dot), Port, Extension or username, Password,
  Connection (row → menu UDP/TCP/TLS), Auth ID, Voicemail number; **Save and connect**;
  **More options…** (opens Options, since the T9 keyboard can take the softkeys).
- **Editing:** title "Edit number"; an empty password keeps the saved one (note shown).
- **Errors:** plain words at the top (and a toast), focus moves to the field.
- **Options:** Save and connect · Show/Hide password · Connection.

## S9 Settings (`settings/SettingsActivity`)
- Options key: Right/Left softkey (OK toggles). About: baresip-studio and license notice.

## S2 Dialer (`dialer/DialerActivity`)
- **Shows:** Number or name (a display: keys type into it, no keyboard; focused, OK = Call), up to
  3 **matches** under it, **Call from** row
  (`◀ Office · 104 ▶` when there's a choice; the ★ number first, Phone (SIM) last), **Call**.
- **Keys:** digits type; hold 0 = `+`; Clear/BACK deletes one digit, leaves when empty;
  ◀ ▶ on Call from change it (no wrap); OK on Call from opens the list; CALL or Call places the
  call → S5. Empty number + CALL fills in the last number called. From Phone (SIM): the phone's
  own dialer with the number (`ACTION_DIAL`).
- **Errors:** plain words under the button (service starting, no number, not callable, a phone
  call in progress, couldn't start).
- **Matches (PLAN §3.3):** contacts whose name has a word starting with the keys' letters
  (2=ABC … 9=WXYZ, accents folded), or whose number contains 3+ typed digits; call-log numbers
  too. First-name matches first, then later words, then numbers. Each number is its own row:
  `Name` / `Mobile · +15550198`, the matched part underlined. ▼ moves into them; OK, CALL or a tap
  calls that one from the Call from number. CALL elsewhere dials the digits as typed.
- **Options:** Call · Call from… · Clear number.

## S3 Contacts (`contacts/ContactsActivity`)
- **Shows:** the phone's contacts with a number, by name: `Name` / `Mobile · +15550198 · +1 more`.
- **Keys:** 2–9 jump to the first name starting with that key's letters (or the next after);
  OK / tap = the contact's numbers (Call each · Call from…); CALL calls the first number from
  the ★ number.
- **Off:** "Contacts are off — Options › Allow contacts" and an **Allow contacts** row. Allowing
  asks for contacts access (upstream needs read and write), then reads them (upstream's contacts
  mode "both"). Refused: a toast with the Settings path.
- **Options:** Call · Call from… (on a contact) · Allow contacts (when off).

## S6 Call log (`calllog/CallLogActivity`)
- **Shows:** the last 200 calls, newest first: `Name` (red if missed) /
  `Missed/Incoming/Outgoing · to/from <number> · <time>`. "No calls yet." when empty.
- **Keys:** CALL calls back from the number the call was on. OK / tap: Call back · Call from… ·
  Delete.
- **Options:** the same, for the focused call.

## S4 Incoming call (`call/IncomingCallActivity`)
- Opened from upstream's ring notification (via `call/CallRouter`), over the lock screen and a
  closed flip. **Shows:** "to Office · 104", caller name, number; **Answer** (green, focused),
  **Decline** (red); hint "CALL answers · END declines · BACK silences".
- **Keys:** CALL / headset = answer → S5. END = decline, then the system's own END action.
  Outer END = decline. BACK = silence the ringer only.
- Closes itself if the call stops ringing (cancelled → Recent shows it missed; answered elsewhere → S5).
- **Options:** Silence ringer · Answer · Decline.

## S5 In call (`call/InCallActivity`)
- **Shows:** title "Calling…" / "Connecting…" / "On call · 03:42" / "On hold · 03:42";
  "from/to Office · 104"; name; number; "Sent: 1234#" once tones are sent; **Speaker**, **Mute**,
  **Hold** (side by side); **End call** (red, focused).
- **Keys:** 0–9 `*` `#` send keypad tones the moment they're pressed (once connected).
  END = hang up, then the system's own END action. Outer END / headset = hang up. Outer Speaker =
  speaker. BACK leaves the screen; the call goes on (the On-call bar shows elsewhere).
- **End call** button and Options › Hang up end the call and stay in the app.
- If a placed call never appears within 10 s: "The call didn't start…" and the screen closes.
- **Options:** Speaker on/off · Mute/Unmute · Hold/Resume · Hang up.
