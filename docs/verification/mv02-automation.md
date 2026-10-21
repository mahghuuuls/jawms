# Continuous-use input qualification harness

Development verification only. Nothing here is a player API, shipping feature, permission to launch, or an already qualified input replacement. Product code is unchanged. The driver only supplies the default use binding to the real `Minecraft.processKeyBinds`; vanilla still calls the controller, sends packets and detects release. It never calls a cast, guard transition, permission or resource settlement.

## Implemented boundary

Four **qualification pilots**, in main/offhand mappings: `normal`, `terminal`, `delayed-normal`, `delayed-terminal`. Before any reset/setup mutation, a two-phase preflight binds run/handler/player/world and verifies actual client idle/released/empty-queue state. Only then does server setup invoke existing authorized commands, check independent zero counters/mana and ensure the fixture stream is discovered without toggling an already-discovered spell. First input additionally requires a fresh live server probe acknowledgement. Client/server snapshots and action-window records are collected automatically. The pilot stops after its first gesture or delayed rejected follow-up; no automatic fresh funded gesture, reconnect, restart, broad-case batching or qualification is implemented. Those obligations remain in the approved external MV-02 matrix. `mv02-execution-map.json` retains their execution routing, not results.

## Later authorized preparation

Do not run these steps until the source is reviewed, the exact replacement clean checkpoint is approved and created, and preparation/launch is separately authorized.

- Keep the established exact candidate/toolkit/fixture dependencies, source/classpath identity, paused regeneration/profile and publication-observer setup. Both endpoints need `-Djawms.enableInputPilot=true`; it defaults off. Client additionally loads `mixins.jawms-input-pilot.json` alongside the existing publication observer config. Do not change the current staged init or install these files just to document the recipe.
- Only an exact disposable level beginning `JAWMS-C007-MV02-` is accepted. The level4 command is player-bound, including toolkit-wrapped senders. A normal owner world, console, unprivileged sender or concurrent pilot is rejected. Dedicated scope comes from the actual server level, not the client's generic `MpServer` label.
- The initial P0 checks still precede any automated input. Pilot capability does not replace transform/discovery/linkage evidence. Genuine physical/visual anchor cards remain required separately.

For example, **later**, with an already prepared disposable world and sky aim:

```text
/jawms-mv02-pilot start normal main JAWMS-C007-MV02-I-P-Retry3
```

Close chat, release physical keys/buttons, leave aim at empty sky and keep the client focused. The10-second preflight window allows ordinary key release; no reaction-time action is required. Default right mouse only, with no conflicting use binding. The runner does not move the camera, steal focus, generate OS input or manipulate another app. Any menu, focus/player/world/connection loss, unexpected physical input after start, missing hook, protocol fault or fixed45-second whole-pilot deadline aborts. A fixture-only2-second ACK freshness/round-trip bound also aborts stalled authorization, including before first input. Each acknowledgement subtracts measured round-trip time from server remaining time and can only reduce, never restart/extend the original lease. Server expiry explicitly sends abort. These are conservative harness safety bounds (UNREACHED on slow/stalled delivery), not product latency promises or replacements for the product's30-second connected/unpaused pending deadline.

The corresponding `terminal`, `delayed-normal` and `delayed-terminal` modes accept the same hand/level arguments. They establish their own resource/transport cohorts through existing commands; no manual timing or mid-pending chat command is needed. The level4 `abort` subcommand and client scope checks release only the pilot-owned binding and retire the harness session, never product latches/claims/faults. Setup failure is retained, not retried. No teardown/rollback is automatic: preserve evidence, then use the separately approved normal cleanup/restoration procedure.

## Evidence and limitations

`MV02_PILOT` JSON records carry a unique run UUID. `ACTION_COMPLETE` deliberately says `NOT_QUALIFIED`. Independent server fixture balances and immutable outcome sums, real controller-entry observations, original production intent/release/close diagnostics and original actual callback/application/cooldown records must agree. Mutable `lastContext` is not a settlement oracle. Never use an old run's marks or a final counter to conceal missing, duplicate or out-of-order boundaries.

Offline assertions (not a Minecraft launch), after evidence exists:

```text
.\gradlew.bat verifyMv02PilotEvidence -PpilotRun=<UUID> -PpilotTopology=integrated -PpilotClientLog=<retained-log> --console=plain
```

Dedicated analysis requires `-PpilotTopology=dedicated` plus `-PpilotServerLog=<distinct-retained-server-log>`. Preserve all relevant rotated slices; the reader accepts plain UTF-8 log files, not gzip, and rejects inputs over32MiB. Its Java entry point accepts additional retained slices. Do not overwrite/decompress an original in place. Missing/truncated/ambiguous records produce UNREACHED or FAIL; no result implicitly retries.

An assertion PASS always carries `qualified:false`: it is **only** the reader's bounded numeric/input assertions. Exact source-to-copy/packet-instance/mapping/cooldown target review, transformed hooks, human-anchor comparison, fresh-input recovery and actual remote scheduling remain mandatory for qualification and complete MV-02 acceptance. Unit fixtures test reader sensitivity and key scheduling but cannot prove native input, a transformed client or a real dedicated connection. No pilot has run merely because these sources/tests exist.

If a primitive/hand/topology cannot be faithfully qualified, leave its action manual and retain setup/readback consolidation. No required matrix group is removed. Previously accepted raw evidence remains immutable; source/helper applicability review determines affected reruns, not blanket reuse or full human reruns.
