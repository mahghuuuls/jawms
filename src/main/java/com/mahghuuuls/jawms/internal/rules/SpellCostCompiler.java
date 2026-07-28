package com.mahghuuuls.jawms.internal.rules;

import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class SpellCostCompiler {

    private SpellCostCompiler() {
    }

    public static Map<ResourceLocation, Double> compile(
            String formula,
            Map<ResourceLocation, Integer> overrides,
            List<SpellDefinition> spells,
            Consumer<String> warning
    ) {
        ExpressionCompiler.CompiledExpression expression = null;
        try {
            expression = ExpressionCompiler.compile(formula);
        } catch (ExpressionCompiler.FormulaException exception) {
            warning.accept("Invalid spells.formula; every affected spell uses original cost: "
                    + exception.getMessage());
        }

        Map<ResourceLocation, SpellDefinition> definitions = new LinkedHashMap<>();
        for (SpellDefinition spell : spells) {
            definitions.put(spell.getId(), spell);
        }
        for (ResourceLocation overrideId : overrides.keySet()) {
            if (!definitions.containsKey(overrideId)) {
                warning.accept("Ignored unknown spells.override entry '" + overrideId + "'.");
            }
        }

        Map<ResourceLocation, Double> costs = new LinkedHashMap<>();
        for (SpellDefinition spell : spells) {
            Integer override = overrides.get(spell.getId());
            if (override != null) {
                costs.put(spell.getId(), override.doubleValue());
                continue;
            }

            double configured = spell.getOriginalCost();
            if (expression != null) {
                configured = expression.evaluate(spell.getOriginalCost(), spell.getTier());
                if (!isSupportedCost(configured)) {
                    warning.accept(
                            "Formula result for spell '" + spell.getId()
                                    + "' was unsupported; using original cost " + spell.getOriginalCost() + "."
                    );
                    configured = spell.getOriginalCost();
                }
            }
            costs.put(spell.getId(), configured);
        }
        return Collections.unmodifiableMap(costs);
    }

    static boolean isSupportedCost(double value) {
        return Double.isFinite(value) && value >= 0.0D && value <= Integer.MAX_VALUE;
    }

    public static final class SpellDefinition {

        private final ResourceLocation id;
        private final int originalCost;
        private final int tier;

        public SpellDefinition(ResourceLocation id, int originalCost, int tier) {
            if (id == null) {
                throw new NullPointerException("id");
            }
            if (originalCost < 0) {
                throw new IllegalArgumentException("Original cost must be non-negative.");
            }
            if (tier < 1 || tier > 4) {
                throw new IllegalArgumentException("Tier must be 1 through 4.");
            }
            this.id = id;
            this.originalCost = originalCost;
            this.tier = tier;
        }

        public ResourceLocation getId() {
            return id;
        }

        public int getOriginalCost() {
            return originalCost;
        }

        public int getTier() {
            return tier;
        }
    }
}
