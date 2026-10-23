param(
    [ValidateSet('epoch','retired','fault')][string]$Case,
    [string]$LogPath,
    [string]$StartMarker,
    [string]$EndMarker = 'C007_TERMINAL_INSPECTED',
    [switch]$DefinitionsOnly
)
$ErrorActionPreference = 'Stop'
function Test-GapObservation {
    param([string]$Case, [string[]]$Lines)
    function Need([bool]$Value, [string]$Message) { if (-not $Value) { throw "UNREACHED/FAIL: $Message" } }
    $events = @()
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i] -match 'PUBLICATION_OBSERVER event=(\S+) values=\[(.*)\] thread=') {
            $name = $Matches[1]; $parts = $Matches[2] -split ', '; $values = @{}
            for ($j = 0; $j + 1 -lt $parts.Count; $j += 2) { $values[$parts[$j]] = $parts[$j+1] }
            $events += [pscustomobject]@{ Name=$name; At=$i; V=$values }
        }
    }
    Need (-not [bool]($Lines -match 'UNREACHED|observer_bad_pair|identity_capacity|OBSERVER overflow|/(ERROR|FATAL)\]|failed=[1-9]')) 'Evidence failure/overflow'
    function Require-RejectedPacket($EventName) {
        $packets = @($events | Where-Object Name -eq $EventName)
        Need ($packets.Count -gt 0) "Missing $EventName"
        foreach ($packet in $packets) {
            $token = $packet.V.packet
            Need ([int]$token -gt 0) 'Invalid packet token'
            $decisions = @($events | Where-Object { $_.Name -eq 'gap_receiver_disposition' -and $_.V.packet -eq $token -and $_.At -gt $packet.At })
            Need ($decisions.Count -eq 1 -and $decisions[0].V.forward -eq 'false' -and $decisions[0].V.type -eq 'SPacketSetSlot') 'Actual receiver rejection missing'
            Need (@($events | Where-Object { $_.Name -eq 'main_apply_scope' -and $_.V.packet -eq $token -and $_.At -gt $packet.At }).Count -eq 0) 'Replayed packet reached vanilla apply'
        }
    }
    switch ($Case) {
        epoch {
            $replays = @($events | Where-Object Name -eq 'gap_epoch_replay'); Need ($replays.Count -eq 1) 'Need one actual epoch replay'
            $r = $replays[0]
            Need ($r.V.oldEpoch -ne $r.V.currentEpoch -and [long]$r.V.serial -gt [long]$r.V.watermark) 'Epoch/serial discriminator missing'
            Require-RejectedPacket 'gap_epoch_packet'
            $closes = @($events | Where-Object { $_.Name -eq 'gap_actual_close_accept' -and $_.V.incomingEpoch -eq $r.V.oldEpoch -and $_.At -gt $r.At })
            Need ($closes.Count -eq 1) 'Old close did not reach actual accept method'
            $c = $closes[0].V
            Need ($c.accepted -eq 'false' -and $c.currentEpoch -eq $r.V.currentEpoch -and $c.incomingIntent -eq $c.currentIntent -and
                ($c.currentUse -eq '0' -or $c.incomingUse -eq $c.currentUse) -and $c.incomingHand -eq $c.currentHand -and
                $c.incomingSpell -eq $c.currentSpell) 'Close rejection tuple is not epoch-discriminating'
            $releases = @($events | Where-Object { $_.Name -eq 'gap_actual_release' -and $_.V.incomingEpoch -eq $r.V.oldEpoch -and $_.At -gt $r.At })
            Need ($releases.Count -eq 2 -and $releases[0].V.phase -eq 'before' -and $releases[1].V.phase -eq 'after') 'Actual server release invocation missing'
            Need ($releases[1].V.accepted -eq 'false' -and $releases[0].V.releasedThrough -eq $releases[1].V.releasedThrough -and
                $releases[1].V.currentEpoch -eq $r.V.currentEpoch -and $releases[1].V.intent -eq $releases[1].V.newestIntent) 'Old release changed or did not challenge current state'
            Need (@($events | Where-Object { $_.Name -eq 'gap_epoch_replay_complete' -and $_.V.capsuleEmpty -eq 'true' }).Count -eq 1) 'Replay capsule not consumed'
        }
        retired {
            $r = @($events | Where-Object Name -eq 'gap_retired_replay'); Need ($r.Count -eq 1) 'Need one retired-generation replay'
            Need (@($events | Where-Object { $_.Name -eq 'gap_actual_retirement' -and $_.V.retired -eq 'true' -and
                $_.V.generation -eq $r[0].V.generation -and $_.At -lt $r[0].At }).Count -gt 0) 'Retirement not actually applied before replay'
            Require-RejectedPacket 'gap_retired_packet'
            Need (-not [bool]($Lines -match 'actual_finish_(event|body) side=CLIENT')) 'Old client Finish ran'
        }
        fault {
            $suppressed = @($events | Where-Object Name -eq 'actual_partner_suppressed'); Need ($suppressed.Count -eq 1) 'Actual missing partner not reached'
            $fault = -1
            for ($i=0; $i -lt $Lines.Count; $i++) { if ($Lines[$i] -match 'inventory publication association failed') { $fault=$i; break } }
            Need ($fault -gt $suppressed[0].At) 'Production fault missing'
            $writes = @($events | Where-Object Name -eq 'gap_actual_held_write'); Need ($writes.Count -eq 1) 'Actual assignment not observed exactly once'
            $w=$writes[0]; $received=@($events | Where-Object { $_.Name -eq 'gap_held_receive' -and $_.V.packet -eq $w.V.packet -and $_.At -lt $w.At })
            Need ($received.Count -eq 1 -and $w.At -gt $fault -and $w.V.fault -eq 'true' -and $w.V.requested -eq $w.V.argument -and
                $w.V.argument -eq $w.V.actual -and [int]$w.V.actual -ge 0 -and [int]$w.V.actual -le 8) 'Unrelated packet assignment/identity/fault order not proven'
            Need (-not [bool]($Lines -match 'actual_finish_(event|body) side=CLIENT')) 'Faulted client Finish ran'
        }
        default { throw 'Unknown observation case' }
    }
    [pscustomobject]@{ Result='OBSERVATION_SUPPLEMENT_PASS_NOT_CARD_ACCEPTANCE'; Case=$Case; Events=$events.Count;
        Limits='Requires separate exact-source/capsule chain, setup/action/resource/authoritative effects/Finish/cooldown/recovery/reset and owner physical evidence.' }
}
if (-not $DefinitionsOnly) {
    if (-not $LogPath -or -not $StartMarker) { throw 'Supply exact retained log path and case start marker' }
    $all=@(Get-Content -LiteralPath $LogPath); $start=-1; $end=-1
    for ($i=0; $i -lt $all.Count; $i++) { if ($all[$i].Contains("label=$StartMarker")) { $start=$i } }
    if ($start -lt 0) { throw 'Case start marker missing' }
    for ($i=$start+1; $i -lt $all.Count; $i++) { if ($all[$i].Contains("label=$EndMarker")) { $end=$i; break } }
    if ($end -lt 0) { throw 'Case end marker missing' }
    Test-GapObservation -Case $Case -Lines $all[$start..$end] | ConvertTo-Json
}
