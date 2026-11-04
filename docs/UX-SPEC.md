# UX spec — BusyCall screens as built

The contract for each screen that exists in code. `PLAN.md §5` is the full list (built or
not); the previews are the visual reference. Update this file in the same change as any
screen, key or label (AGENTS.md §12).

Everywhere (`ui/FlipBaseActivity`): Options is the only softkey (right; left after the first
MENU press, or Settings › Options key). The footer's "Options" label is also tappable.
OK activates the focused row or button. BACK goes back (Clear is BACK on Kyocera). Unknown
keys show "Unhandled key N (scan M)". Actions fire on key UP.

## S1 Home (`home/HomeActivity`) — launcher
- **Shows:** "My numbers": each number as `Name · extension` with a status dot and either its
  voicemail count or status. "Recent": the last 8 calls, `Name` (red if missed) and
  `Missed/Incoming/Outgoing · to/from <number> · <time>`.
- **Problems:** "Starting the phone service…" until baresip is up; if permissions were refused,
  a plain message and **Retry**.
- **Keys:** 0–9 `*` `#` open the dialer with that digit (upstream's dialer, for now). CALL on a
  recent calls back (upstream's screens, for now). OK on a number: Retry if it has no connection,
  else Numbers. OK on a recent: call back.
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
