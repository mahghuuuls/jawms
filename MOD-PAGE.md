# Just Another Wizardry Mana System

> **Beta release:** JAWMS has received extensive automated and in-game testing, but its broad configuration and integration surface still needs wider use across real modpacks. Public API `1.2` has compatibility coverage through separately compiled fixtures, but it has not yet been exercised by an independently developed addon.

> **Temporary Cleanroom note — August 7, 2026:** Cleanroom `0.6.8-alpha` reports its built-in MixinBooter as `11.8`, while JAWMS requires `11.11`. In `config/forge_early.cfg`, use:
>
> ```properties
> B:CUSTOM_BUILT_IN_MOD_VERSION=true
> S:MIXIN_BOOTER_VERSION=11.11
> ```
>
> This temporary override has been tested successfully and should become unnecessary when Cleanroom updates its reported MixinBooter version.

There are already good mods that give Electroblob's Wizardry a player mana pool, including [Wizardry Player Mana](https://www.curseforge.com/minecraft/mc-mods/wizardry-player-mana) and [Wizardry Player Mana Tweaked](https://www.curseforge.com/minecraft/mc-mods/wizardryplayermanatweaked). Those solutions work well for their intended scope. JAWMS is for players, modpack authors, and addon developers who want a more extensive and configurable mana system instead of only replacing where mana is stored.

JAWMS completely replaces wand mana for player-cast Wizardry spells with one persistent player mana pool. Stored wand mana is preserved, but it is ignored for casting and its obsolete displays are hidden, while Wizardry's spell selection, tiers, cooldowns, progression, charge-up behavior, and spell effects remain in place. Its intentionally opinionated defaults follow the author's preference for smaller mana numbers, relatively quick regeneration, a short punishment window after casting, and equipment that makes a prepared wizard meaningfully stronger. They are a starting point rather than a universal balance prescription: server and modpack owners can tune the mana economy, spell costs, equipment, flasks, and Wizardry replacements to fit a different playstyle.

<p style="color:#d6a100"><strong>AI usage disclaimer:</strong> This mod was developed with AI-agent assistance using <a href="https://github.com/mahghuuuls/minecraft-1.12.2-mod-agent-workflow">this agent workflow</a>. The project owner reviewed the work during development.</p>

## The Mana System

JAWMS provides several distinct stats that equipment, configuration, and addons can change:

| Stat | What it does |
| --- | --- |
| Mana | The player's current casting resource. |
| Maximum Mana | Sets the size of the player mana pool. |
| Mana Regen | Restores mana over time when regeneration is allowed. |
| Mana Regen Delay | Controls how long regeneration is blocked after casting. |
| Spell Efficiency | Reduces spell costs with diminishing returns instead of allowing additive reductions to approach free casting. |
| Spell Cost Multiplier | Independently raises or lowers final spell costs and can apply globally or to one Wizardry element. |

Maximum mana, mana regeneration, and regeneration delay support flat changes, percentage increases, and independent percentage reductions. Addons can also allow regeneration during the post-cast delay or during continuous casting, making room for equipment and effects with meaningful advantages and drawbacks.

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

An unequipped player therefore has `60` maximum mana and `2.4` mana regen. Every equipped Wizardry armor or robe piece adds `10` maximum mana and `0.4` mana regen, producing `100` maximum mana and `4` mana regen with a full set. Change the base or individual armor-slot values to rebalance this progression.

### Adding Mana Properties to Existing Items

The `item_modifiers.rules` list can assign mana properties to any registered item without writing an addon. A rule uses this format:

```text
namespace:item[@metadata-or-*]=field:value;field:value
```

The item only contributes while equipped as armor or a Bauble, or held in the main hand or offhand. Items sitting elsewhere in the inventory do nothing. Separate active slots contribute separately. `@*` matches every metadata value; use a number such as `@3` to target one variant. NBT matching is not supported.

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

JAWMS automatically adds those synchronized values to the matched item's tooltip. Helpful changes appear in blue; penalties such as lower mana, lower regeneration, a longer regeneration delay, or higher spell cost appear in red. Editing the rule and running `/jawms reload` updates both the server behavior and connected-client tooltip.

Available fields include:

- `flat_maximum`, `maximum_increase`, and `maximum_reduction`
- `flat_regeneration`, `regeneration_increase`, and `regeneration_reduction`
- `flat_lockout`, `lockout_increase`, and `lockout_reduction`
- `global_efficiency` and element-specific values such as `efficiency_fire`
- `global_cost_multiplier` and element-specific values such as `cost_multiplier_fire`
- `regenerate_during_lockout` and `regenerate_during_continuous`

