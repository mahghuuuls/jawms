# JAWMS Addon Development

This is the canonical integration guide for Just Another Wizardry Mana System. JAWMS exposes Java 8 API types in these two package trees only:

- `com.mahghuuuls.jawms.api` for logical-server mana operations, modifier providers, state changes, and tooltip formatting.
- `com.mahghuuuls.jawms.api.client` for immutable synchronized client values and HUD replacement.

Types under `com.mahghuuuls.jawms.internal` are implementation details and carry no compatibility promise.

## Release And API Matrix

JAWMS mod versions, CurseForge file identities, and API versions are separate identifiers. A CurseMaven coordinate identifies one immutable uploaded file; it does not mean that every artifact with a compatible Java API has the same coordinate.

| JAWMS distribution | CurseForge file | Public API | Direct dependency status |
| --- | --- | --- | --- |
| Published `0.1.0` beta | `8597828` | `1.2` | Verified from a clean ForgeDevEnv consumer |
| Published `0.2.0` beta | `8621418` | `1.3` | Resolved through standard CurseMaven and consumed by completed companion addons |
| Published `0.3.0` beta | `8632899` | `1.3` | Current immutable dependency recipe; MixinBooter `11.8` or newer |
| Candidate `0.4.0` beta | Not published; file identity pending | `1.4` | Source, compatibility-fixture, and build evidence only until publication |

API major `1` follows an additive compatibility policy. A higher minor version retains the documented public descriptors from supported earlier minor versions and does not add required methods to an already-published interface. A breaking change requires another major version or a parallel opt-in contract. A type may be deprecated during major `1`, but deprecation does not authorize its removal from a later `1.x` release.

The current API version is available as `ManaApi.API_VERSION` and `ManaApiVersion.CURRENT`. Use the matrix above to choose a real distribution file; do not infer a CurseForge file ID from those constants.

## Published `0.3.0` Dependency Recipe

