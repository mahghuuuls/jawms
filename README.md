# Just Another Wizardry Mana System

Just Another Wizardry Mana System (JAWMS) replaces Electroblob's Wizardry wand mana with a persistent, server-authoritative player mana pool. It adds configurable regeneration and spell costs, reworks Wizardry's mana-related equipment around the new resource, and provides a simple configurable HUD.

JAWMS `1.1.1` preserves addon API `1.6` and builds separately packaged optional Developer Tools. See the [detailed mod page](MOD-PAGE.md) for configuration examples, stat explanations, Wizardry integration behavior, companion addons, and the public API overview. Player-visible release changes are recorded in the [changelog](CHANGELOG.md).

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

JAWMS targets Minecraft 1.12.2 with Forge-compatible loading on Cleanroom. The `1.1.1` compatibility candidate requires:

- Electroblob's Wizardry `4.3.19` or newer
- MixinBooter `10.7` or newer (provided by Cleanroom; a separate mod on standard Forge)

Install JAWMS on both the client and server for multiplayer. Baubles `1.5.2` or newer is optional.

JAWMS uses minimum-or-newer runtime requirements so dependency updates are not rejected solely for being newer. The build and annotation processor remain pinned to MixinBooter `11.8`, independently of the shipping minimum. Candidate qualification on real standalone `10.7`/`11.8` and Cleanroom `0.6.13-alpha`/`0.7.14-alpha` is pending. Earlier release results are not validation of this candidate; an open range does not claim every later version has been tested. Cleanroom resolves this dependency against its built-in reported version; installing a newer standalone JAR does not establish that identity, and no version-alias change is required by this candidate.

## Companion Addons

- [JAWMS Hud Styles](https://www.curseforge.com/minecraft/mc-mods/jawms-hud-styles) replaces the basic mana bar with compact configurable HUD styles.
- [JAWMS Classic Bar Integration](https://www.curseforge.com/minecraft/mc-mods/jawms-classic-bar) displays JAWMS mana through Classic Bar Legacy.
- [JAWMS Additions](https://www.curseforge.com/minecraft/mc-mods/jawms-additions) expands the mana system with new potions, enchantments, and Baubles.
- [JAWMS Integrations](https://www.curseforge.com/minecraft/mc-mods/jawms-integrations) adds optional compatibility between JAWMS and other mods.

## For Addon Developers

Use the canonical [Addon Development Guide](docs/ADDON-DEVELOPMENT.md) for the exact published dependency recipe, release/API matrix, server API, modifier units, client snapshot semantics, replacement-HUD lifecycle, and cache-safe rendering obligations. The separate [Command Reference](docs/COMMANDS.md) documents primary commands, optional Developer Tools, and contributor/API fixtures under their truthful availability tiers.

JAWMS `1.1.1` preserves API `1.6` while retaining the published API `1.2`, `1.3`, `1.4`, and `1.5` binary surfaces. The supported package trees are `com.mahghuuuls.jawms.api` and `com.mahghuuuls.jawms.api.client`; other JAWMS packages are implementation details. Use the guide for current dependency setup and API usage; older releases are documented separately in [API History](docs/API-HISTORY.md).

## License

JAWMS is licensed under the [MIT License](LICENSE). Dependency and attribution notes are listed in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
