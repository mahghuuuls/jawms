package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaContributionExplanation;
import com.mahghuuuls.jawms.api.ManaProfileExplanation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One immutable profile and provenance trace accepted together by authoritative state. */
public final class ManaProfileEvaluation {

    private final long worldTick;
    private final long rulesRevision;
    private final int baseMaximum;
    private final double baseRegeneration;
    private final double baseLockoutSeconds;
    private final ManaProfileCalculation calculation;
    private final List<ContributionEvaluationRecord> records;

    ManaProfileEvaluation(
            long worldTick,
            long rulesRevision,
            int baseMaximum,
            double baseRegeneration,
            double baseLockoutSeconds,
            ManaProfileCalculation calculation,
            List<ContributionEvaluationRecord> records
    ) {
        this.worldTick = worldTick;
        this.rulesRevision = rulesRevision;
        this.baseMaximum = baseMaximum;
        this.baseRegeneration = baseRegeneration;
        this.baseLockoutSeconds = baseLockoutSeconds;
        this.calculation = calculation;
        this.records = Collections.unmodifiableList(new ArrayList<>(records));
    }

    public ResolvedManaProfile getProfile() {
        return calculation.getProfile();
    }

    public ManaProfileExplanation toPublicExplanation() {
        ResolvedManaProfile profile = calculation.getProfile();
        List<ManaContributionExplanation> publicRecords = new ArrayList<>(records.size());
        for (ContributionEvaluationRecord record : records) {
            publicRecords.add(record.toPublicExplanation());
        }
        return new ManaProfileExplanation(
                worldTick,
                rulesRevision,
                baseMaximum,
                baseRegeneration,
                baseLockoutSeconds,
                calculation.getMaximumMana().toPublicAggregation(),
                calculation.getRegeneration().toPublicAggregation(),
                calculation.getLockout().toPublicAggregation(),
                profile.getMaximumMana(),
                profile.getRegeneration(),
                profile.getLockoutSeconds(),
                profile.getLockoutTicks(),
                profile.getGlobalSpellEfficiency(),
                profile.getElementSpellEfficiency(),
                profile.getGlobalSpellCostMultiplier(),
                profile.getElementSpellCostMultipliers(),
                profile.canRegenerateDuringPostCastLockout(),
                profile.canRegenerateDuringContinuousCasting(),
                publicRecords
        );
    }
}
