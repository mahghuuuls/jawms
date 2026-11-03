# JAWMS Commands

JAWMS commands belong to four different availability tiers. Check the tier before trying a command: installing the normal JAWMS JAR does not install Developer Tools or contributor fixtures.

For the matching JAWMS, public-API, and Developer Tools version boundary, see the canonical [`docs/ADDON-DEVELOPMENT.md`](ADDON-DEVELOPMENT.md) release/API matrix.

## Primary JAWMS

Availability: the normal JAWMS JAR. Audience: server operators and modpack developers. Side: logical server; usable by an operator player or dedicated-server console. Permission level: 4.

### `/jawms`

Command contract: Permission level: 4. Side: logical server. Audience: server operators and modpack developers. Purpose/result: reload and publish authoritative rules with a revision result. Failure conditions: invalid syntax, permission, or configuration leaves the prior revision active. Cleanup/reset: restore configuration and reload again when reverting a test.

Authoritative usage:

```text
/jawms reload
```

| Syntax | Purpose and observable result | Failure conditions | Cleanup or reset |
| --- | --- | --- | --- |
| `/jawms reload` | Reloads and publishes all authoritative JAWMS rules, then reports the old/new revision and warning count. | Invalid syntax, insufficient permission, or invalid configuration. A failed publication leaves the previous revision active and points to the server log. | None. To undo a rules change, restore the configuration and reload again. |

## Optional JAWMS Developer Tools

Availability: the unreleased development candidate builds the separate matching `jawms-devtools-1.2.0.jar` alongside JAWMS `[1.2.0,)`, which provides API 1.7. The runtime range admits newer JAWMS versions without claiming they have all been tested; matching candidate versions still require validation before release. Installing it is the enablement mechanism; JAWMS has no gameplay configuration toggle for it. Audience: addon developers and controlled test servers. Side: logical server; explicit player targets work from dedicated-server console. Permission level: 4. Output is sent to the command sender and logged with the `[jawms-dev]` prefix.

### `/jawms-dev`

Command contract: Permission level: 4. Side: logical server. Audience: addon developers and controlled test-server operators. Purpose/result: inspect or mutate only the public JAWMS API state described below. Failure conditions: Invalid syntax, target, number, permission, side, API state, or operation fails without mutation. Cleanup/reset: restore deliberately changed mana and allow or trigger any requested cache refresh before ending the test.

Authoritative usage:

```text
/jawms-dev state [player] | mana <set|restore|consume|drain> <player> <amount> | providers | explain [player] | cache <invalidate|dirty> <player>
```

| Syntax | Purpose and observable result | Failure conditions | Cleanup or reset |
| --- | --- | --- | --- |
| `/jawms-dev state [player]` | Reports every public mana scalar and gate: current/maximum mana, effective regeneration, effective and remaining lockout in ticks/seconds, both regeneration permissions, continuous-cast activity, and current regeneration eligibility. Omitted target uses the executing player. | Console must supply a target. Invalid target, insufficient permission, wrong side/thread, or unavailable state fails without mutation. | None. |
| `/jawms-dev mana set <player> <amount>` | Sets current mana through the public bounded service and reports success, typed failure, requested amount, actual delta, cause, and old/new current/maximum values. | Invalid target/number/syntax, insufficient permission, wrong side/thread, or a reserved-cast boundary. | Record the starting value and restore it with another `set`; ordinary gameplay may also change mana naturally. |
| `/jawms-dev mana restore <player> <amount>` | Restores up to the requested amount through the public service and reports the typed result fields. | Same command-boundary failures as `set`; negative or invalid amounts are rejected before mutation. | Use `set` or `drain` if the test needs its starting mana restored. |
| `/jawms-dev mana consume <player> <amount>` | Consumes the exact amount or reports typed insufficiency without partial mutation. | Same command-boundary failures as `set`; unaffordable exact consumption reports failure and no state change. | Use `set` or `restore` to restore the starting value. |
| `/jawms-dev mana drain <player> <amount>` | Drains up to the requested amount and reports the actual delta, including a partial drain at low mana. | Same command-boundary failures as `set`; invalid input changes nothing. | Use `set` or `restore` to restore the starting value. |
| `/jawms-dev providers` | Reports whether registration is frozen, the count, and every provider in stable provider-ID order with kind and known target item. | Insufficient permission or command unavailability. | None. |
| `/jawms-dev explain [player]` | Reports the last accepted evaluation tick/rules revision, configured bases, aggregation stages, final values, efficiency/cost maps, permissions, and every considered source with identity, disposition, bounded failure, item/slot, and accepted contribution. The query does not evaluate providers again. | Console must supply a target. It fails clearly if no authoritative evaluation has yet been accepted. | None. |
| `/jawms-dev cache invalidate <player>` | Immediately invalidates the registry cache generation so the next profile evaluation is fresh. Use after the authoritative addon state transition. | Invalid target/syntax, insufficient permission, wrong side/thread, or use during provider evaluation; rejected input changes no cache state. | None; the next ordinary profile evaluation consumes the fresh generation. |
| `/jawms-dev cache dirty <player>` | Coalesces one deferred refresh for the player's END tick. Use before the authoritative addon state transition. | Same command-boundary failures as `invalidate`; rejected input changes no pending state. | None; the pending request is consumed once at PlayerTick END. |

