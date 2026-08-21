# Changelog

## [1.1.0] - 2026-08-21

### Added

- Public API `1.6` operation that lets addons start or restart a player's normal mana-regeneration delay without simulating or charging a spell.

### Changed

- Opened Wizardry, MixinBooter, optional Baubles, and Developer Tools runtime requirements to minimum-or-newer ranges while retaining exact pinned development inputs.
- Updated addon documentation for JAWMS `1.1.0` and API `1.6`.

### Compatibility

- Existing addons built for API `1.2` through `1.5` remain supported.
- Open ranges prevent newer dependencies from being rejected solely by metadata; they do not claim every future version has been tested.

## [1.0.0] - 2026-08-20

### Added

- Additive public API `1.5` configured-flask snapshots, directional regeneration-lockout helpers, invariant item-provider previews, and synchronized client preview lookup.
- One final JAWMS tooltip section for configured and opted-in provider contributions after ordinary Forge tooltip subscribers.
- Matching optional JAWMS Developer Tools `1.0.0` artifact with a strict JAWMS `[1.0.0,1.1.0)` dependency.

### Changed

- Clarified whole maximum-mana values, signed mutation deltas, committed external accounting, snapshot lifecycle, provider-preview eligibility, and tooltip-order guarantees in the addon guide.
- Promoted the release from beta to stable after API exercises across JAWMS companion and integration addons.

### Compatibility

- Retained all published API `1.2`, `1.3`, and `1.4` binary surfaces and added a frozen published-`0.4.0` compatibility oracle.
- Continued to require MixinBooter `11.8` or newer without an upper bound.

## [0.4.0] - 2026-08-15

### Added

- Additive public API `1.4` typed mana-mutation outcomes, immutable provider descriptors, immediate and deferred modifier-cache refresh operations, and accepted mana-profile explanations.
- Separate optional JAWMS Developer Tools artifact with permission-level-4 public-API inspection and mutation commands for controlled addon-development environments.
- Canonical command reference covering primary, optional Developer Tools, contributor-fixture, and compatibility-fixture commands.

### Compatibility

- Retained all documented API `1.2` and API `1.3` descriptors and added frozen old-binary compatibility checks for both generations.
- Published additive API `1.4` as CurseForge file `8651841` for direct addon consumption.

## [0.3.0] - 2026-08-12

### Changed

- Added companion-addon links for JAWMS Hud Styles and JAWMS Classic Bar Integration.
- Updated the addon guide with the published `0.2.0` / API `1.3` CurseMaven file and real-addon adoption.

### Compatibility

- Lowered the MixinBooter requirement to `11.8` and removed its upper bound. Exact standalone `11.8` and `11.13` passed standard-Forge client/server validation; later accepted versions remain untested until separately verified.
- Cleanroom `0.6.10-alpha` accepted its built-in MixinBooter identity `11.8` and initialized JAWMS without a version override. In-world Cleanroom behavior remains unverified because an unrelated modpack GUI failure blocked world selection.

## [0.2.0] - 2026-08-11

### Added

- Additive public API `1.3` client HUD context with known/unknown Creative state, scaled dimensions, and partial ticks.
- Connection-local synchronized snapshot revisions with explicit unknown revision semantics and complete immutable value equality.
- Canonical addon-development guide with the published `0.1.0` dependency recipe, release/API matrix, HUD ownership convention, rendering-state contract, and testing limitations.

### Compatibility

- Retained all documented API `1.2` descriptors and verified an unchanged Java 8 API-`1.2` binary against API `1.3`.
- Published API `1.3` as CurseForge file `8621418` for direct addon consumption.

## [0.1.0] - 2026-08-07

### Added

- Persistent player mana as the sole resource for Electroblob's Wizardry wand spells.
- Configurable mana capacity, regeneration, post-cast delay, spell-cost formula, per-spell overrides, and mana flasks.
- Diminishing-return spell efficiency and multiplicative spell-cost modifiers.
- Configurable equipped-item and contextual modifier APIs, authoritative mana operations, state-change events, and tooltip helpers.
- Configurable blue mana HUD plus a read-only client API and cancellable render event for replacement HUDs.
- Optional Baubles equipment support.

### Changed

- Reworked Wizardry armor, Storage, Condenser, Siphon, Melee, mana artefacts, Demonic Seal, and Font of Mana for the player-mana system.
- Removed wand mana from casting decisions and hid obsolete wand-mana displays while preserving stored wand data and non-mana wand behavior.
- Made mana flasks restore player mana first and send remaining restoration to eligible Wizard armor.
