# Just Another Wizardry Mana System

Just Another Wizardry Mana System (JAWMS) replaces Electroblob's Wizardry wand mana with a persistent, server-authoritative player mana pool. It adds configurable regeneration and spell costs, reworks Wizardry's mana-related equipment around the new resource, and provides a simple configurable HUD.

The current `0.1.0` release is a public beta. See the [detailed mod page](MOD-PAGE.md) for its beta status, configuration examples, stat explanations, Wizardry integration behavior, addon API overview, and current Cleanroom setup note. Player-visible release changes are recorded in the [changelog](CHANGELOG.md).

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

JAWMS targets Minecraft 1.12.2 on Forge and Cleanroom. It requires:

- Electroblob's Wizardry `4.3.19`
- MixinBooter `11.11` or newer in the `11.x` series

Install JAWMS on both the client and server for multiplayer. Baubles `1.5.2` or newer is optional.

Current Cleanroom releases may require the temporary MixinBooter version override documented at the top of [MOD-PAGE.md](MOD-PAGE.md).

## For Addon Developers

Use the canonical [Addon Development Guide](docs/ADDON-DEVELOPMENT.md) for the exact published dependency recipe, release/API matrix, server API, modifier units, client snapshot semantics, replacement-HUD lifecycle, and cache-safe rendering obligations.

Published JAWMS `0.1.0` exposes API `1.2`. The current development candidate exposes additive API `1.3`, but it has no published CurseMaven file identity yet. The supported Java 8 package trees are `com.mahghuuuls.jawms.api` and `com.mahghuuuls.jawms.api.client`; `com.mahghuuuls.jawms.internal` is implementation detail.

## License

JAWMS is licensed under the [MIT License](LICENSE). Dependency and attribution notes are listed in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