All four mutation operations use the cause `jawms_devtools:command`. Developer Tools intentionally has no wand-mana, casting-cancellation, projectile, balance-rotation, provider-injection, or configuration-bypass control.

## Contributor Development Fixtures

Availability: source-checkout development runs only (`src/developmentFixture`); these commands are not in either released JAR. Audience: JAWMS contributors performing implementation regression and balance work. Side: logical server/integrated server. Permission level: 2. Their controls can deliberately alter fixture, entity, casting, food, item, or mana state.

### `/jawms-test`

Command contract: Permission level: 2. Side: logical server in source-checkout development runs. Audience: JAWMS contributors. Purpose/result: prepare and inspect internal regression states that are deliberately not public API. Failure conditions: invalid syntax, value, player context, item, target, spell, or fixture precondition fails the command. Cleanup/reset: disable probes, use matching clear operations, remove targets, restore player/items, or discard the disposable world.

Player-only operations use the sender's server-player entity, including when a command runner wraps the sender. Feedback and permission checks remain on the original sender; a console or non-player entity is not an implicit player target.

Authoritative usage:

```text
/jawms-test mana set <amount> | mana inspect | rules inspect | integration inspect | integration last-effect | integration clear-last-effect | integration last-melee | integration clear-last-melee | regen pause <true|false> | mana restore <amount> | mana consume <amount> | mana drain <amount> | mana regen <true|false> | mana events <true|false> | mana event-inspect | cost get <namespace:spell> | casting debug [<true|false>] | casting cancel [<true|false>] | casting cancel-tick [<true|false>] | casting factor <positive-number> | casting factor-delay <positive-number> <ticks> | casting payment <inspect|clear> | hunger set <0..20> | rules reload-after-cast <ticks> | flask inspect | armor mana <head|chest|legs|feet> <amount> | wand set <main|off> <namespace:spell> | wand siphon <main|off> <0..3> | wand melee <main|off> <0..3> | melee event <normal|cancel|zero> | melee target <health> | melee target-inspect | projectile <inspect|clear-last> | projectile activation <normal|active|inactive> | projectile activation-after-cast <active|inactive> <ticks> | projectile mana-after-cast <amount> <ticks> | wand mana <main|off> <amount> | wand inspect <main|off>
```

Subcommands and purpose:

- `mana set`, `mana restore`, `mana consume`, `mana drain`, and `mana inspect` control or inspect the executing contributor's player-mana state.
- `mana regen`, `regen pause`, `mana events`, and `mana event-inspect` control regeneration/event probes and inspect their last observation.
- `rules inspect`, `rules reload-after-cast`, `integration inspect`, `integration last-effect`, `integration clear-last-effect`, `integration last-melee`, and `integration clear-last-melee` inspect or reset authoritative rules/integration probes.
- `cost get`, `casting debug`, `casting cancel`, `casting cancel-tick`, `casting factor`, `casting factor-delay`, and `casting payment <inspect|clear>` exercise contributor-only casting hooks and payment observations.
- `hunger set`, `flask inspect`, and `armor mana` prepare or inspect Wizardry integration state.
- `wand set`, `wand siphon`, `wand melee`, `wand mana`, and `wand inspect` prepare held-wand regression cases.
- `melee event`, `melee target`, and `melee target-inspect` prepare and inspect the contributor melee target/event fixture.
- `projectile inspect`, `projectile clear-last`, `projectile activation`, `projectile activation-after-cast`, and `projectile mana-after-cast` control and inspect contributor projectile regression probes.