A configured item rule is authoritative for that item: it replaces the complete built-in Wizardry armor or addon-provided item contribution instead of silently stacking with it.

## Configurable Spell Costs

Server and modpack owners can define a general spell-cost formula using each spell's original Wizardry mana cost and spell tier. Individual spells can override that formula when their original balance does not fit the player-mana economy.

The default configuration is:

```properties
spells {
    S:formula=min(original_cost, tier * 25)

    S:overrides <
     >
}
```

`original_cost` is the mana cost registered by Wizardry before JAWMS changes it. `tier` is `1` for novice, `2` for apprentice, `3` for advanced, and `4` for master. The default formula keeps inexpensive spells at their original cost while capping the configured base cost by tier at `25`, `50`, `75`, or `100` mana.

The expression language supports numbers, both variables, and parentheses. Its operators and functions can be combined freely:

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

An override always wins over the formula for that spell. Invalid formulas or results fall back safely to the affected spell's original cost, while an unknown override is ignored with a warning.

Spell efficiency is separate from direct cost multipliers. Its cost factor is `scale / (scale + efficiency)`. With the default scale of `100`, `100` efficiency makes spells cost half as much, `200` makes them cost one third, and `300` makes them cost one quarter before other multipliers and final rounding. Adding the same amount of efficiency therefore adds a consistent amount of casting capacity without allowing ordinary additive percentages to reach a free spell. Cost multipliers give addons another tool for effects such as halving spell cost or increasing spell cost in exchange for more damage.

Continuous spells consume their configured per-second cost in half-second portions. If the next portion cannot be paid, casting stops without making mana negative.

## Electroblob's Wizardry Integration

Wizardry armor and robes give flat maximum mana and mana regeneration by default, so a prepared wizard benefits more from later percentage bonuses. Their original spell-cost reductions are replaced by school-matching spell efficiency.

JAWMS also gives configurable player-mana behavior to Wizardry mechanics that previously depended on wand mana:

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

Mana flasks are also integrated with the new system. They restore player mana first, then send any remaining configurable restoration to eligible Wizard armor. They never refill wand mana, and their tooltips show the configured amount and behavior.

## A Basic but Flexible HUD

The built-in HUD is intentionally a simple blue mana bar rather than a heavily styled interface. It can still be configured extensively:

- Enable or disable the built-in bar.
- Hide it while mana is full.
- Show no number, current mana, maximum mana, or both values.
- Change text scale, bar length, and thickness.
- Select any of nine screen anchors and apply positive or negative offsets.
- Fill left-to-right, right-to-left, bottom-to-top, or top-to-bottom.

Changes apply without restarting Minecraft. The bar renders below chat so messages remain readable. Addons can disable or cancel only the built-in rendering and draw a completely different HUD while continuing to receive synchronized mana data.

The API allows addon developers to replace this basic HUD with a more stylized interface.

## Configuration

JAWMS generates three files:

- `config/jawms/jawms.cfg` controls the shared mana economy, spell costs, flasks, Wizardry armor defaults, spell efficiency, configured item modifiers, and addon-provider controls.
- `config/jawms/integrations/ebwizardry.cfg` controls the Wizardry upgrades, artefacts, Demonic Seal, and Font of Mana replacements.
- `config/jawms/jawms-client.cfg` controls the local HUD.

Valid server-side changes can be applied with `/jawms reload`. If an integration file contains an invalid value, JAWMS keeps the previous valid integration rules instead of partially applying a broken configuration.

## Addon API

JAWMS includes a Java 8 API for addon mods. Addons can:

- Read, add, consume, or set authoritative player mana through supported operations.
- Provide equipped-item modifiers for armor, Baubles slots, and either hand.
- Provide player-wide contextual modifiers for enchantments, potion effects, temporary states, or other addon mechanics.
- Modify maximum mana, mana regeneration, regeneration delay, spell efficiency, spell-cost multipliers, and regeneration permissions.
- Observe mana-state changes and provide synchronized tooltip text.
- Read synchronized client mana state and replace the built-in HUD without depending on internal implementation classes.

The supported API is version `1.2` under `com.mahghuuuls.jawms.api` and its documented client subpackage.

## Requirements

JAWMS targets Minecraft `1.12.2` on Forge and Cleanroom and must be installed on both the client and server for multiplayer. Current Cleanroom releases may require the temporary setup noted at the top of this page.

Required:

- Electroblob's Wizardry `4.3.19`
- MixinBooter `11.11` or another compatible `11.x` release

Optional:

- Baubles `1.5.2+` for equipped Baubles-slot effects

Source code: [github.com/mahghuuuls/jawms](https://github.com/mahghuuuls/jawms)
