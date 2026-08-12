JAWMS gives every player one persistent mana pool for Electroblob's Wizardry. Casting draws mana from the player instead of from each wand, mana regenerates over time, Wizardry equipment improves the player's mana capabilities, and a configurable bar shows the current amount.

There are already good mods that add player mana to Wizardry, including [Wizardry Player Mana](https://www.curseforge.com/minecraft/mc-mods/wizardry-player-mana) and [Wizardry Player Mana Tweaked](https://www.curseforge.com/minecraft/mc-mods/wizardryplayermanatweaked). Those solutions work well for their intended scope. JAWMS focuses on a more extensive system for players, modpack creators, and addon developers who want detailed control over the complete mana economy.

<span style="color:#d6a100">**Beta release:**</span> The JAWMS API is still being tested and refined while companion addons are developed.

<span style="color:#d6a100">**AI usage disclaimer:** This mod was developed with AI-agent assistance using [this agent workflow](https://github.com/mahghuuuls/minecraft-1.12.2-mod-agent-workflow). The project owner reviewed the work during development.</span>

## For Players

### What Changes While Playing

- Mana belongs to the player, so changing wands does not give a separate mana pool.
- Spells consume player mana. If there is not enough mana, the spell cannot be cast.
- Mana regenerates naturally, but regeneration normally pauses briefly after casting and while channeling a continuous spell.
- Wizardry armor and robes increase maximum mana and mana regeneration.
- Mana flasks restore player mana, with any configured remainder going to eligible Wizard armor.
- Wizardry upgrades, artefacts, Demonic Seal, and Font of Mana have been adapted to interact with player mana.
- Stored wand mana is preserved for compatibility, but it is ignored for casting and its obsolete displays are hidden.

Wizardry's spell selection, tiers, cooldowns, progression, charge-up behavior, and spell effects remain in place. JAWMS replaces the mana resource rather than replacing Wizardry's complete casting system.

### Mana Bar and Companion Addons

The built-in display is a simple blue mana bar. It can hide while full, show different numeric values, use different anchors and offsets, change size, and fill in any of four directions. It can also be disabled completely.

Players who prefer a different presentation can use a companion addon:

- [JAWMS Hud Styles](https://www.curseforge.com/minecraft/mc-mods/jawms-hud-styles) replaces the basic bar with compact configurable HUD styles beside the hotbar.
- [JAWMS Classic Bar Integration](https://www.curseforge.com/minecraft/mc-mods/jawms-classic-bar) displays JAWMS mana as part of Classic Bar Legacy's HUD stack.

### Requirements

Required:

- Electroblob's Wizardry `4.3.19`
- MixinBooter `11.8` or newer

Optional:

- Baubles `1.5.2+` for equipped Baubles-slot effects

## For Modpack Creators

JAWMS is designed so that its default balance can be replaced without writing another mod. A pack can configure:

- Base maximum mana, mana regeneration, and the delay before regeneration resumes.
- The mana added by each Wizardry armor slot.
- A general spell-cost formula based on the original Wizardry cost and spell tier.
- Exact mana-cost overrides for individual spells.
- The strength of diminishing-return Spell Efficiency.
- Mana flask restoration and Wizard armor overflow behavior.
- Mana properties granted by any registered item while worn, equipped as a Bauble, or held in either hand.
- The player-mana behavior of Wizardry upgrades, artefacts, Demonic Seal, and Font of Mana.
- The local appearance and visibility of the built-in mana bar.
- Which registered addon modifier providers a server permits.

### Configuration Files and Reloading

JAWMS generates three files:

- `config/jawms/jawms.cfg` controls the shared mana economy, spell costs, flasks, Wizardry armor defaults, Spell Efficiency, configured item modifiers, and addon-provider controls.
- `config/jawms/integrations/ebwizardry.cfg` controls Wizardry upgrades, artefacts, Demonic Seal, and Font of Mana.
- `config/jawms/jawms-client.cfg` controls the local HUD.

Valid server-side changes can be applied with `/jawms reload`. If an integration file contains an invalid value, JAWMS keeps the previous valid integration rules instead of partially applying a broken revision. The detailed reference below explains the defaults, formulas, and configured-item rule format.

## For Mod Developers

JAWMS includes a documented Java 8 API. Addons can:

- Read, add, consume, or set authoritative player mana through supported operations.
- Provide equipped-item modifiers for armor, Baubles slots, and either hand.
- Provide player-wide modifiers for enchantments, potion effects, temporary states, and other mechanics.
- Modify maximum mana, mana regeneration, regeneration delay, Spell Efficiency, spell-cost multipliers, and regeneration permissions.
- Observe mana-state transitions and provide synchronized tooltip text.
- Read synchronized client mana state and replace the built-in HUD without depending on internal implementation classes.

JAWMS `0.3.0` provides additive API `1.3` while retaining the documented API `1.2` binary surface. Published JAWMS `0.2.0` file `8621418` provides the same API `1.3` surface for direct CurseMaven development. JAWMS Hud Styles and JAWMS Classic Bar Integration both use the public HUD API.

The canonical [Addon Development Guide](https://github.com/mahghuuuls/jawms/blob/main/docs/ADDON-DEVELOPMENT.md) contains the dependency recipe, release and API matrix, server-side operations, modifier arithmetic, synchronization rules, HUD lifecycle, and rendering-state responsibilities. The supported package trees are `com.mahghuuuls.jawms.api` and `com.mahghuuuls.jawms.api.client`.

## Detailed System Reference

### Mana Stats and Modifier Arithmetic

JAWMS uses these distinct concepts:

- **Mana:** The player's current casting resource.
- **Maximum Mana:** The size of the player's mana pool.
- **Mana Regen:** Mana restored per second when regeneration is allowed.
- **Mana Regen Delay:** How long regeneration is blocked after casting.
- **Spell Efficiency:** Diminishing-return points that reduce spell costs.
- **Spell Cost Multiplier:** An independent global or element-specific factor that can raise or lower spell costs.

Maximum mana, mana regeneration, and regeneration delay all use the same ordering:

```text
max(0, configured base + sum(flat bonuses))
    × (1 + sum(percentage increases) / 100)
    × product(1 - each percentage reduction / 100)
```

Percentage increases add together. Each percentage reduction is a separate remaining-value multiplier. After those factors, maximum mana is rounded down and bounded to at least `1`. Mana regeneration remains non-negative and can accumulate fractional mana internally. Regeneration delay remains non-negative and is rounded upward only when converted to game ticks.

Permissions such as regenerating during the post-cast delay or during continuous casting are separate from the numerical regeneration rate.

### Default Mana and Wizardry Armor

The default values in `config/jawms/jawms.cfg` are:

```properties
mana {
    S:baseMaximum=60
    S:baseRegeneration=2.4
    S:regenerationLockoutSeconds=2.0
}

wizardry_armor_defaults {
    B:enabled=true
    S:helmetMaximum=10
    S:helmetFlatRegeneration=0.4
    S:chestMaximum=10
    S:chestFlatRegeneration=0.4
    S:legsMaximum=10
    S:legsFlatRegeneration=0.4
    S:bootsMaximum=10
    S:bootsFlatRegeneration=0.4
}
```

An unequipped player therefore has `60` maximum mana and `2.4` mana regen. Every equipped Wizardry armor or robe piece adds `10` maximum mana and `0.4` mana regen, producing `100` maximum mana and `4` mana regen with a full set.

### Adding Mana Properties to Existing Items

The `item_modifiers.rules` list can assign mana properties to any registered item without writing an addon. A rule uses this format:

```text
namespace:item[@metadata-or-*]=field:value;field:value
```

The item contributes only while equipped as armor or a Bauble, or held in the main hand or offhand. Items elsewhere in the inventory do nothing. Separate active slots contribute separately. `@*` matches every metadata value; use a number such as `@3` to target one variant. NBT matching is not supported.

For example:

```properties
item_modifiers {
    S:rules <
        minecraft:diamond_chestplate@*=flat_maximum:20;flat_regeneration:0.5
        minecraft:blaze_rod@*=maximum_increase:25;regeneration_increase:50;global_efficiency:20;cost_multiplier_fire:0.75
     >
}
```

The chestplate gives `+20 Mana` and `+0.5 Mana regen` while worn. The held blaze rod gives `+25% Mana`, `+50% Mana regen`, `+20 Spell efficiency`, and `-25% Fire spell mana cost`.

JAWMS automatically adds synchronized values to the matched item's tooltip. Helpful changes appear in blue; penalties such as lower mana, lower regeneration, a longer regeneration delay, or higher spell cost appear in red. Editing the rule and running `/jawms reload` updates both server behavior and connected-client tooltips.

Available fields include:

- `flat_maximum`, `maximum_increase`, and `maximum_reduction`
- `flat_regeneration`, `regeneration_increase`, and `regeneration_reduction`
- `flat_lockout`, `lockout_increase`, and `lockout_reduction`
- `global_efficiency` and element-specific values such as `efficiency_fire`
- `global_cost_multiplier` and element-specific values such as `cost_multiplier_fire`
- `regenerate_during_lockout` and `regenerate_during_continuous`

A configured item rule is authoritative for that item: it replaces the complete built-in Wizardry armor or addon-provided item contribution instead of silently stacking with it.

### Spell Costs

The general spell-cost formula uses each spell's original Wizardry mana cost and tier. Individual spell overrides take precedence when the original balance does not fit the player-mana economy.

The default configuration is:

```properties
spells {
    S:formula=min(original_cost, tier * 25)

    S:overrides <
     >
}
```

`original_cost` is the cost registered by Wizardry before JAWMS changes it. `tier` is `1` for novice, `2` for apprentice, `3` for advanced, and `4` for master. The default formula keeps inexpensive spells at their original cost while capping the configured base cost by tier at `25`, `50`, `75`, or `100` mana.

The expression language supports numbers, both variables, parentheses, and these operations:

- Addition and multiplication: `original_cost + tier * 5`
- Subtraction and division: `(original_cost - 2) / tier`
- Remainder and exponentiation: `(tier ^ 2) % 5`
- Minimum and maximum: `min(original_cost, tier * 25)` and `max(1, original_cost)`
- Absolute value: `abs(original_cost - 50)`
- Rounding down or up: `floor(original_cost / 3)` and `ceil(original_cost / 3)`
- Nearest whole number: `round(original_cost / 3)`

Overrides use a full spell registry ID and a non-negative whole-number cost:

```properties
spells {
    S:formula=min(original_cost, tier * 25)

    S:overrides <
        ebwizardry:black_hole=120
        ebwizardry:magic_missile=4
     >
}
```

An override always wins over the formula. Invalid formulas or results fall back safely to the affected spell's original cost, while an unknown override is ignored with a warning.

Spell Efficiency is not a percentage. With configured scale `K`, its cost factor is:

```text
K / (K + applicable efficiency)
```

With the default scale of `100`, `100` efficiency makes spells cost half as much, `200` makes them cost one third, and `300` makes them cost one quarter. Global and matching-element cost multipliers then multiply independently with the efficiency factor and Wizardry's retained contextual cost factor before the final cost is rounded upward.

Continuous spells consume their configured per-second cost in half-second portions. If the next portion cannot be paid, casting stops without making mana negative.

### Electroblob's Wizardry Integration

Wizardry armor and robes give flat maximum mana and mana regeneration by default, so a prepared wizard benefits more from later percentage bonuses. Their original spell-cost reductions are replaced by school-matching Spell Efficiency.

JAWMS gives configurable player-mana behavior to Wizardry mechanics that previously depended on wand mana:

- **Storage:** increases maximum mana while an upgraded wand is held.
- **Condenser:** increases mana regeneration while an upgraded wand is held.
- **Siphon:** restores mana after qualifying kills.
- **Melee:** spends player mana to enhance successful wand melee attacks.
- **Ring of Condensing:** adds mana regeneration, with multiple active rings stacking.
- **Ring of Siphoning:** improves Siphon's mana restoration.
- **Ring of Extraction:** restores mana after attributable Wizardry sorcery kills.
- **Perfectionist:** refunds the player mana paid for a Force Arrow that misses.
- **Demonic Seal:** can cover a mana shortfall with hunger while retaining a configurable minimum hunger level.
- **Font of Mana:** grants mana regeneration and can permit regeneration during the post-cast delay.

Each replacement can be enabled, disabled, and tuned independently. Disabling one never brings obsolete wand-mana behavior back. Unrelated Wizardry behavior, such as Font cooldown reduction and Wizard armor's separate defensive mana, remains intact.

Mana flasks restore player mana first, then send any remaining configurable restoration to eligible Wizard armor. They never refill wand mana, and their tooltips show the configured amount and behavior.

### Built-in HUD Reference

The built-in HUD can:

- Be enabled or disabled.
- Hide while mana is full.
- Show no number, current mana, maximum mana, or both values.
- Change text scale, bar length, and thickness.
- Use any of nine screen anchors with positive or negative offsets.
- Fill left-to-right, right-to-left, bottom-to-top, or top-to-bottom.

Changes apply without restarting Minecraft. The bar renders below chat so messages remain readable. Addons can claim only the built-in rendering callback and draw a completely different HUD while continuing to receive synchronized mana data.

Source code: [github.com/mahghuuuls/jawms](https://github.com/mahghuuuls/jawms)