Failures include invalid syntax/value, missing player context, missing item/spell/target, or unmet fixture preconditions. Cleanup: return boolean probes to `false`/`normal`, use the matching `clear` operations, restore mana/hunger/items, remove spawned targets, and restart the disposable world when complete isolation is needed.

### `/jawms-balance`

Command contract: Permission level: 2. Side: logical server in source-checkout development runs. Audience: JAWMS contributors running balance repetitions. Purpose/result: prepare, record, inspect, and close named balance rotations. Failure conditions: invalid player context, rotation, repetition, note, value, or lifecycle order fails the command. Cleanup/reset: stop active runs as aborted, disable the boost, and discard invalid evidence.

Authoritative usage:

```text
/jawms-balance rotations | setup <rotation> | start <rotation> <1|2|3> | note <hit|miss|failure> [count] | note downtime <ticks> | boost <true|false> | profile | status | stop <completed|depleted|aborted>
```

`rotations` lists available models; `setup` prepares one; `start` begins a numbered repetition; `note` records outcomes/downtime; `boost` toggles the fixture percentage profile; `profile` and `status` inspect it; and `stop` closes a repetition with its disposition. Failures include invalid player context, rotation, repetition, note, number, boolean, or lifecycle order. Cleanup: `stop aborted` for an active run, set `boost false`, restore the inventory/world state, and discard the generated balance evidence if the session was invalid.

### `/jawms-use-transport`

Command contract: Permission level: 4. Side: logical server controlling only the executing player's separately sided development observer. Audience: JAWMS contributors. Purpose/result: observe real channel frames, hold a real close until a rejected physical press, duplicate/replay previously emitted frames, or withhold the next connection's capability. Failure conditions: invalid syntax, non-player sender, or missing channel observer fails; asynchronous arm/queue failures are explicit log errors and invalidate readiness. Cleanup/reset: release input, run `reset` to deliver any actual held frame and discard history, then inspect empty queues. It never clears a production guard, manufactures permission or performs payment.

Authoritative usage:

```text
/jawms-use-transport <mode>|capability-off|inspect|reset
```

Unreleased development helper; no runtime validation or launch approval. Modes: `normal`, `gate`, `stale`, `stall`, `pub-first`, `pair-first`, `bulk`, `direct`, `replace`, `wheel`, `spell`, `upgrade`, `source-reentry`, `callback`, `pair-fault`, `late`, `stale-pair`, `change`. The first four retain the close/input tests. New publication modes additionally require `-Djawms.enablePublicationObserver=true` and program arguments `--mixin mixins.jawms-publication-observer.json` on both named development endpoints. They are not packaged-instance recipes. `direct` fills other hotbar slots with sticks in the disposable world so the real MC|PickItem handler can correct the same wand without changing selection; restore inventory afterward. Explicit modes may mutate the operator's inventory/spell/upgrade context at the reached pending/copy/Finish boundary. They never invoke a validity guard or create provenance/normal permission. `gate` requires an observed rejected press before delivery; `stall` requires explicit teardown/reconnect. Timeout cannot be recovered by this command. One bounded local-operator byte history may survive disconnect to test old-epoch rejection; no player/world/callback references survive disconnect. Observer identity space is bounded to256 objects and2048 lines per explicit reset; overflow marks evidence UNREACHED, not a pass. Pair/application queues are bounded to256 each. Neither helper, observer Mixin configuration nor command ships in JAWMS or Developer Tools.

Each fixture subscribes only once to Forge's event bus (the FML accessor aliases it in 1.12.2).
Connection notifications are idempotent: the observer lookup is published only after the named
pipeline handler is attached, and removal/disconnect retires it. A missing observer is not readiness.

Contributor observer support lives outside the Mixin-definition package: ordinary callable
classes must not be placed under `com.mahghuuuls.jawms.testfixture.publication` or its
subpackages. The packet observers separately target the named SetSlot and WindowItems
parameterized constructors; their empty decoding constructors do not copy stacks.
`PublicationObserverContractTest` checks compiled package isolation and the real Mixin
selector parser against those exact bytecode copy sites. It does not bootstrap transformed
Minecraft; an approved development startup and live observation are still required.

### `/jawms-mv02-pilot`

