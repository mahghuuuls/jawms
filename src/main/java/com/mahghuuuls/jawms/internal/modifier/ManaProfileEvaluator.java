package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionDisposition;
import com.mahghuuuls.jawms.internal.integration.wizardry.WizardryPassiveContributionAdapter;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.List;

/** Single source-aware boundary for authoritative profile evaluation. */
public final class ManaProfileEvaluator {

    private ManaProfileEvaluator() {
    }

    public static ManaProfileEvaluation evaluate(
            EntityPlayer player, GameplayRules rules
    ) {
        if (player == null || rules == null) {
            throw new NullPointerException("player and rules must not be null");
        }
        List<ContributionEvaluationRecord> records = ActiveStackCollector.collectRecords(
                player, rules
        );
        records.addAll(ManaProviderRegistry.instance().evaluateContextualRecords(
                player, rules
        ));
        records.addAll(WizardryPassiveContributionAdapter.collectRecords(
                player, rules.getWizardryIntegrationRules()
        ));
        List<ManaContribution> accepted = new ArrayList<>();
        for (ContributionEvaluationRecord record : records) {
            if (record.getDisposition() == ManaContributionDisposition.APPLIED) {
                accepted.add(record.getContribution());
            }
        }
        ManaProfileCalculation calculation = ManaProfileResolver.calculate(
                rules.getBaseMaximum(),
                rules.getBaseRegeneration(),
                rules.getRegenerationLockoutSeconds(),
                accepted
        );
        return new ManaProfileEvaluation(
                player.world.getTotalWorldTime(),
                rules.getRevision(),
                rules.getBaseMaximum(),
                rules.getBaseRegeneration(),
                rules.getRegenerationLockoutSeconds(),
                calculation,
                records
        );
    }
}
