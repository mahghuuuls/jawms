package com.mahghuuuls.jawms.internal.integration.wizardry.effect;

import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaCauses;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaPublicState;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import com.mahghuuuls.jawms.internal.rules.WizardryIntegrationRules;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Applies the main-hand Wizardry Melee replacement at final damage commit. */
public final class WizardryMeleeManaService {

    private static final Map<UUID, LastMelee> LAST_MELEE = new HashMap<>();

    /**
     * Called only after Forge's complete LivingDamageEvent bus has returned.
     * Vanilla applies the returned positive amount directly to health.
     */
    public static float applyFinalDamage(
            EntityLivingBase target, DamageSource source, float finalDamage
    ) {
        if (!(source.getTrueSource() instanceof EntityPlayerMP)) {
            return finalDamage;
        }
        EntityPlayerMP player = (EntityPlayerMP) source.getTrueSource();
        if (target == player || !isDirectPlayerAttack(player, source)) {
            return finalDamage;
        }

        if (player.world.isRemote
                || !com.mahghuuuls.jawms.internal.mana.ManaService.canUseManaProfile(player)) {
            return finalDamage;
        }
        ItemStack mainHand = player.getHeldItemMainhand();
        if (!(mainHand.getItem() instanceof ItemWand)) {
            return finalDamage;
        }
        WizardryIntegrationRules rules = GameplayRulesManager.current()
                .getWizardryIntegrationRules();
        int level = Math.max(0, WandHelper.getUpgradeLevel(
                mainHand, WizardryItems.melee_upgrade
        ));
        IManaService mana = ManaApi.getManaService();
        ManaPublicState before = mana.getState(player);
        Outcome outcome;
        float augmentedDamage = finalDamage;
        int plannedMana = 0;
        int paidMana = 0;

        if (!rules.isMeleeEnabled()) {
            outcome = Outcome.DISABLED;
        } else if (level <= 0) {
            outcome = Outcome.NO_LEVEL;
        } else if (!(finalDamage > 0.0F) || !Float.isFinite(finalDamage)) {
            outcome = Outcome.NON_POSITIVE_DAMAGE;
        } else {
            Plan plan = plan(
                    level,
                    rules.getMeleeDamagePerLevel(),
                    rules.getMeleeManaPerLevel(),
                    finalDamage,
                    before.getCurrentMana(),
                    player.capabilities.isCreativeMode
            );
            plannedMana = plan.getManaCost();
            if (!plan.hasDamageBonus()) {
                outcome = Outcome.NO_DAMAGE_BONUS;
            } else if (!plan.isAffordable()) {
                outcome = Outcome.UNAFFORDABLE;
            } else if (player.capabilities.isCreativeMode || plannedMana == 0) {
                augmentedDamage = plan.getAugmentedDamage();
                outcome = Outcome.ENHANCED_FREE;
            } else {
                ManaMutationResult payment = mana.consumeMana(
                        player, plannedMana, ManaCauses.WIZARDRY_MELEE
                );
                if (payment.isSuccessful() && payment.getActualAmount() == plannedMana) {
                    paidMana = plannedMana;
                    augmentedDamage = plan.getAugmentedDamage();
                    outcome = Outcome.ENHANCED_PAID;
                } else {
                    outcome = Outcome.PAYMENT_REJECTED;
                }
            }
        }

        ManaPublicState after = mana.getState(player);
        remember(player, new LastMelee(
                rules.getRevision(), outcome, level, finalDamage, augmentedDamage,
                plannedMana, paidMana,
                before.getCurrentMana(), after.getCurrentMana(),
                before.getRemainingLockoutTicks(), after.getRemainingLockoutTicks(),
                target.getEntityId()
        ));
        return augmentedDamage;
    }

    static boolean isDirectPlayerAttack(EntityPlayerMP player, DamageSource source) {
        if (!(source instanceof EntityDamageSource)) {
            return false;
        }
        EntityDamageSource entitySource = (EntityDamageSource) source;
        return !entitySource.getIsThornsDamage()
                && source.getTrueSource() == player
                && source.getImmediateSource() == player
                && "player".equals(source.getDamageType())
                && !source.isProjectile()
                && !source.isUnblockable();
    }