Command contract: Permission level: 4. Side: logical server with an explicitly opted-in connected development client. Audience: JAWMS contributors performing controlled input qualification. Purpose/result: establish a bounded run/world/player/connection preflight, execute existing fixture setup only after client-idle proof, and collect automatic use-key pilot observations. Failure conditions: missing development opt-in/observers/fixture, wrong disposable level, unauthorized or non-player sender, unresolved input, stale live acknowledgement, missing evidence or scope loss stops the pilot; action completion never qualifies input. Cleanup/reset: abort and release only the pilot-owned key, preserve logs, then restore fixture/configuration through the separately approved normal cleanup. Never clears product guards, claims, latches or faults.

Authoritative usage:

```text
/jawms-mv02-pilot start <normal|terminal|delayed-normal|delayed-terminal> <main|off> <exact-disposable-level> | abort
```

Disabled by default through `-Djawms.enableInputPilot=true` on both development endpoints; the client additionally needs the separately loaded `mixins.jawms-input-pilot.json`. See [the qualification-harness guide](verification/mv02-automation.md) for preflight, fixed whole-run/freshness bounds, no-bypass limits and offline assertions. Four pilot kinds only; no launch approval, automatic qualification, broad-case runner, reconnect or registration-off restart. Physical anchors and every required matrix variant remain. Neither command, driver, helper nor input Mixin ships in primary JAWMS or Developer Tools.

## API Compatibility Fixtures

Availability: separately compiled compatibility fixtures used by the JAWMS project; not shipped in primary JAWMS or Developer Tools. Audience: JAWMS API maintainers and compatibility reviewers. Fixture behavior is not a public compatibility promise.

### `/jawms-cast-fixture`

Command contract: Permission level: 2. Side: logical server/integrated server, separate cast-cost fixture. Audience: JAWMS casting/API maintainers. Purpose/result: prepare an independently counted synthetic resource and harmless addon spell, inspect actual effects/payments/order, or safely catch intentional false/throwing effects at a command boundary. Failure conditions: invalid syntax/mode, non-player sender, or an unequipped probe wand fails; intentional probe exceptions alone are caught. Cleanup/reset: `setup` resets counters/resource and replaces the held wand; `teardown` disables this player's participation. Use only disposable worlds, restore inventory/config after the session, and remove the fixture JAR afterward.

Authoritative usage:

```text
/jawms-cast-fixture setup <absent|allow|refuse|free> [base] | inspect | probe <false|throw|nested|invalidate|settle-throw|final-invalidate> | teardown
```

`base` equips the registered Wizardry Light spell instead of the fixture spell. Every setup starts the fixture balance at 40, mana at 50 and hunger at 20. A paid fixture effect costs four fixture units. Absent/refuse/free cases are explicit and log the distinction. The ordinary synthetic effect shows "Cast fixture: effect reached" without damaging entities or changing blocks. Probe false/throw uses the real wand canCast/cast path with intentional exceptions caught outside the effect, not an ordinary uncaught world tick. The additional disposable-world probes exercise a nested cast, provider invalidation, a provider debit followed by an exception, or removal of the original wand during final notification. Run setup before each probe. Build with `castCostFixtureJar`/`reobfCastCostFixtureJar`; add `-PjawmsIncludeCastCostFixture=true` to development launches. Logs use `CAST_FIXTURE`. Never install the frozen API-1.6 consumer and rebuilt API compatibility fixture together; their mod IDs match.

Development launches load the deobfuscated fixture JAR as one mod origin containing both classes and spell properties. Separate class/resource directories are insufficient because Wizardry scans the mod's own origin for spell JSON. `verifyCastCostFixtureDevelopmentLaunch` checks both development launch classpaths without starting Minecraft; run it with `-PjawmsIncludeCastCostFixture=true` to check the enabled fixture cohort.

Fixture spells are constructed only in the spell-registration callback, after the dependency's built-ins, not when preInit loads continuous support. Wizardry assigns network IDs during construction and later renumbers only addon spells; early construction leaves gaps/collisions and corrupts login property synchronization. `CastCostFixtureRegistrationTest` checks early class loading and runs actual fixture registration, Wizardry's sorter and complete login-message encode/decode with real None/Light schemas and all five fixture spells. It isolates spell/fixture state and uses an in-memory registry adapter, with explicit logger/None ObjectHolder setup. The one shared SpellActions owner initializes Minecraft's action enums once; registration asserts those shared enums remain unchanged. The test does not execute FML event dispatch, the full built-in registry, transformations or a network/world launch. Those remain startup-validation obligations.

### `/jawms-continuous-fixture`

