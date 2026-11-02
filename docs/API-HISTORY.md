# JAWMS API History

This reference records older JAWMS releases and the public API additions they introduced. Use the current [Addon Development Guide](ADDON-DEVELOPMENT.md) for API `1.6`; the `1.1.1` compatibility patch preserves that API without adding a new contract.

## Release Matrix

| JAWMS release | CurseForge file | Public API | Main API addition |
| --- | --- | --- | --- |
| `0.1.0` | `8597828` | `1.2` | Server mana operations, modifier providers, events, tooltip helpers, and replaceable HUD data |
| `0.2.0` | `8621418` | `1.3` | Client HUD context and synchronized snapshot revisions |
| `0.3.0` | `8632899` | `1.3` | Same API generation with the open MixinBooter `11.8+` boundary |
| `0.4.0` | `8651841` | `1.4` | Typed mutation failures, provider observation, cache refresh, profile explanations, and Developer Tools |
| `1.0.0` | See the CurseForge Files page | `1.5` | Configured-flask snapshots, directional lockout helpers, invariant provider previews, and final JAWMS tooltip placement |
| `1.1.0` | See the CurseForge Files page | `1.6` | Explicit attributed regeneration-lockout requests and open minimum-or-newer runtime dependency ranges |
| `1.1.1` | Not yet published | `1.6` | Cleanroom loading compatibility; no API additions |

CurseForge file IDs identify immutable uploaded files. They are not mod versions or API versions.

## Compatibility Policy

API major `1` is additive. A later `1.x` API retains the documented public descriptors from supported earlier minor versions and does not add required methods to an already-published interface. Breaking changes require another API major or a parallel opt-in contract.

JAWMS `1.1.0` retains the published API `1.2`, `1.3`, `1.4`, and `1.5` binary surfaces. New source should use API `1.6`; the older descriptions below exist for maintaining already-published addons.

## Historical Dependency Coordinates

The full JAWMS mod is the compilation input; there has never been a separate `jawms-api` artifact.

```groovy
implementation rfg.deobf('curse.maven:just-another-wizardry-mana-system-1643697:8597828') // 0.1.0 / API 1.2
implementation rfg.deobf('curse.maven:just-another-wizardry-mana-system-1643697:8621418') // 0.2.0 / API 1.3
implementation rfg.deobf('curse.maven:just-another-wizardry-mana-system-1643697:8632899') // 0.3.0 / API 1.3
implementation rfg.deobf('curse.maven:just-another-wizardry-mana-system-1643697:8651841') // 0.4.0 / API 1.4
```

Use only one JAWMS dependency. Current releases use the numeric file ID displayed for their JAR on the CurseForge Files page.

## API 1.2

API `1.2` established the server-authoritative mana service, item and contextual modifier providers, mana state-change events, contribution tooltip formatting, immutable client mana values, and cancellable built-in HUD rendering.

The original client snapshot factory and HUD event constructor have unknown synchronized revision and Creative context because callers cannot supply that later state. They remain for binary compatibility, not as the recommended current source API.

JAWMS `0.1.0` used MixinBooter `[11.11,12.0)`.

## API 1.3

API `1.3` added `ClientHudContext`, scaled dimensions, partial ticks, known-or-unknown Creative state, and connection-local synchronized snapshot revisions. JAWMS `0.2.0` introduced the API; `0.3.0` retained it while changing the MixinBooter requirement to the open range `[11.8,)`.

## API 1.4

API `1.4` added:

- typed `ManaMutationResult` failure information and signed actual deltas;
- immutable provider descriptors and registration-freeze observation;
- immediate and deferred modifier-cache refresh operations;
- accepted-profile explanations with deterministic source records;
- the separately packaged optional JAWMS Developer Tools.

Published JAWMS `0.4.0` file `8651841` is the immutable API-1.4 dependency for addons intentionally remaining on that generation. Its recommended runtime range is `jawms@[0.4.0,0.5.0)`.

## API 1.5

API `1.5` was introduced by JAWMS `1.0.0`. It adds:

- common/server and synchronized-client configured-flask snapshots;
- explicit flat regeneration-lockout increase and reduction helpers;
- declared invariant item-provider previews synchronized without evaluating providers for presentation;
- a final contiguous JAWMS tooltip section after ordinary Forge tooltip subscribers;
- clearer whole-mana, mutation-accounting, and provider-preview contracts.

Current addons should follow [Addon Development](ADDON-DEVELOPMENT.md), not reconstruct current usage by combining the historical sections above.

## API 1.6

API `1.6` is the current stable API in JAWMS `1.1.0`. It adds an attributed logical-server operation that starts or restarts the player's currently accepted regeneration lockout without simulating a spell, consuming mana or hunger, reserving payment, or changing continuous-cast state.

JAWMS `1.1.0` also changes current runtime requirements to minimum-or-newer ranges. Those ranges admit later dependency versions without claiming every later version has been tested.
