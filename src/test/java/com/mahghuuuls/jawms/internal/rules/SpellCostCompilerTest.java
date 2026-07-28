package com.mahghuuuls.jawms.internal.rules;

import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellCostCompilerTest {

    @Test
    void defaultFormulaPreservesSmallCostsAndCapsHigherTiers() {
        List<SpellCostCompiler.SpellDefinition> spells = Arrays.asList(
                spell("novice", 10, 1),
                spell("advanced", 100, 3),
                spell("master", 175, 4)
        );

        Map<ResourceLocation, Double> costs = SpellCostCompiler.compile(
                GameplayRules.DEFAULT_COST_FORMULA,
                Collections.emptyMap(),
                spells,
                ignored -> {
                }
        );

        assertEquals(10.0D, costs.get(id("novice")));
        assertEquals(75.0D, costs.get(id("advanced")));
        assertEquals(100.0D, costs.get(id("master")));
    }

    @Test
    void exactOverrideWinsAndZeroIsValid() {
        Map<ResourceLocation, Integer> overrides = new LinkedHashMap<>();
        overrides.put(id("master"), 0);

        Map<ResourceLocation, Double> costs = SpellCostCompiler.compile(
                "original_cost + tier",
                overrides,
                Collections.singletonList(spell("master", 175, 4)),
                ignored -> {
                }
        );

        assertEquals(0.0D, costs.get(id("master")));
    }

    @Test
    void syntaxAndPerSpellNumericFailuresFallBackToOriginalCost() {
        List<String> warnings = new ArrayList<>();
        List<SpellCostCompiler.SpellDefinition> spells = Arrays.asList(
                spell("zero", 0, 1),
                spell("ten", 10, 1)
        );

        Map<ResourceLocation, Double> syntaxFallback = SpellCostCompiler.compile(
                "original_cost +",
                Collections.emptyMap(),
                spells,
                warnings::add
        );
        assertEquals(0.0D, syntaxFallback.get(id("zero")));
        assertEquals(10.0D, syntaxFallback.get(id("ten")));

        warnings.clear();
        Map<ResourceLocation, Double> perSpellFallback = SpellCostCompiler.compile(
                "10 / original_cost",
                Collections.emptyMap(),
                spells,
                warnings::add
        );
        assertEquals(0.0D, perSpellFallback.get(id("zero")));
        assertEquals(1.0D, perSpellFallback.get(id("ten")));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("jawms_test:zero")));
    }

    private static SpellCostCompiler.SpellDefinition spell(String path, int cost, int tier) {
        return new SpellCostCompiler.SpellDefinition(id(path), cost, tier);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("jawms_test", path);
    }
}