Command contract: Permission level: 2. Side: logical server/integrated server, separate public-only cast-cost fixture. Audience: JAWMS casting/API maintainers. Purpose/result: select harmless continuous cohorts, independent side-keyed effect/finish counters and permitted partial synthetic resource payments. Failure conditions: invalid syntax/mode, non-server-player sender, active item use, undiscovered spell properties or a maximum other than the required 100 fails setup. Cleanup/reset: release use first; `setup` replaces the fixture state and wand, `teardown` removes resource state; neither resets production protocol state. Restore inventory/config and remove the fixture after testing.

Authoritative usage:

```text
/jawms-continuous-fixture setup <mode> [main|off] | inspect | teardown
```

Unreleased development candidate; implementation and automated review are in progress, with no runtime acceptance. Modes are normal, terminal, creative, zero, zero-portion, prediction, inapplicable, refused, invalidated, chargeup, synthetic, nofinish, tick-interrupt and creative-interrupt. The last two return not-applicable and cancel a real server SpellCastEvent.Tick after two successful effects; ordinary Finish remains eligible, unlike a cancelled stop callback. Fixed spell rates are 4/1/0 and charge-up 200. Three fixture units per applicable effect, with permitted terminal partial payment; terminal/Creative/zero start at 2 and zero-portion at 5. Other cohorts start at 1,000,000. `prediction` records a provider-local terminal estimate but its actual receipt continues; no prediction API exists. Public fixture cooldown counters observe positive transitions, not setter invocation counts. The separate development observer records actual setter targets. Disable registration only at startup with `-Djawms.castFixtureRegistrationOff=true` for the separate empty-registry cohort. Do not change this flag during a connection. Bundles are retained in `docs/verification/continuous-bundles.json`, not installed or live-verified.

### `/jawms-api-fixture`

Command contract: Permission level: 2. Side: logical server/integrated server compatibility fixture. Audience: JAWMS API maintainers and compatibility reviewers. Purpose/result: exercise public API linkage, mutations, providers, refresh timing, and accepted explanations with counters. Failure conditions: invalid player context, syntax, boolean, or missing accepted evaluation fails the command. Cleanup/reset: disable provider probes, invalidate after non-deferred changes, restore mana, and inspect status.

Side: logical server/integrated server. Permission level: 2.

Authoritative usage:

```text
/jawms-api-fixture inspect | explain | cycle | lockout | providers <status|registrations|preview-replace|preview-clear|invalidate|dirty|contextual|contextual-deferred|null|throwing|mutation|bauble> [true|false]
```

`inspect` reports API state/version; `explain` reports retained evaluation identity and before/after provider counters; `cycle` performs the fixture mutation round-trip; `lockout` applies the accepted regeneration lockout and reports before/after state, applied ticks, event count, cause, and provider counters; provider `status` and `registrations` inspect counters/descriptors; `preview-replace` and `preview-clear` exercise invariant-preview publication while retaining provider call counters; `invalidate` and `dirty` exercise refresh timing; and the boolean provider operations enable or disable contextual, deferred-contextual, null, throwing, recursive-mutation, or Baubles probes. Failures include invalid player context, syntax, boolean, or unavailable accepted evaluation. Cleanup: let the requested lockout expire, use `preview-clear`, disable every enabled provider probe, invalidate the cache after non-deferred state changes, restore mana, and inspect status before ending the session.

### `/jawms-api-hud`

Command contract: Permission level: 0. Side: physical-client compatibility fixture. Audience: JAWMS client-API maintainers and compatibility reviewers. Purpose/result: control and inspect replacement-HUD context, visibility, cancellation, ordering, and failure probes. Failure conditions: invalid syntax, flag, or mode fails the command. Cleanup/reset: turn probe/mode/earlier/parent/equal/replacement controls off.

Side: physical client command/control fixture. Permission level: 0.

Authoritative usage:

```text
/jawms-api-hud probe <on|off> | mode <off|visible|hide-full|hide-creative|exception> | earlier <on|off> | parent <on|off> | equal <off|ab|ba> | replacement <on|off> | status
```

`probe` observes client context/revision, `mode` selects replacement behavior, `earlier` and `parent` exercise cancellation ordering, `equal` chooses equal-priority claimant order, legacy `replacement` selects visible/off replacement behavior, and `status` reports counters/outcomes. Failures include invalid syntax or mode. Cleanup: use `probe off`, `mode off`, `earlier off`, `parent off`, `equal off`, and `replacement off` before leaving the fixture session.
