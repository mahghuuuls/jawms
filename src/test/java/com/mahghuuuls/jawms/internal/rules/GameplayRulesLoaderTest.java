package com.mahghuuuls.jawms.internal.rules;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameplayRulesLoaderTest {

    @BeforeAll
    static void initializeForgeConfigurationBasePath() throws ReflectiveOperationException {
        Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, new File(".").getAbsoluteFile());
    }

    @TempDir
    Path temporaryDirectory;

    @Test
    void freshConfigurationResolvesApprovedDefaultsAndCreatesFile() {
        File file = temporaryDirectory.resolve("jawms.cfg").toFile();

        GameplayRules rules = GameplayRulesLoader.load(
                file,
                GameplayRules.initialDefaults(),
                1L,
                spells(),
                ignored -> {
                }
        );

        assertTrue(file.isFile());
        assertEquals(50, rules.getBaseMaximum());
        assertEquals(2.0D, rules.getBaseRegeneration());
        assertEquals(40L, rules.getRegenerationLockoutTicks());
        assertEquals(20, rules.getSmallFlask());
        assertEquals(50, rules.getMediumFlask());
        assertEquals(100, rules.getLargeFlask());
        assertEquals(10.0D, rules.getSpellCosts().get(id("novice")));
        assertEquals(75.0D, rules.getSpellCosts().get(id("advanced")));
        assertEquals(100.0D, rules.getSpellCosts().get(id("master")));
    }

    @Test
    void invalidIndependentEntriesKeepPreviousValuesWhileValidEntriesPublish() {
        File file = temporaryDirectory.resolve("jawms.cfg").toFile();
        GameplayRulesLoader.load(
                file,
                GameplayRules.initialDefaults(),
                1L,
                spells(),
                ignored -> {
                }
        );
        Configuration configuration = new Configuration(file);
        configuration.load();
        configuration.get("mana", "baseMaximum", "50").set("-4");
        configuration.get("mana", "baseRegeneration", "2.0").set("NaN");
        configuration.get("mana", "regenerationLockoutSeconds", "2.0").set("0.01");
        configuration.get("spells", "formula", GameplayRules.DEFAULT_COST_FORMULA).set("original_cost +");
        configuration.get("spells", "overrides", new String[0]).set(new String[]{
                "jawms_test:master=7",
                "missing_mod:missing_spell=12",
                "master=9",
                "bad entry"
        });
        configuration.get("flasks", "small", "20").set("25");
        configuration.get("item_modifiers", "rules", new String[0]).set(new String[]{
                "missing_mod:missing_item=1,0.5,50"
        });
        configuration.save();

        List<String> warnings = new ArrayList<>();
        GameplayRules rules = GameplayRulesLoader.load(
                file,
                GameplayRules.initialDefaults(),
                2L,
                spells(),
                warnings::add
        );

        assertEquals(50, rules.getBaseMaximum());
        assertEquals(2.0D, rules.getBaseRegeneration());
        assertEquals(1L, rules.getRegenerationLockoutTicks());
        assertEquals(25, rules.getSmallFlask());
        assertEquals(10.0D, rules.getSpellCosts().get(id("novice")));
        assertEquals(7.0D, rules.getSpellCosts().get(id("master")));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("mana.baseMaximum")));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("mana.baseRegeneration")));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("spells.formula")));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("missing_mod:missing_spell")));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("item_modifiers.rules")));
    }

    @Test
    void invalidArmorProfileEnabledValueKeepsPreviousDisabledStateAndWarns() throws IOException {
        File file = temporaryDirectory.resolve("jawms.cfg").toFile();
        GameplayRules defaults = GameplayRulesLoader.load(
                file,
                GameplayRules.initialDefaults(),
                1L,
                spells(),
                ignored -> {
                }
        );

        Configuration configuration = new Configuration(file);
        configuration.load();
        configuration.get("wizardry_armor_defaults", "enabled", true).set(false);
        configuration.save();
        GameplayRules disabled = GameplayRulesLoader.load(
                file,
                defaults,
                2L,
                spells(),
                ignored -> {
                }
        );
        assertTrue(!disabled.getArmorProfile().isEnabled());

        String configText = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(configText.contains("B:enabled=false"));
        Files.write(
                file.toPath(),
                configText.replace("B:enabled=false", "B:enabled=maybe").getBytes(StandardCharsets.UTF_8)
        );

        List<String> warnings = new ArrayList<>();
        GameplayRules reloaded = GameplayRulesLoader.load(
                file,
                disabled,
                3L,
                spells(),
                warnings::add
        );

        assertTrue(!reloaded.getArmorProfile().isEnabled());
        assertTrue(warnings.stream().anyMatch(value -> value.contains("wizardry_armor_defaults.enabled")
                && value.contains("keeping false")));
    }

    @Test
    void completeRulesMigrateLegacyPercentagesAndRejectMixedShapes() {
        com.mahghuuuls.jawms.api.ManaContribution parsed =
                GameplayRulesLoader.parseContribution(
                        "flat_maximum:10;maximum_percent:50;flat_regeneration:2;"
                                + "regeneration_percent:-50;flat_lockout:1;"
                                + "lockout_increase:50;lockout_reduction:50;"
                                + "global_efficiency:5;efficiency_fire:20;"
                                + "regenerate_during_lockout:true"
                );
        assertEquals(10, parsed.getFlatMaximumMana());
        assertEquals(50.0D, parsed.getMaximumManaIncrease());
        assertEquals(50.0D, parsed.getRegenerationReduction());
        assertEquals(20.0D, parsed.getElementSpellEfficiency(
                electroblob.wizardry.constants.Element.FIRE
        ));
        assertTrue(parsed.canRegenerateDuringPostCastLockout());
        assertFalse(parsed.canRegenerateDuringContinuousCasting());

        File file = temporaryDirectory.resolve("jawms.cfg").toFile();
        GameplayRulesLoader.load(
                file,
                GameplayRules.initialDefaults(),
                1L,
                spells(),
                ignored -> {
                }
        );
        Configuration configuration = new Configuration(file);
        configuration.load();
        configuration.get("item_modifiers", "rules", new String[0]).set(new String[]{
                "minecraft:stick@*=flat_maximum:10;maximum_percent:50;"
                        + "flat_regeneration:2;regeneration_percent:-50;"
                        + "flat_lockout:1;lockout_increase:50;lockout_reduction:50;"
                        + "global_efficiency:5;efficiency_fire:20;"
                        + "regenerate_during_lockout:true",
                "minecraft:blaze_rod@*=maximum_percent:50;maximum_increase:10"
        });
        configuration.save();

        List<String> warnings = new ArrayList<>();
        GameplayRules rules = GameplayRulesLoader.load(
                file,
                GameplayRules.initialDefaults(),
                2L,
                spells(),
                warnings::add
        );

        assertEquals(0, rules.getConfiguredItemRules().size());
        assertThrows(
                IllegalArgumentException.class,
                () -> GameplayRulesLoader.parseContribution(
                        "maximum_percent:50;maximum_increase:10"
                )
        );
        assertTrue(warnings.stream().anyMatch(value -> value.contains("unknown item")));
    }

    private static List<SpellCostCompiler.SpellDefinition> spells() {
        return Arrays.asList(
                new SpellCostCompiler.SpellDefinition(id("novice"), 10, 1),
                new SpellCostCompiler.SpellDefinition(id("advanced"), 100, 3),
                new SpellCostCompiler.SpellDefinition(id("master"), 175, 4)
        );
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("jawms_test", path);
    }
}
