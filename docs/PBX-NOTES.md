# Phone-system notes

Anything specific to one phone system goes here, never in code (`AGENTS.md §0`
rule 6). For each system: version tested, settings that matter, and anything
that surprised us.

## Grandstream UCM
_Not tested yet._ To record: model and firmware, extension settings used
(transport, DTMF mode, codecs, NAT), voicemail code, and what its Wave QR code
contains (spike S6).

## Asterisk / FreePBX
_Not tested yet._

## FreeSWITCH
_Not tested yet._

## Hosted SIP provider
_Not tested yet._

## Numbers starting with + (all systems)
Phone contacts store numbers as `+1 555 0198`. Many phone systems' outbound routes don't match
a leading `+`. **Grandstream UCM** answers **604** ("does not exist anywhere") for
`+15550198…`, while `15550198…` connects (E4810, 2026-11-05). BusyCall's per-number setting
**Numbers starting with +** handles it: *Drop the +* (default), *Keep the +*, or *Replace + with…*
(e.g. `011`, `00`) for systems that want an international prefix.

## Calls through a non-standard port or TLS (all systems; found with UCM RemoteConnect)
Upstream builds a call's address from the server **name** only (`sip:*97@host`), so a number
that signs in on `host:5061;transport=tls` (Grandstream UCM RemoteConnect) sent its calls to
UDP 5060: "Calling…" forever, no dialing sound, and nothing in the UCM's CDR, while
registration and incoming calls worked (2026-11-20, E4810). BusyCall adds the number's own port
and transport to calls on its own server (`SipAddress.withAccountRoute`, in the bridge).

## Grandstream UCM RemoteConnect (remote access)
Number settings that work from cell data (E4810, 2026-11-20):
- **Server**: the "IP Endpoint/Trunk RemoteConnect Address" from UCM › RemoteConnect › Plan
  (`<id>.e.gdms.cloud`), **Port** `5061`, **Connection** TLS.
- **Network help (NAT)**: STUN, server `nat-a.gdms.cloud` (the plan page's STUN address).
  Without it calls connect but neither side hears the other.
- The Basic plan limits remote calls to 20 minutes each and 120 minutes a day, 2 at once.
- The UCM sends incoming calls from its LAN IP; BusyCall shows phone-number callers as the
  number whatever host they come from (`bridge/PeerName`).

## Call park (Asterisk-based systems, Grandstream UCM)
Parking is a transfer to the park number (`700` by default). BusyCall parks like a desk phone:
the caller holds, BusyCall calls 700, the system announces the slot (e.g. 701) to you, and OK
("Park") connects the two (upstream's attended transfer), leaving the caller in the slot.
Anyone dials the slot number to pick it up. The park number is a per-number setting.

## Emergency calls over VoIP (all systems)
BusyCall sends emergency numbers (911, 112, … as Android lists them for the SIM's country) on the
phone network, and over a VoIP number **only when there is no cell service at all** (then the ★
number if it's connected, else the first connected one). For that fallback to help, the phone
system must route 911 to a trunk that delivers an emergency address for the extension (US:
Kari's Law and RAY BAUM'S Act; a "dispatchable location"). Grandstream UCM: Call Features ›
Emergency Calls, with the trunk's E911 address set at the provider. Through RemoteConnect the
caller may be far from that address, which is why the call screen says "Tell them where you are".

## Blocked calls and voicemail (all systems)
BusyCall's blocking rejects a call the way upstream does: SIP calls get "486 Busy Here", SIM calls
are declined. Whether the caller then reaches voicemail is the phone system's or carrier's busy
setting. Grandstream UCM: Extension › (Features) Call Forward Busy, or voicemail on busy.
