# UX spec — BusyCall screens as built

The contract for each screen that exists in code. `PLAN.md §5` is the full list (built or
not); the previews are the visual reference. Update this file in the same change as any
screen, key or label (AGENTS.md §12).

Everywhere (`ui/FlipBaseActivity`): while a call is active, an **"On call · 03:42 · Name"** bar
(or "Incoming call · Name") shows under the title; OK or a tap returns to the call. Options is the only softkey (right; left after the first
MENU press, or Settings › Options key). The footer's "Options" label is also tappable.
OK activates the focused row or button. BACK goes back (Clear is BACK on Kyocera). Unknown
keys show "Unhandled key N (scan M)". Actions fire on key UP.

## S1 Home (`home/HomeActivity`) — launcher
- **Shows:** "My numbers": each number as `Name · extension` with a status dot and either its
  voicemail count or status. "Recent": the last 8 calls, `Name` (red if missed) and
  `Missed/Incoming/Outgoing · to/from <number> · <time>`.
- **Problems:** "Starting the phone service…" until baresip is up; if permissions were refused,
  a plain message and **Retry**.
- **Keys:** 0–9 `*` `#` open the Dialer (S2) with that digit. CALL or OK on a recent calls back,
  from the number the call was on. OK on a number: Retry if it has no connection, else Numbers.
- **Options:** Numbers · Classic screens (temporary) · Settings.
- **Empty:** one "+ Add a number" row.

## S7 Numbers (`numbers/NumbersActivity`)
- **Shows:** hint "★ = the number the Dialer calls from"; each number `★ Name · ext` (★ on the
  default) with a status dot and `server · connection · status`; **Phone (SIM)** "Opens the
  phone's dialer" (only with telephony); **+ Add a number**.
- **OK on a number:** menu Edit · Make default ★ (not on the default) · Retry (when not
  registered) · Delete (confirm).
- **OK on Phone (SIM):** the phone's dialer (`ACTION_DIAL`), nothing else.
- **+ Add a number:** menu Type it in · Paste setup link. (Scan QR setup code: later.)
- **Paste setup link:** reads the clipboard, clears it, opens S7a; or a plain message: nothing
  copied / not a BusyCall setup link / newer version / missing server or extension.
- **Options:** + Add a number · Settings.

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

## S2 Dialer (`dialer/DialerActivity`) — basic
- **Shows:** Number (a display: keys type into it, no keyboard), **Call from** row
  (`◀ Office · 104 ▶` when there's a choice; the ★ number first, Phone (SIM) last), **Call**.
- **Keys:** digits type; hold 0 = `+`; Clear/BACK deletes one digit, leaves when empty;
  ◀ ▶ on Call from change it (no wrap); OK on Call from opens the list; CALL or Call places the
  call → S5. Empty number + CALL fills in the last number called. From Phone (SIM): the phone's
  own dialer with the number (`ACTION_DIAL`).
- **Errors:** plain words under the button (service starting, no number, not callable, a phone
  call in progress, couldn't start).
- **Options:** Call · Call from… · Clear number. Contact matching by keypad letters: later.

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
