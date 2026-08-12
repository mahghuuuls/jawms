# Changelog

## [0.2.0] - 2026-08-11

### Added

- Additive public API `1.3` client HUD context with known/unknown Creative state, scaled dimensions, and partial ticks.
- Connection-local synchronized snapshot revisions with explicit unknown revision semantics and complete immutable value equality.
- Canonical addon-development guide with the published `0.1.0` dependency recipe, release/API matrix, HUD ownership convention, rendering-state contract, and testing limitations.

### Compatibility

- Retained all documented API `1.2` descriptors and verified an unchanged Java 8 API-`1.2` binary against API `1.3`.
- Direct CurseMaven consumption of API `1.3` remains pending until the release receives a real published file identity; no provisional coordinate is claimed.
- Lowered the MixinBooter requirement to `11.8` and removed its upper bound. Exact standalone `11.8` and `11.13` passed standard-Forge client/server validation; later accepted versions remain untested until separately verified.

### Known Limitations

- JAWMS initialized and reached the main menu in an owner-managed Cleanroom `0.6.10-alpha` modpack using Cleanroom's built-in MixinBooter identity `11.8` without a JAWMS-specific override. An unrelated modpack GUI linkage failure blocked world-entry/cast/HUD validation, so general Cleanroom compatibility is not claimed.

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
