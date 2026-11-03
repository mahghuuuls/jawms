# Just Another Wizardry Mana System

Just Another Wizardry Mana System (JAWMS) replaces Electroblob's Wizardry wand mana with a persistent, server-authoritative player mana pool. It adds configurable regeneration and spell costs, reworks Wizardry's mana-related equipment around the new resource, and provides a simple configurable HUD.

The JAWMS `1.2.0` development candidate provides addon API `1.7` and builds separately packaged optional Developer Tools. See the [detailed mod page](MOD-PAGE.md) for configuration examples, stat explanations, Wizardry integration behavior, companion addons, and the public API overview. Player-visible release changes are recorded in the [changelog](CHANGELOG.md).

## Features

- Player mana is the sole mana resource for Wizardry wand spells; stored wand mana is preserved but ignored and hidden.
- Spell costs support a formula based on Wizardry's original cost and spell tier, individual spell overrides, diminishing-return spell efficiency, and multiplicative cost modifiers.
- Maximum mana, mana regeneration, post-cast regeneration delay, flasks, Wizardry armor, and equipped-item modifiers are configurable.
- Storage, Condenser, Siphon, Melee, four mana artefacts, Demonic Seal, and Font of Mana have configurable player-mana replacements.
- The built-in blue HUD supports visibility, text, scale, screen anchor, offsets, dimensions, and fill direction. It can be disabled for an addon-provided replacement.
- The public API supports authoritative mana operations, item and contextual modifier providers, state-change events, tooltip helpers, and a read-only client HUD/replacement view.
- Baubles equipment is supported when Baubles or a compatible provider is installed.

## Configuration

JAWMS generates three configuration files:

- `config/jawms/jawms.cfg`: server-controlled mana, spell-cost, flask, armor, efficiency, and configured-item rules.
- `config/jawms/integrations/ebwizardry.cfg`: Electroblob's Wizardry upgrade, artefact, Demonic Seal, and Font of Mana replacements.
- `config/jawms/jawms-client.cfg`: client-local HUD settings.

Server operators can apply valid gameplay and integration changes with `/jawms reload`. Invalid integration revisions are rejected together so the previous valid behavior remains active.

## Requirements

JAWMS targets Minecraft 1.12.2 with Forge-compatible loading on Cleanroom. JAWMS `1.2.0` requires:

- Electroblob's Wizardry `4.3.19` or newer
- MixinBooter `10.7` or newer (provided by Cleanroom; a separate mod on standard Forge)

Install JAWMS on both the client and server for multiplayer. Baubles `1.5.2` or newer is optional.

The packaged 1.2.0 candidate was tested on Cleanroom `0.7.14-alpha` with its built-in MixinBooter `10.7` identity. See the addon guide for the separate development dependency pins.

## Companion Addons

- [JAWMS Hud Styles](https://www.curseforge.com/minecraft/mc-mods/jawms-hud-styles) replaces the basic mana bar with compact configurable HUD styles.
- [JAWMS Classic Bar Integration](https://www.curseforge.com/minecraft/mc-mods/jawms-classic-bar) displays JAWMS mana through Classic Bar Legacy.
- [JAWMS Additions](https://www.curseforge.com/minecraft/mc-mods/jawms-additions) expands the mana system with new potions, enchantments, and Baubles.
- [JAWMS Integrations](https://www.curseforge.com/minecraft/mc-mods/jawms-integrations) adds optional compatibility between JAWMS and other mods.

## For Addon Developers

Use the canonical [Addon Development Guide](docs/ADDON-DEVELOPMENT.md) for the dependency recipe, release/API matrix, server API, modifier units, client snapshot semantics, replacement-HUD lifecycle, and cache-safe rendering obligations. The separate [Command Reference](docs/COMMANDS.md) documents primary commands, optional Developer Tools, and contributor/API fixtures under their truthful availability tiers.

JAWMS `1.2.0` provides API `1.7` and retains the earlier API `1.2` through `1.6` binary surfaces. The supported package trees are `com.mahghuuuls.jawms.api` and `com.mahghuuuls.jawms.api.client`; other JAWMS packages are implementation details. Use the guide for current dependency setup and API usage; older releases are documented separately in [API History](docs/API-HISTORY.md).

API 1.7 lets addons register an additional cast-cost participant. A participant can reserve its own resource, refuse a cast before the spell takes effect, and settle after a successful effect. A continuous spell can stop after its last effect and the permitted payment. Operators can enable detailed casting diagnostics with the server JVM option `-Djawms.debugCasting=true`.

## License

JAWMS is licensed under the [MIT License](LICENSE). Dependency and attribution notes are listed in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
