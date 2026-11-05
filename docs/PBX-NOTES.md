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
