package com.mahghuuuls.jawms.internal.integration.wizardry.effect;

import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaCauses;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaPublicState;
import com.mahghuuuls.jawms.internal.integration.wizardry.WizardryPassiveContributionAdapter;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import com.mahghuuuls.jawms.internal.rules.WizardryIntegrationRules;
import electroblob.wizardry.item.ItemArtefact;
import electroblob.wizardry.registry.WizardryItems;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Applies current-rule Wizardry kill effects to authoritative player mana. */
public final class WizardryKillManaService {

    private static final Map<UUID, LastEffect> LAST_EFFECTS = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getTrueSource() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getSource().getTrueSource();
        if (player.world.isRemote) {
            return;
        }

        WizardryIntegrationRules rules = GameplayRulesManager.current()
                .getWizardryIntegrationRules();
        int siphonLevel = rules.isSiphonEnabled()
                ? WizardryPassiveContributionAdapter.aggregateHeldUpgrade(
                player,
                WizardryItems.siphon_upgrade,
                rules.getHeldWandAggregation()
        ) : 0;
        boolean siphoning = siphonLevel > 0
                && rules.isSiphoningEnabled()
                && ItemArtefact.isArtefactActive(player, WizardryItems.ring_siphoning);
        int siphonRequested = calculateSiphonRestoration(
                siphonLevel,
                rules.getSiphonManaPerLevel(),
                siphoning,
                rules.getSiphoningIncrease()
        );

        WizardrySorceryAttribution.Category attribution =
                WizardrySorceryAttribution.classify(event.getSource(), player);
        boolean extraction = rules.isExtractionEnabled()
                && WizardrySorceryAttribution.isSorcery(attribution)
                && ItemArtefact.isArtefactActive(player, WizardryItems.ring_extraction);
        int extractionRequested = extraction ? rules.getExtractionMana() : 0;

        IManaService mana = ManaApi.getManaService();
        ManaPublicState before = mana.getState(player);
        int siphonActual = restore(mana, player, siphonRequested, true);
        int extractionActual = restore(mana, player, extractionRequested, false);
        ManaPublicState after = mana.getState(player);
        if (Boolean.getBoolean("jawms.enableTestCommands")) {
            LAST_EFFECTS.put(player.getUniqueID(), new LastEffect(
                    rules.getRevision(), siphonLevel, rules.getHeldWandAggregation(),
                    siphoning, attribution, extraction,
                    siphonRequested, siphonActual, extractionRequested, extractionActual,
                    before.getCurrentMana(), after.getCurrentMana(),
                    before.getRemainingLockoutTicks(), after.getRemainingLockoutTicks()
            ));
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_EFFECTS.remove(event.player.getUniqueID());
    }

    public static int calculateSiphonRestoration(
            int level, int manaPerLevel, boolean siphoning, double increasePercent
    ) {
        long base = (long) Math.max(0, level) * (long) Math.max(0, manaPerLevel);
        if (base <= 0L) {
            return 0;
        }
        if (!siphoning || increasePercent <= 0.0D) {
            return saturatingInt(base);
        }
        if (!Double.isFinite(increasePercent)) {
            return Integer.MAX_VALUE;
        }
        BigDecimal multiplier = BigDecimal.ONE.add(
                BigDecimal.valueOf(increasePercent).movePointLeft(2)
        );
        BigDecimal modified = BigDecimal.valueOf(base).multiply(multiplier)
                .setScale(0, RoundingMode.CEILING);
        if (modified.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) >= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, modified.intValue());
    }

    public static String describeLastEffect(EntityPlayerMP player) {
        LastEffect effect = LAST_EFFECTS.get(player.getUniqueID());
        return effect == null ? "JAWMS integration last-effect=none." : effect.describe();
    }

    public static void clearLastEffect(EntityPlayerMP player) {
        LAST_EFFECTS.remove(player.getUniqueID());
    }

    private static int restore(
            IManaService mana, EntityPlayerMP player, int requested, boolean siphon
    ) {
        if (requested <= 0) {
            return 0;
        }
        ManaMutationResult result = mana.restoreMana(
                player,
                requested,
                siphon ? ManaCauses.WIZARDRY_SIPHON : ManaCauses.WIZARDRY_EXTRACTION
        );
        return result.getActualAmount();
    }

    private static int saturatingInt(long value) {
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static final class LastEffect {
        private final long rulesRevision;
        private final int siphonLevel;
        private final WizardryIntegrationRules.HeldWandAggregation aggregation;
        private final boolean siphoning;
        private final WizardrySorceryAttribution.Category attribution;
        private final boolean extraction;
        private final int siphonRequested;
        private final int siphonActual;
        private final int extractionRequested;
        private final int extractionActual;
        private final int manaBefore;
        private final int manaAfter;
        private final long lockoutBefore;
        private final long lockoutAfter;

        private LastEffect(
                long rulesRevision,
                int siphonLevel,
                WizardryIntegrationRules.HeldWandAggregation aggregation,
                boolean siphoning,
                WizardrySorceryAttribution.Category attribution,
                boolean extraction,
                int siphonRequested,
                int siphonActual,
                int extractionRequested,
                int extractionActual,
                int manaBefore,
                int manaAfter,
                long lockoutBefore,
                long lockoutAfter
        ) {
            this.rulesRevision = rulesRevision;
            this.siphonLevel = siphonLevel;
            this.aggregation = aggregation;
            this.siphoning = siphoning;
            this.attribution = attribution;
            this.extraction = extraction;
            this.siphonRequested = siphonRequested;
            this.siphonActual = siphonActual;
            this.extractionRequested = extractionRequested;
            this.extractionActual = extractionActual;
            this.manaBefore = manaBefore;
            this.manaAfter = manaAfter;
            this.lockoutBefore = lockoutBefore;
            this.lockoutAfter = lockoutAfter;
        }

        private String describe() {
            return "JAWMS integration last-effect rulesRevision=" + rulesRevision
                    + ", siphonLevel=" + siphonLevel
                    + ", aggregation=" + aggregation
                    + ", siphoning=" + siphoning
                    + ", attribution=" + attribution
                    + ", extraction=" + extraction
                    + ", siphon=" + siphonRequested + "/" + siphonActual
                    + ", extractionMana=" + extractionRequested + "/" + extractionActual
                    + ", mana=" + manaBefore + "->" + manaAfter
                    + ", lockout=" + lockoutBefore + "->" + lockoutAfter + ".";
        }
    }
}
