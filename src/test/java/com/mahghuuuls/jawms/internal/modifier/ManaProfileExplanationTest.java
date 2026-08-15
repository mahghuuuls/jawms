package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaContributionExplanation;
import com.mahghuuuls.jawms.api.ManaContributionDisposition;
import com.mahghuuuls.jawms.api.ManaContributionFailure;
import com.mahghuuuls.jawms.api.ManaContributionSourceKind;
import com.mahghuuuls.jawms.api.ManaProfileExplanation;
import com.mahghuuuls.jawms.internal.mana.ManaCapability;
import com.mahghuuuls.jawms.internal.mana.ManaState;
import electroblob.wizardry.constants.Element;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManaProfileExplanationTest {

    @Test
    void retainedExplanationMatchesTheExactResolverCalculationWithoutProviderRecall() {
        ManaContribution accepted = ManaContribution.builder()
                .flatMaximumMana(10)
                .maximumManaIncrease(50.0D)
                .maximumManaReduction(20.0D)
                .flatRegeneration(1.0D)
                .regenerationIncrease(25.0D)
                .regenerationReduction(10.0D)
                .flatLockoutSeconds(-1.0D)
                .lockoutIncrease(10.0D)
                .lockoutReduction(50.0D)
                .globalSpellEfficiency(5.0D)
                .elementSpellEfficiency(Element.FIRE, 7.0D)
                .globalSpellCostMultiplier(0.5D)
                .elementSpellCostMultiplier(Element.FIRE, 1.5D)
                .regenerateDuringPostCastLockout(true)
                .build();
        AtomicInteger calls = new AtomicInteger();
        ManaProviderRegistry registry = new ManaProviderRegistry();
        registry.registerContextual(id("applied"), player -> {
            calls.incrementAndGet();
            return accepted;
        });
        registry.registerContextual(id("empty"), player -> ManaContribution.EMPTY);
        PlayerFixture fixture = player(42L);
        assertThrows(IllegalStateException.class,
                () -> ManaApi.explainManaProfile(fixture.player));
        assertEquals(0, calls.get());
        List<ContributionEvaluationRecord> records = registry.evaluateContextualRecords(
                fixture.player, com.mahghuuuls.jawms.internal.rules.GameplayRules.initialDefaults()
        );
        ManaProfileCalculation calculation = ManaProfileResolver.calculate(
                60, 2.4D, 2.0D, Collections.singletonList(accepted)
        );
        ManaProfileEvaluation evaluation = new ManaProfileEvaluation(
                42L, 3L, 60, 2.4D, 2.0D, calculation, records
        );
        fixture.state.applyProfileEvaluation(evaluation);

        ManaProfileExplanation first = ManaApi.explainManaProfile(fixture.player);
        ManaProfileExplanation second = ManaApi.explainManaProfile(fixture.player);

        assertEquals(1, calls.get());
        assertEquals(42L, first.getEvaluatedWorldTick());
        assertEquals(3L, first.getGameplayRulesRevision());
        assertEquals(60, first.getConfiguredBaseMaximumMana());
        assertEquals(2.4D, first.getConfiguredBaseRegeneration());
        assertEquals(2.0D, first.getConfiguredBaseLockoutSeconds());
        assertEquals(84, first.getFinalMaximumMana());
        assertEquals(fixture.state.getMaximumMana(), first.getFinalMaximumMana());
        assertEquals(3.825D, first.getFinalRegeneration(), 0.000000001D);
        assertEquals(fixture.state.getEffectiveRegeneration(),
                first.getFinalRegeneration(), 0.000000001D);
        assertEquals(0.55D, first.getFinalLockoutSeconds(), 0.000000001D);
        assertEquals(11L, first.getFinalLockoutTicks());
        assertEquals(fixture.state.getEffectiveLockoutTicks(),
                first.getFinalLockoutTicks());
        assertEquals(5.0D, first.getGlobalSpellEfficiency());
        assertEquals(fixture.state.getGlobalSpellEfficiency(),
                first.getGlobalSpellEfficiency());
        assertEquals(7.0D, first.getElementSpellEfficiency().get(Element.FIRE));
        assertEquals(fixture.state.getElementSpellEfficiency(),
                first.getElementSpellEfficiency());
        assertEquals(0.5D, first.getGlobalSpellCostMultiplier());
        assertEquals(fixture.state.getGlobalSpellCostMultiplier(),
                first.getGlobalSpellCostMultiplier());
        assertEquals(0.75D, first.getElementSpellCostMultipliers().get(Element.FIRE));
        assertEquals(fixture.state.getElementSpellCostMultipliers(),
                first.getElementSpellCostMultipliers());
        assertTrue(first.canRegenerateDuringPostCastLockout());
        assertFalse(first.canRegenerateDuringContinuousCasting());
        assertEquals(10.0D, first.getMaximumManaAggregation().getFlatTotal());
        assertEquals(70.0D, first.getMaximumManaAggregation().getNonNegativeSubtotal());
        assertEquals(50.0D, first.getMaximumManaAggregation().getIncreasePercentage());
        assertEquals(0.8D, first.getMaximumManaAggregation().getReductionFactor());
        assertEquals(84.0D, first.getMaximumManaAggregation().getResolvedValue());
        assertEquals(2, first.getContributions().size());
        assertEquals(ManaContributionDisposition.APPLIED,
                first.getContributions().get(0).getDisposition());
        assertEquals(ManaContributionDisposition.EMPTY,
                first.getContributions().get(1).getDisposition());
        assertFalse(first.getContributions().get(1).hasAcceptedContribution());
        assertThrows(UnsupportedOperationException.class,
                () -> first.getContributions().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> first.getElementSpellEfficiency().clear());
        assertEquals(first.getFinalMaximumMana(), second.getFinalMaximumMana());
    }

    @Test
    void traceIsClearedByLifecycleAndNonSourceAwareReplacement() {
        ManaContribution contribution = ManaContribution.builder()
                .flatMaximumMana(1)
                .build();
        ContributionEvaluationRecord record = ContributionEvaluationRecord.playerWide(
                id("source"),
                ManaContributionSourceKind.CONTEXTUAL_PROVIDER,
                ManaContributionDisposition.APPLIED,
                ManaContributionFailure.NONE,
                contribution
        );
        ManaProfileCalculation calculation = ManaProfileResolver.calculate(
                60, 2.4D, 2.0D, Collections.singletonList(contribution)
        );
        ManaProfileEvaluation evaluation = new ManaProfileEvaluation(
                1L, 0L, 60, 2.4D, 2.0D, calculation,
                Collections.singletonList(record)
        );
        ManaState state = new ManaState();
        state.applyProfileEvaluation(evaluation);
        state.clearTransientLifecycleState();
        assertThrows(IllegalStateException.class, state::explainAcceptedProfile);

        state.applyProfileEvaluation(evaluation);
        state.applyResolvedProfile(calculation.getProfile());
        assertThrows(IllegalStateException.class, state::explainAcceptedProfile);

        state.applyProfileEvaluation(evaluation);
        ManaState replacement = new ManaState();
        replacement.copyFrom(state);
        assertThrows(IllegalStateException.class, replacement::explainAcceptedProfile);

        NBTTagCompound saved = state.serializeIntrinsicState();
        state.deserializeIntrinsicState(saved);
        assertThrows(IllegalStateException.class, state::explainAcceptedProfile);
    }

    @Test
    void appliedExplanationRejectsANeutralContribution() {
        assertThrows(IllegalArgumentException.class, () ->
                new ManaContributionExplanation(
                        id("neutral"),
                        ManaContributionSourceKind.CONTEXTUAL_PROVIDER,
                        ManaContributionDisposition.APPLIED,
                        ManaContributionFailure.NONE,
                        null,
                        null,
                        -1,
                        ManaContribution.EMPTY
                ));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("test", path);
    }

    private static PlayerFixture player(long tick) {
        EntityPlayer player = mock(EntityPlayerMP.class);
        WorldServer world = mock(WorldServer.class);
        ManaState state = new ManaState();
        player.world = world;
        when(player.getUniqueID()).thenReturn(UUID.randomUUID());
        when(world.getTotalWorldTime()).thenReturn(tick);
        when(world.isCallingFromMinecraftThread()).thenReturn(true);
        when(player.getCapability(ManaCapability.INSTANCE, null)).thenReturn(state);
        return new PlayerFixture(player, state);
    }

    private static final class PlayerFixture {
        private final EntityPlayer player;
        private final ManaState state;

        private PlayerFixture(EntityPlayer player, ManaState state) {
            this.player = player;
            this.state = state;
        }
    }
}
