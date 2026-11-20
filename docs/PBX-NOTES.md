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