    public static Plan plan(
            int level,
            double damagePerLevel,
            int manaPerLevel,
            float finalDamage,
            int currentMana,
            boolean creative
    ) {
        int safeLevel = Math.max(0, level);
        double rawBonus = safeLevel * Math.max(0.0D, damagePerLevel);
        float augmented = finalDamage;
        if (finalDamage > 0.0F && Float.isFinite(finalDamage)
                && rawBonus > 0.0D && Double.isFinite(rawBonus)) {
            double total = Math.min((double) Float.MAX_VALUE, finalDamage + rawBonus);
            augmented = (float) total;
        } else if (rawBonus == Double.POSITIVE_INFINITY && finalDamage > 0.0F) {
            augmented = Float.MAX_VALUE;
        }
        long rawCost = (long) safeLevel * (long) Math.max(0, manaPerLevel);
        int cost = rawCost >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rawCost;
        boolean hasBonus = augmented > finalDamage;
        boolean affordable = hasBonus && (creative || cost == 0 || currentMana >= cost);
        return new Plan(augmented, cost, hasBonus, affordable);
    }

    public static String describeLastMelee(EntityPlayerMP player) {
        LastMelee record = LAST_MELEE.get(player.getUniqueID());
        return record == null ? "JAWMS integration last-melee=none." : record.describe();
    }

    public static void clearLastMelee(EntityPlayerMP player) {
        LAST_MELEE.remove(player.getUniqueID());
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_MELEE.remove(event.player.getUniqueID());
    }

    private static void remember(EntityPlayerMP player, LastMelee record) {
        if (Boolean.getBoolean("jawms.enableTestCommands")) {
            LAST_MELEE.put(player.getUniqueID(), record);
        }
    }

    public enum Outcome {
        ENHANCED_PAID,
        ENHANCED_FREE,
        UNAFFORDABLE,
        PAYMENT_REJECTED,
        NON_POSITIVE_DAMAGE,
        NO_DAMAGE_BONUS,
        NO_LEVEL,
        DISABLED
    }

    public static final class Plan {
        private final float augmentedDamage;
        private final int manaCost;
        private final boolean damageBonus;
        private final boolean affordable;

        private Plan(
                float augmentedDamage, int manaCost,
                boolean damageBonus, boolean affordable
        ) {
            this.augmentedDamage = augmentedDamage;
            this.manaCost = manaCost;
            this.damageBonus = damageBonus;
            this.affordable = affordable;
        }

        public float getAugmentedDamage() { return augmentedDamage; }
        public int getManaCost() { return manaCost; }
        public boolean hasDamageBonus() { return damageBonus; }
        public boolean isAffordable() { return affordable; }
    }

    private static final class LastMelee {
        private final long rulesRevision;
        private final Outcome outcome;
        private final int level;
        private final float originalDamage;
        private final float finalDamage;
        private final int plannedMana;
        private final int paidMana;
        private final int manaBefore;
        private final int manaAfter;
        private final long lockoutBefore;
        private final long lockoutAfter;
        private final int targetId;

        private LastMelee(
                long rulesRevision, Outcome outcome, int level,
                float originalDamage, float finalDamage,
                int plannedMana, int paidMana,
                int manaBefore, int manaAfter,
                long lockoutBefore, long lockoutAfter, int targetId
        ) {
            this.rulesRevision = rulesRevision;
            this.outcome = outcome;
            this.level = level;
            this.originalDamage = originalDamage;
            this.finalDamage = finalDamage;
            this.plannedMana = plannedMana;
            this.paidMana = paidMana;
            this.manaBefore = manaBefore;
            this.manaAfter = manaAfter;
            this.lockoutBefore = lockoutBefore;
            this.lockoutAfter = lockoutAfter;
            this.targetId = targetId;
        }

        private String describe() {
            return "JAWMS integration last-melee rulesRevision=" + rulesRevision
                    + ", outcome=" + outcome
                    + ", level=" + level
                    + ", damage=" + originalDamage + "->" + finalDamage
                    + ", manaPayment=" + plannedMana + "/" + paidMana
                    + ", mana=" + manaBefore + "->" + manaAfter
                    + ", lockout=" + lockoutBefore + "->" + lockoutAfter
                    + ", targetId=" + targetId + ".";
        }
    }
}
