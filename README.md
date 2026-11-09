# Just Another Wizardry Mana System

Just Another Wizardry Mana System (JAWMS) replaces Electroblob's Wizardry wand mana with a persistent, server-authoritative player mana pool. It adds configurable regeneration and spell costs, reworks Wizardry's mana-related equipment around the new resource, and provides a simple configurable HUD.

JAWMS `1.5.0` provides addon API `1.10` and builds optional Developer Tools as a separate JAR. See the [detailed mod page](MOD-PAGE.md) for configuration examples and the [changelog](CHANGELOG.md) for release changes.

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

JAWMS targets Minecraft 1.12.2 with Forge-compatible loading on Cleanroom. JAWMS `1.5.0` requires the following dependencies.

- Electroblob's Wizardry `4.3.19` or newer
- MixinBooter `10.7` or newer (provided by Cleanroom; a separate mod on standard Forge)

Install JAWMS on both the client and server for multiplayer. Baubles `1.5.2` or newer is optional.

The tested Cleanroom baseline is `0.7.14-alpha` with built-in MixinBooter `12.1`. See the addon guide for the separate development dependency pins.

## Companion Addons

- [JAWMS Hud Styles](https://www.curseforge.com/minecraft/mc-mods/jawms-hud-styles) replaces the basic mana bar with compact configurable HUD styles.
- [JAWMS Classic Bar Integration](https://www.curseforge.com/minecraft/mc-mods/jawms-classic-bar) displays JAWMS mana through Classic Bar Legacy.
- [JAWMS Additions](https://www.curseforge.com/minecraft/mc-mods/jawms-additions) expands the mana system with new potions, enchantments, and Baubles.
- [JAWMS Integrations](https://www.curseforge.com/minecraft/mc-mods/jawms-integrations) adds optional compatibility between JAWMS and other mods.

## For Addon Developers

The [Addon Development Guide](docs/ADDON-DEVELOPMENT.md) covers dependency setup, server APIs, modifier providers, and client HUD behavior. The [Command Reference](docs/COMMANDS.md) covers JAWMS, Developer Tools, and contributor fixtures.

JAWMS `1.5.0` retains the earlier API `1.2` through `1.9` binary surfaces. The supported package trees are `com.mahghuuuls.jawms.api` and `com.mahghuuuls.jawms.api.client`. Use the guide for current dependency setup and API usage. Earlier releases are documented in [API History](docs/API-HISTORY.md).

API 1.10 lets a readiness provider register a read-only first-acceptance verifier. JAWMS preserves saved mana while waiting for confirmation of the recorded contribution and keeps the original 100-running-server-tick deadline.

## License

JAWMS is licensed under the [MIT License](LICENSE). Dependency and attribution notes are listed in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
