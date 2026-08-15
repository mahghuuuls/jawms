package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import electroblob.wizardry.constants.Element;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ManaProfileResolver {

    private ManaProfileResolver() {
    }

    public static ResolvedManaProfile resolve(
            int baseMaximum,
            double baseRegeneration,
            double baseLockoutSeconds,
            List<ManaContribution> contributions
    ) {
        return calculate(
                baseMaximum, baseRegeneration, baseLockoutSeconds, contributions
        ).getProfile();
    }

    static ManaProfileCalculation calculate(
            int baseMaximum,
            double baseRegeneration,
            double baseLockoutSeconds,
            List<ManaContribution> contributions
    ) {
        if (baseMaximum < 1) {
            throw new IllegalArgumentException("Base maximum must be positive.");
        }
        requireNonNegativeFinite(baseRegeneration, "baseRegeneration");
        requireNonNegativeFinite(baseLockoutSeconds, "baseLockoutSeconds");
        if (contributions == null) {
            throw new NullPointerException("contributions");
        }

        double maximumFlat = 0.0D;
        double maximumRunning = baseMaximum;
        double maximumIncrease = 0.0D;
        double maximumReductionFactor = 1.0D;
        double regenerationFlat = 0.0D;
        double regenerationRunning = baseRegeneration;
        double regenerationIncrease = 0.0D;
        double regenerationReductionFactor = 1.0D;
        double lockoutFlat = 0.0D;
        double lockoutRunning = baseLockoutSeconds;
        double lockoutIncrease = 0.0D;
        double lockoutReductionFactor = 1.0D;
        double globalEfficiency = 0.0D;
        EnumMap<Element, Double> elementEfficiency = new EnumMap<>(Element.class);
        BigDecimal globalSpellCostMultiplier = BigDecimal.ONE;
        EnumMap<Element, BigDecimal> elementSpellCostMultiplier = new EnumMap<>(Element.class);
        boolean postCastPermission = false;
        boolean continuousPermission = false;

        for (ManaContribution contribution : contributions) {
            if (contribution == null) {
                throw new IllegalArgumentException("Contribution list contains null.");
            }
            validateContribution(contribution);
            maximumFlat = saturatedAdd(maximumFlat, contribution.getFlatMaximumMana());
            maximumRunning = saturatedAdd(
                    maximumRunning, contribution.getFlatMaximumMana()
            );
            maximumIncrease = saturatedAdd(
                    maximumIncrease,
                    contribution.getMaximumManaIncrease()
            );
            maximumReductionFactor *= remainingFactor(
                    contribution.getMaximumManaReduction()
            );
            regenerationFlat = saturatedAdd(
                    regenerationFlat,
                    contribution.getFlatRegeneration()
            );
            regenerationRunning = saturatedAdd(
                    regenerationRunning,
                    contribution.getFlatRegeneration()
            );
            regenerationIncrease = saturatedAdd(
                    regenerationIncrease,
                    contribution.getRegenerationIncrease()
            );
            regenerationReductionFactor *= remainingFactor(
                    contribution.getRegenerationReduction()
            );
            lockoutFlat = saturatedAdd(
                    lockoutFlat,
                    contribution.getFlatLockoutSeconds()
            );
            lockoutRunning = saturatedAdd(
                    lockoutRunning,
                    contribution.getFlatLockoutSeconds()
            );
            lockoutIncrease = saturatedAdd(
                    lockoutIncrease,
                    contribution.getLockoutIncrease()
            );
            lockoutReductionFactor *= remainingFactor(
                    contribution.getLockoutReduction()
            );
            globalEfficiency = saturatedAdd(
                    globalEfficiency,
                    contribution.getGlobalSpellEfficiency()
            );
            for (Map.Entry<Element, Double> entry
                    : contribution.getElementSpellEfficiency().entrySet()) {
                elementEfficiency.put(
                        entry.getKey(),
                        saturatedAdd(
                                elementEfficiency.getOrDefault(entry.getKey(), 0.0D),
                                entry.getValue()
                        )
                );
            }
            globalSpellCostMultiplier = globalSpellCostMultiplier.multiply(
                    BigDecimal.valueOf(contribution.getGlobalSpellCostMultiplier())
            );
            for (Map.Entry<Element, Double> entry
                    : contribution.getElementSpellCostMultipliers().entrySet()) {
                elementSpellCostMultiplier.put(
                        entry.getKey(),
                        elementSpellCostMultiplier.getOrDefault(
                                entry.getKey(), BigDecimal.ONE
                        ).multiply(
                                BigDecimal.valueOf(entry.getValue())
                        )
                );
            }
            postCastPermission |= contribution.canRegenerateDuringPostCastLockout();
            continuousPermission |= contribution.canRegenerateDuringContinuousCasting();
        }

        double maximumSubtotal = Math.max(0.0D, maximumRunning);
        double regenerationSubtotal = Math.max(0.0D, regenerationRunning);
        double lockoutSubtotal = Math.max(0.0D, lockoutRunning);
        double resolvedMaximum = resolveNumeric(
                maximumSubtotal,
                maximumIncrease,
                maximumReductionFactor
        );
        int maximum = resolvedMaximum >= Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : Math.max(1, (int) Math.floor(resolvedMaximum));
        double regeneration = resolveNumeric(
                regenerationSubtotal,
                regenerationIncrease,
                regenerationReductionFactor
        );
        double lockoutSeconds = resolveNumeric(
                lockoutSubtotal,
                lockoutIncrease,
                lockoutReductionFactor
        );

        ResolvedManaProfile profile = new ResolvedManaProfile(
                maximum,
                regeneration,
                lockoutSeconds,
                GameplayRules.secondsToTicks(lockoutSeconds),
                globalEfficiency,
                elementEfficiency,
                positiveDouble(globalSpellCostMultiplier),
                applicableSpellCostMultipliers(
                        globalSpellCostMultiplier,
                        elementSpellCostMultiplier
                ),
                postCastPermission,
                continuousPermission
        );
        return new ManaProfileCalculation(
                profile,
                new NumericAggregation(
                        baseMaximum, maximumFlat, maximumSubtotal,
                        maximumIncrease, maximumReductionFactor, resolvedMaximum
                ),
                new NumericAggregation(
                        baseRegeneration, regenerationFlat, regenerationSubtotal,
                        regenerationIncrease, regenerationReductionFactor, regeneration
                ),
                new NumericAggregation(
                        baseLockoutSeconds, lockoutFlat, lockoutSubtotal,
                        lockoutIncrease, lockoutReductionFactor, lockoutSeconds
                )
        );
    }

    private static double resolveNumeric(
            double subtotal,
            double increase,
            double reductionFactor
    ) {
        if (subtotal == 0.0D || reductionFactor == 0.0D) {
            return 0.0D;
        }
        double increaseFactor = saturatedAdd(1.0D, increase / 100.0D);
        double increased = saturatedMultiply(subtotal, increaseFactor);
        return saturatedMultiply(increased, reductionFactor);
    }

    private static double remainingFactor(double reduction) {
        return 1.0D - reduction / 100.0D;
    }

    private static double saturatedAdd(double left, double right) {
        double result = left + right;
        if (Double.isFinite(result)) {
            return result;
        }
        return result < 0.0D ? -Double.MAX_VALUE : Double.MAX_VALUE;
    }

    private static double saturatedMultiply(double left, double right) {
        double result = left * right;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    static void validateContribution(ManaContribution contribution) {
        requireFinite(contribution.getFlatRegeneration(), "flatRegeneration");
        requireFinite(contribution.getFlatLockoutSeconds(), "flatLockoutSeconds");
        requireNonNegativeFinite(contribution.getMaximumManaIncrease(), "maximumManaIncrease");
        requireReduction(contribution.getMaximumManaReduction(), "maximumManaReduction");
        requireNonNegativeFinite(contribution.getRegenerationIncrease(), "regenerationIncrease");
        requireReduction(contribution.getRegenerationReduction(), "regenerationReduction");
        requireNonNegativeFinite(contribution.getLockoutIncrease(), "lockoutIncrease");
        requireReduction(contribution.getLockoutReduction(), "lockoutReduction");
        requireNonNegativeFinite(contribution.getGlobalSpellEfficiency(), "globalSpellEfficiency");
        for (Map.Entry<Element, Double> entry
                : contribution.getElementSpellEfficiency().entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("Element efficiency key must not be null.");
            }
            requireNonNegativeFinite(entry.getValue(), "elementSpellEfficiency");
        }
        requirePositiveFinite(
                contribution.getGlobalSpellCostMultiplier(),
                "globalSpellCostMultiplier"
        );
        for (Map.Entry<Element, Double> entry
                : contribution.getElementSpellCostMultipliers().entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("Element cost-multiplier key must not be null.");
            }
            requirePositiveFinite(entry.getValue(), "elementSpellCostMultiplier");
        }
    }

    private static EnumMap<Element, Double> applicableSpellCostMultipliers(
            BigDecimal globalMultiplier,
            EnumMap<Element, BigDecimal> elementMultipliers
    ) {
        EnumMap<Element, Double> result = new EnumMap<>(Element.class);
        for (Element element : Element.values()) {
            BigDecimal elementMultiplier = elementMultipliers.getOrDefault(
                    element,
                    BigDecimal.ONE
            );
            result.put(
                    element,
                    positiveDouble(globalMultiplier.multiply(elementMultiplier))
            );
        }
        return result;
    }

    private static double positiveDouble(BigDecimal value) {
        if (value.compareTo(BigDecimal.valueOf(Double.MAX_VALUE)) > 0) {
            return Double.MAX_VALUE;
        }
        double result = value.doubleValue();
        return result == 0.0D ? Double.MIN_VALUE : result;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite.");
        }
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and positive.");
        }
    }

    private static void requireReduction(double value, String name) {
        requireNonNegativeFinite(value, name);
        if (value > 100.0D) {
            throw new IllegalArgumentException(name + " must not exceed 100.");
        }
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative.");
        }
    }
}
