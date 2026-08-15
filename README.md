# Just Another Wizardry Mana System

Just Another Wizardry Mana System (JAWMS) replaces Electroblob's Wizardry wand mana with a persistent, server-authoritative player mana pool. It adds configurable regeneration and spell costs, reworks Wizardry's mana-related equipment around the new resource, and provides a simple configurable HUD.

The current published `0.4.0` release is a public beta. It advances the addon API to `1.4` and adds separately packaged optional Developer Tools. See the [detailed mod page](MOD-PAGE.md) for beta status, configuration examples, stat explanations, Wizardry integration behavior, companion addons, and the public API overview. Player-visible release changes are recorded in the [changelog](CHANGELOG.md).

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

JAWMS targets Minecraft 1.12.2 on standard Forge. Its open MixinBooter range also allows Cleanroom `0.6.10-alpha` to satisfy the dependency with its built-in `11.8` identity, without a JAWMS-specific version override. JAWMS requires:

- Electroblob's Wizardry `4.3.19`
- MixinBooter `11.8` or newer

Install JAWMS on both the client and server for multiplayer. Baubles `1.5.2` or newer is optional.

Exact standalone MixinBooter `11.8` and `11.13` passed standard-Forge client/server validation. The open dependency range admits later versions but does not claim they have all been tested.

## Companion Addons

- [JAWMS Hud Styles](https://www.curseforge.com/minecraft/mc-mods/jawms-hud-styles) replaces the basic mana bar with compact configurable HUD styles.
- [JAWMS Classic Bar Integration](https://www.curseforge.com/minecraft/mc-mods/jawms-classic-bar) displays JAWMS mana through Classic Bar Legacy.

## For Addon Developers

Use the canonical [Addon Development Guide](docs/ADDON-DEVELOPMENT.md) for the exact published dependency recipe, release/API matrix, server API, modifier units, client snapshot semantics, replacement-HUD lifecycle, and cache-safe rendering obligations. The separate [Command Reference](docs/COMMANDS.md) documents primary commands, optional Developer Tools, and contributor/API fixtures under their truthful availability tiers.

Published JAWMS `0.4.0` file `8651841` provides additive API `1.4` while retaining the documented API `1.2` binary surface and API `1.3` additions. The supported Java 8 package trees are `com.mahghuuuls.jawms.api` and `com.mahghuuuls.jawms.api.client`; `com.mahghuuuls.jawms.internal` is implementation detail.

## License

JAWMS is licensed under the [MIT License](LICENSE). Dependency and attribution notes are listed in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
