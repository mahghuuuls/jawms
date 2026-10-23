$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'verify-gap-observations.ps1') -DefinitionsOnly
function Event($Name, $Values) {
    $parts=@(); foreach($key in $Values.Keys){ $parts += "$key, $($Values[$key])" }
    "PUBLICATION_OBSERVER event=$Name values=[$($parts -join ', ')] thread=SYNTHETIC_READER_TEST"
}
function Reject($Name, [string[]]$Lines, $Case) {
    $failed=$false; try { Test-GapObservation -Case $Case -Lines $Lines | Out-Null } catch { $failed=$true }
    if(-not $failed){throw "Reader sensitivity failed: $Name"}
}
$retired=@(
    (Event gap_actual_retirement ([ordered]@{generation=1;retired='true';pending='false'})),
    (Event gap_retired_replay ([ordered]@{generation=1;serial=4})),
    (Event gap_retired_packet ([ordered]@{packet=8})),
    (Event gap_receiver_disposition ([ordered]@{packet=8;type='SPacketSetSlot';forward='false';watermark=5}))
)
Test-GapObservation retired $retired | Out-Null
Reject 'no actual retirement' $retired[1..3] retired
Reject 'old packet really applied' ($retired + (Event main_apply_scope ([ordered]@{packet=8}))) retired
Reject 'wrong receiver packet' ($retired -replace 'packet, 8, type','packet, 9, type') retired
$fault=@(
    (Event actual_partner_suppressed ([ordered]@{serial=27})),
    'inventory publication association failed; reconnect required',
    (Event gap_held_receive ([ordered]@{packet=10})),
    (Event gap_actual_held_write ([ordered]@{packet=10;requested=0;argument=0;actual=0;epoch='new';fault='true'}))
)
Test-GapObservation fault $fault | Out-Null
Reject 'same index without actual write' $fault[0..2] fault
Reject 'write outside fault' ($fault -replace 'fault, true','fault, false') fault
$epoch=@(
    (Event gap_epoch_replay ([ordered]@{oldEpoch='old';currentEpoch='new';serial=9;watermark=1;intent=1;use=1})),
    (Event gap_epoch_packet ([ordered]@{packet=12})),
    (Event gap_receiver_disposition ([ordered]@{packet=12;type='SPacketSetSlot';forward='false';watermark=1})),
    (Event gap_actual_close_accept ([ordered]@{incomingEpoch='old';currentEpoch='new';incomingIntent=1;currentIntent=1;incomingUse=1;currentUse=1;incomingHand='MAIN_HAND';currentHand='MAIN_HAND';incomingSpell='stream';currentSpell='stream';accepted='false'})),
    (Event gap_actual_release ([ordered]@{phase='before';incomingEpoch='old';currentEpoch='new';intent=1;newestIntent=1;releasedThrough=0;accepted='null'})),
    (Event gap_actual_release ([ordered]@{phase='after';incomingEpoch='old';currentEpoch='new';intent=1;newestIntent=1;releasedThrough=0;accepted='false'})),
    (Event gap_epoch_replay_complete ([ordered]@{capsuleEmpty='true'}))
)
Test-GapObservation epoch $epoch | Out-Null
Reject 'stale serial masks epoch' ($epoch -replace 'watermark, 1','watermark, 10') epoch
Reject 'close accepted' ($epoch -replace 'currentSpell, stream, accepted, false','currentSpell, stream, accepted, true') epoch
Reject 'release watermark changed' ($epoch -replace 'phase, after, (.*)releasedThrough, 0','phase, after, $1releasedThrough, 1') epoch
Reject 'missing real handler' ($epoch | Where-Object {$_ -notmatch 'gap_actual_close_accept'}) epoch
'PASS: three synthetic observation fixtures and nine rejecting mutations; not game evidence.'