The supported compilation input is the full JAWMS mod. There is no separately published `jawms-api` artifact. In a current [CleanroomMC ForgeDevEnv](https://github.com/CleanroomMC/ForgeDevEnv) project, add the following repository blocks:

```groovy
repositories {
    maven {
        name 'CleanroomMC Maven'
        url 'https://maven.cleanroommc.com'
    }
    exclusiveContent {
        forRepository {
            maven {
                name 'CleanroomMC CurseMaven'
                url 'https://curse.cleanroommc.com'
            }
        }
        filter {
            includeGroup 'curse.maven'
        }
    }
}
```

Then add these exact dependencies:

```groovy
dependencies {
    implementation rfg.deobf(
            'curse.maven:just-another-wizardry-mana-system-1643697:8632899'
    )
    runtimeOnly rfg.deobf(
            'curse.maven:electroblobs-wizardry-265642:8320066'
    )
    runtimeOnly 'zone.rong:mixinbooter:11.8'
}
```

Declare the same runtime requirements in the addon mod's Forge metadata. The `@Mod` declaration is authoritative during loading:

```java
@Mod(
        modid = "exampleaddon",
        name = "Example JAWMS Addon",
        version = "1.0.0",
        dependencies = "required-after:jawms@[0.3.0,0.4.0);"
                + "required-after:ebwizardry@[4.3.19,4.3.20);"
                + "required-after:mixinbooter@[11.8,)"
)
public final class ExampleAddon {
}
```

Also list the three ranges in `mcmod.info` if that file is part of the project:

```json
"requiredMods": [
  "jawms@[0.3.0,0.4.0)",
  "ebwizardry@[4.3.19,4.3.20)",
  "mixinbooter@[11.8,)"
]
```

Do not rely on CurseMaven transitive metadata to install Wizardry or MixinBooter. They are explicit runtime dependencies of JAWMS on standard Forge and must be present in development and in the installed modpack.

### Historical `0.1.0` And `0.2.0` Dependencies

Published `0.1.0` file `8597828` provides API `1.2`. Published `0.2.0` file `8621418` provides API `1.3`:

```groovy
implementation rfg.deobf('curse.maven:just-another-wizardry-mana-system-1643697:8597828')
implementation rfg.deobf('curse.maven:just-another-wizardry-mana-system-1643697:8621418')
```

Use only the one JAWMS dependency matching the release you target. Published `0.1.0` and `0.2.0` require MixinBooter `11.11` through versions below `12.0`. Addons targeting those immutable releases should declare `jawms@[0.1.0,0.3.0)` or the narrower `jawms@[0.2.0,0.3.0)` as appropriate and retain the historical `[11.11,12.0)` MixinBooter range.

### Published `0.3.0` MixinBooter Boundary

Published `0.3.0` uses exact MixinBooter `11.8` as the development dependency:

```groovy
runtimeOnly 'zone.rong:mixinbooter:11.8'
```

Its `@Mod` and `mcmod.info` requirements are both open-ended from the proven minimum:

```text
required-after:mixinbooter@[11.8,)
```

The open range means JAWMS accepts MixinBooter `11.8` or newer; it does not mean every future release has been tested. Exact standalone `11.8` and `11.13` are the explicit standard-Forge validation lanes retained for that release. Addons requiring JAWMS `0.3.0` should declare `jawms@[0.3.0,0.4.0)` and must not copy the historical `[11.11,12.0)` MixinBooter range into current metadata.

### Candidate API `1.4` Source Boundary

JAWMS `0.4.0` advances the public API to `1.4`, but it has no CurseForge file identity until publication. Do not invent a CurseMaven coordinate for it. The API-`1.4` examples below describe the candidate source and are compiled by JAWMS's maintained compatibility fixture. Continue using published `0.3.0` file `8632899` for an independently resolvable dependency until a real `0.4.0` file exists.

If addon source directly uses Wizardry types exposed by the server API, such as `Spell` or `Element`, change the Wizardry declaration from `runtimeOnly` to `implementation` while keeping the same deobfuscated coordinate. The replacement-HUD-only example does not directly use those types, so `runtimeOnly` is sufficient there.

For a ForgeDevEnv addon that does not use its own Mixins, make both settings explicit in `gradle.properties`:

```properties
use_mixins = false
mixin_configs =
```

Leaving the template's example `mixin_configs` name non-empty can put a nonexistent config in the addon JAR manifest. Because JAWMS brings MixinBooter into the runtime, that invalid manifest entry is discovered during launch even though the addon itself does not need Mixins.

## Server API Quick Start

All `IManaService` methods require a logical-server player and the Minecraft server thread. Current mana is server-authoritative; client values are display-only.

The basic service and provider operations below exist in published API `1.3`. Sections that explicitly identify an API-`1.4` addition require the local `0.4.0` candidate until that version receives a real published file identity.

```java
private static final ResourceLocation POTION_RESTORE =
        new ResourceLocation("exampleaddon", "potion_restore");

public void restoreFromPotion(EntityPlayer player) {
    ManaMutationResult result = ManaApi.getManaService()
            .restoreMana(player, 20, POTION_RESTORE);
    int restored = result.getActualAmount();
}
```

Mutation amounts are non-negative whole mana points. `setCurrentMana` clamps to the effective maximum, `restoreMana` restores up to the requested amount, `consumeMana` consumes the exact amount or changes nothing, and `drainMana` drains up to the requested amount. Every mutation needs a namespaced cause, applies to Creative players, synchronizes automatically, and does not start the post-cast regeneration delay.

API `1.4` adds `ManaMutationResult.getFailure()`. `setCurrentMana`, `restoreMana`, and `drainMana` return successful `NONE`, including clamped or bounded no-ops. An affordable `consumeMana`, including consuming zero, also returns successful `NONE`; an exact consume that cannot be paid returns unsuccessful `INSUFFICIENT_MANA` without changing state. The service does not otherwise return `REJECTED`; the retained constructor uses that conservative reason only when an external legacy caller constructs an unsuccessful result.

| Operation | Ordinary result |
| --- | --- |
| `setCurrentMana` | Successful `NONE`; the requested value is bounded to the effective maximum. |
| `restoreMana` | Successful `NONE`; the actual increase can be zero at full mana or smaller than requested near full mana. |
| `consumeMana` | Successful `NONE` for an affordable exact amount, including zero; otherwise unsuccessful `INSUFFICIENT_MANA` with no state change. |
| `drainMana` | Successful `NONE`; drains up to the request and can report a smaller or zero actual amount. |

Invalid amounts or causes, wrong logical side or thread, a missing capability, mutation during provider evaluation, mutation-depth overflow, and setting current mana below an active reservation remain exceptions rather than ordinary failure results.

Subscribe to `ManaStateChangeEvent` on the ordinary Forge event bus to observe committed logical-server changes. It provides immutable old/new state, actual current-mana delta, cause, and the affected server player. The event is not a client notification and is not cancellable.

### Modifier Providers

Register providers during Forge pre-initialization or initialization. Provider IDs are globally unique, evaluation order is provider-ID order, duplicate IDs fail, and registration closes at load-complete.

Implementing `IItemManaModifierProvider` or `IContextualManaModifierProvider` does not register anything by itself. Every provider must be passed explicitly to the matching `ManaApi.register...Provider` method during the open registration window.

```java
@Mod.EventHandler
public void preInit(FMLPreInitializationEvent event) {
    ManaApi.registerItemProvider(
            new ResourceLocation("exampleaddon", "mana_ring"),
            ModItems.MANA_RING,
            (player, context) -> ManaContribution.builder()
                    .flatMaximumMana(10)
                    .regenerationIncrease(25.0D)
                    .build()
    );
}
```

An item provider is evaluated only for an active armor, main-hand, offhand, or available Baubles slot containing its target item. `ManaItemContext` supplies a defensive stack copy, `ManaSlotCategory`, and a stable index for that category. A contextual provider contributes player-wide state such as an active potion effect or enchantment:

```java
ManaApi.registerContextualProvider(
        new ResourceLocation("exampleaddon", "mana_effect"),
        player -> player.isPotionActive(ModPotions.MANA_SURGE)
                ? ManaContribution.builder().flatRegeneration(1.5D).build()
                : ManaContribution.EMPTY
);
```

Provider callbacks run on the logical server and must be deterministic, side-effect-free, and reasonably cheap. They may query JAWMS, but must not mutate mana or call `invalidateModifierCache` from inside evaluation. Return one complete immutable contribution. Returning `null` or throwing a runtime exception invalidates only that provider result and caches the failure for the rest of the evaluation tick.

API `1.4` adds provider-registry observation. `ManaApi.getRegisteredProviders()` returns an immutable, provider-ID-sorted snapshot of the registrations accepted so far. Each `ManaProviderDescriptor` exposes the provider ID and whether it is an item or contextual provider. Item descriptors expose a target item registry ID when one is known; call `hasKnownTargetItemId()` before `getTargetItemId()`. The target can be explicitly unknown before the item receives a registry name, and contextual providers never have a target item. A snapshot never exposes provider callbacks, `Item` objects, or mutable registry state. `ManaApi.isProviderRegistrationFrozen()` reports whether load-complete has closed registration; an earlier snapshot remains unchanged when later legal registrations are accepted.

API `1.4` provides two explicit refresh operations for addon state that JAWMS cannot otherwise observe:

- Call `ManaApi.invalidateModifierCache(player)` **after** the authoritative addon state has changed. This invalidates immediately in the current server tick and clears any older deferred request for that player.
- Call `ManaApi.markModifierCacheDirty(player)` from a callback that runs **before** the authoritative addon state changes. Duplicate requests coalesce by player UUID, do not evaluate providers synchronously, and are consumed before that player's next server END-tick profile evaluation. A dirty request made after an immediate invalidation still schedules one later refresh.

Both operations require a logical-server player on the Minecraft server thread and reject calls during provider evaluation. Logout, clone/respawn, dimension change, and server shutdown discard pending refresh state. Ordinary equipment changes and normal next-tick evaluation already refresh profiles, so do not request an extra refresh for them.

Potion transitions need special timing in Forge 1.12.2. `PotionAddedEvent` is posted before a new effect is inserted or an existing amplifier/duration is combined. Uncancelled `PotionRemoveEvent` and `PotionExpiryEvent` are also posted before removal. For a logical-server `EntityPlayerMP`, call `markModifierCacheDirty` from those pre-transition callbacks so the coalesced server END-tick refresh observes the new effect, amplifier replacement, removal, or expiry. Filter out client-side entities. If addon code owns a different callback that runs only after its authoritative state has changed, call `invalidateModifierCache` instead. Provider callbacks themselves remain pure and must never request either refresh.

### API `1.4` Accepted Profile Explanation

`ManaApi.explainManaProfile(player)` returns the immutable server-only explanation retained from the latest profile evaluation accepted by gameplay. It does not scan equipment, invoke a provider, post an event, mutate mana, or send a packet. If the player has not accepted an evaluation yet, it throws `IllegalStateException` instead of performing a speculative calculation.

The explanation includes the evaluated world tick, gameplay-rules revision, configured bases, numeric aggregation stages, every final profile value, and deterministic source records. Each `ManaContributionExplanation` identifies its stable source ID and kind, optional item and slot identity, one disposition, and a bounded failure category. Only `APPLIED` records expose an accepted `ManaContribution`:

- `APPLIED`: the immutable contribution participated in the accepted arithmetic;
- `EMPTY`: the source was evaluated successfully but contributed nothing;
- `DISABLED`: server rules disabled the relevant provider before invocation;
- `OVERRIDDEN`: a configured or generated item layer won precedence, so the matching item provider was not invoked;
- `INVALID`: the provider returned `null` or an invalid contribution;
- `FAILED`: the provider callback threw an exception.

All contextual providers are represented, including empty and disabled providers. Item providers appear only when their target item occupies an active armor, hand, or available Baubles slot. Unmatched item providers remain visible through `ManaApi.getRegisteredProviders()` but are absent from a player's explanation. Failure records expose only `ManaContributionFailure`; they never expose exception text, stack traces, callbacks, players, items, configuration objects, or other mutable runtime state.

### Contribution Units And Arithmetic

| Contribution | Unit and meaning |
| --- | --- |
| `flatMaximumMana` | Signed whole mana, added to configured base before percentages |
| maximum increase/reduction | Percentage values; increases add together, reductions are independent remaining factors |
| `flatRegeneration` | Signed mana per second, added before percentages |
| regeneration increase/reduction | Percentage values using the same ordering as maximum mana |
| `flatLockoutSeconds` | Signed seconds added to the configured post-cast delay before percentages |
| lockout increase/reduction | Percentage values using the same ordering as maximum mana |
| spell efficiency | Non-negative points, global or per Wizardry element |
| spell-cost multiplier | Strictly positive factor, global or per element; `0.5` halves and `1.5` increases cost by half |
| regeneration permissions | Independent permissions for post-cast delay and continuous casting |

For maximum mana, regeneration, and delay, resolution is:

```text
max(0, configured base + sum(flat))
    * (1 + sum(percentage increases) / 100)
    * product(1 - each percentage reduction / 100)
```

After those factors, maximum mana is floored and bounded to `1..2147483647`. Regeneration is non-negative and measured in mana per second; fractional regeneration accumulates internally while current mana remains whole. Delay is non-negative seconds and is rounded upward only when converted to ticks.

Spell efficiency is not a percentage. With configured efficiency scale `K`, its cost factor is `K / (K + applicable points)`. Global and matching-element cost multipliers multiply independently with that factor and Wizardry's retained contextual cost factor before final upward rounding.

`ManaTooltips.format(contribution)` is an optional server-safe formatter for addon-owned contribution tooltips. Configuration rules can override an API item provider's entire contribution for a stack, and a server operator can disable a registered provider ID, so do not present an API contribution as guaranteed active without considering server rules.

## Client Snapshot

`ClientManaApi.getSnapshot()` returns one immutable `ClientManaSnapshot`. Its classes are safe to load on a dedicated server, where the snapshot remains unavailable; actual rendering belongs behind the addon's physical-client boundary.

Always check `snapshot.isAvailable()` before using its mana values. An available snapshot contains:

- current whole mana;
- effective maximum whole mana;
- effective regeneration in mana per second;
- in API `1.3`, a known or explicitly unknown snapshot revision.

A known revision identifies an accepted synchronized state and never decreases within one connection. It is not a timestamp, packet age, cast-eligibility flag, remaining lockout, or continuous-cast state. Compare known revisions only within the current connection. Before initial synchronization and after disconnect, the snapshot is unavailable and its revision is unknown. The retained API-`1.2` factory also creates an available snapshot with unknown revision because externally supplied values have no synchronized identity.

## Replacement HUD Lifecycle

JAWMS publishes `ManaHudRenderEvent` on the Forge event bus. This table is part of the API-major-`1` rendering contract.

| Question | Contract |
| --- | --- |
| Physical side | Client only; the public value classes themselves remain dedicated-server-safe |
| Thread | Minecraft render thread |
| Forge phase | `RenderGameOverlayEvent.Chat` |
| Posting gates | A local player exists, the snapshot is available, GUI is visible, and the player is not a spectator |
| Creative | Eligible; API `1.3` JAWMS events provide known Creative state in `ClientHudContext` |
| Built-in config order | Event is posted before JAWMS checks its built-in `enabled` and hide-when-full settings |
| Parent cancellation | A cancelled parent Forge `Chat` event does not by itself prevent an otherwise eligible JAWMS event |
| Event lifetime | One render callback only; do not retain the event or its context as live game state |
| Disconnect | Snapshot becomes unavailable; no old player's synchronized values remain available |
| Cancellation scope | Suppresses only JAWMS's built-in draw for that callback; it does not affect mana, synchronization, the addon's draw, or later frames |

### Best-Effort Ownership Pattern

Use `LOWEST` priority and `receiveCanceled=false`. If the replacement is disabled, return without touching the event. Once enabled, cancel before applying the replacement's own visibility policy. This prevents JAWMS's built-in bar from appearing when the replacement intentionally hides at full mana, in Creative, or for another addon-specific reason.

The following minimal flow compiles against the published `0.3.0` / API-`1.3` dependency recipe above. It deliberately uses only the retained API-`1.2` subset, so it also illustrates the older compatibility boundary and implements only an enabled switch and full-mana policy:

```java
@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
public void renderMana(ManaHudRenderEvent event) {
    if (!replacementEnabled) {
        return;
    }

    event.setCanceled(true);

    ClientManaSnapshot snapshot = event.getSnapshot();
    if (hideWhenFull
            && snapshot.getCurrentMana() >= snapshot.getMaximumMana()) {
        return;
    }

    renderReplacement(
            snapshot,
            event.getPartialTicks(),
            event.getScaledWidth(),
            event.getScaledHeight()
    );
}
```

API `1.3` adds immutable `ClientHudContext`. A replacement compiled against published `0.3.0` file `8632899` may add a known-state Creative policy before drawing:

```java
ClientHudContext context = event.getContext();
if (hideInCreative
        && context.isCreativeModeKnown()
        && context.isCreativeMode()) {
    return;
}
```

Do not copy the published `0.1.0` dependency coordinate and then use `getContext()`: API `1.2` does not contain that method. JAWMS `0.2.0` was the first API-`1.3` release, while published `0.3.0` file `8632899` is the current dependency recipe.

This is best-effort ownership, not a provider-selection system. An earlier subscriber can cancel first, in which case `receiveCanceled=false` skips this subscriber. If two replacements use the same priority, Forge registration order decides which runs first; JAWMS does not define a stable winner.

The API-`1.2` event constructor and scalar getters remain available. Events built through that older constructor have unknown Creative context in API `1.3`; call `isCreativeModeKnown()` before `isCreativeMode()` because the latter deliberately throws for unknown state.

### Rendering-State Responsibilities

The event guarantees the phase and immutable values above, not an undocumented OpenGL entry state. A replacement renderer must:

1. capture every entry state it intends to restore;
2. establish every texture, blend, alpha, depth, lighting, color, active-texture-unit, and matrix state it relies on;
3. balance every matrix push with a pop in `finally`;
4. restore changed state through `GlStateManager` so Minecraft's Java-side cache agrees with actual OpenGL state;
5. restore the default texture unit's binding and enable state, the previous active texture unit, blend function, alpha function, color, and every other state it changed.

Do not use raw `glPushAttrib/glPopAttrib` as the only restoration mechanism: it can restore OpenGL while leaving `GlStateManager`'s cache stale. JAWMS cannot recover from arbitrary rendering-state misuse by another subscriber.

The maintained, executable Java 8 reference is [`CacheSafeReplacementHud`](../src/apiCompatibilityFixture/java/example/jawmsaddon/CacheSafeReplacementHud.java). It is fixture source rather than public API: copy or adapt its pattern; never depend on the fixture class or package. Its `try/finally` guard demonstrates a partially transparent texture, default-texture-unit handling, matrix balance, and cache-consistent restoration.

## Optional Developer Tools

JAWMS `0.4.0` builds a separate `jawms-devtools-0.4.0.jar` for addon-development environments. Install it beside the matching JAWMS version when you need permission-level-4 state, mutation, provider, accepted-explanation, or cache-refresh diagnostics. It uses only the documented public API and is not required by the primary JAWMS mod. Removing the tool JAR removes `/jawms-dev` without disabling JAWMS.

The canonical [`docs/COMMANDS.md`](COMMANDS.md) reference distinguishes primary operator commands, optional Developer Tools, contributor-only development fixtures, and API compatibility fixtures. Do not distribute contributor or compatibility fixtures as addon-development tools.

## Testing A HUD Addon

Compile public code for Java 8 and test it in a real Forge 1.12.2 client. A plain JVM test can validate immutable values and listener logic, but it cannot prove that Forge transformed `ManaHudRenderEvent` with its injected cancelable-event behavior. At least one launched Forge test must observe the real callback, call `setCanceled(true)`, and confirm that the built-in JAWMS draw is suppressed while the replacement and adjacent HUD/chat rendering remain correct.

Also test:

- replacement disabled, full-mana hiding, and Creative policy;
- hidden GUI, spectator, unavailable snapshot, disconnect, and reconnect;
- an earlier cancellation and two equal-priority registrations;
- parent `Chat` cancellation coexistence;
- normal and exceptional renderer exit paths;
- dedicated-server startup with public client API classes present but no client linkage;
- the exact published dependency coordinate in a clean workspace without resolving JAWMS from `mavenLocal`, a copied JAWMS JAR, or an unpublished file ID.

API `1.3` in published JAWMS `0.3.0` has source, Java 8, descriptor, old-binary, launched-fixture, direct CurseMaven, and completed-addon coverage. [JAWMS Hud Styles](https://www.curseforge.com/minecraft/mc-mods/jawms-hud-styles) and [JAWMS Classic Bar Integration](https://www.curseforge.com/minecraft/mc-mods/jawms-classic-bar) both consume the public HUD API. Candidate API `1.4` has source/build and compatibility-fixture coverage only until `0.4.0` is independently published and exercised by a real addon.
