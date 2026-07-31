package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import electroblob.wizardry.constants.Element;

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
        if (baseMaximum < 1) {
            throw new IllegalArgumentException("Base maximum must be positive.");
        }
        requireNonNegativeFinite(baseRegeneration, "baseRegeneration");
        requireNonNegativeFinite(baseLockoutSeconds, "baseLockoutSeconds");
        if (contributions == null) {
            throw new NullPointerException("contributions");
        }

        double maximumFlat = baseMaximum;
        double maximumIncrease = 0.0D;
        double maximumReductionFactor = 1.0D;
        double regenerationFlat = baseRegeneration;
        double regenerationIncrease = 0.0D;
        double regenerationReductionFactor = 1.0D;
        double lockoutFlat = baseLockoutSeconds;
        double lockoutIncrease = 0.0D;
        double lockoutReductionFactor = 1.0D;
        double globalEfficiency = 0.0D;
        EnumMap<Element, Double> elementEfficiency = new EnumMap<>(Element.class);
        boolean postCastPermission = false;
        boolean continuousPermission = false;

        for (ManaContribution contribution : contributions) {
            if (contribution == null) {
                throw new IllegalArgumentException("Contribution list contains null.");
            }
            maximumFlat = saturatedAdd(maximumFlat, contribution.getFlatMaximumMana());
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
            postCastPermission |= contribution.canRegenerateDuringPostCastLockout();
            continuousPermission |= contribution.canRegenerateDuringContinuousCasting();
        }

        double resolvedMaximum = resolveNumeric(
                Math.max(0.0D, maximumFlat),
                maximumIncrease,
                maximumReductionFactor
        );
        int maximum = resolvedMaximum >= Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : Math.max(1, (int) Math.floor(resolvedMaximum));
        double regeneration = resolveNumeric(
                Math.max(0.0D, regenerationFlat),
                regenerationIncrease,
                regenerationReductionFactor
        );
        double lockoutSeconds = resolveNumeric(
                Math.max(0.0D, lockoutFlat),
                lockoutIncrease,
                lockoutReductionFactor
        );

        return new ResolvedManaProfile(
                maximum,
                regeneration,
                lockoutSeconds,
                GameplayRules.secondsToTicks(lockoutSeconds),
                globalEfficiency,
                elementEfficiency,
                postCastPermission,
                continuousPermission
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

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative.");
        }
    }
}
