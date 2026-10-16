package com.mahghuuuls.jawms.api;

import electroblob.wizardry.spell.Spell;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import java.util.Objects;

/**
 * Frozen inputs, not a provider cost prescription. Player and spell are borrowed
 * callback-only references. Stack access always copies the owning cast argument.
 * Identities are opaque, server-run-local values, not authorization tokens.
 */
public final class CastAttemptContext {
    private final EntityPlayer player;
    private final Spell spell;
    private final ResourceLocation spellId;
    private final ItemStack stack;
    private final EnumHand hand;
    private final boolean continuous;
    private final boolean creative;
    private final int castingTick;
    private final String useId;
    private final String attemptId;
    private final AcceptedCost cost;
    private final int portion;
    private final int plannedMana;
    private final int plannedHunger;

    public CastAttemptContext(EntityPlayer player, Spell spell, ResourceLocation spellId,
            ItemStack stack, EnumHand hand, boolean continuous, boolean creative,
            int castingTick, String useId, String attemptId, AcceptedCost cost,
            int portion, int plannedMana, int plannedHunger) {
        this.player = Objects.requireNonNull(player, "player");
        this.spell = Objects.requireNonNull(spell, "spell");
        this.spellId = Objects.requireNonNull(spellId, "spellId");
        this.stack = Objects.requireNonNull(stack, "stack").copy();
        this.hand = Objects.requireNonNull(hand, "hand");
        this.continuous = continuous;
        this.creative = creative;
        this.castingTick = castingTick;
        this.useId = Objects.requireNonNull(useId, "useId");
        this.attemptId = Objects.requireNonNull(attemptId, "attemptId");
        this.cost = Objects.requireNonNull(cost, "cost");
        if (portion < 0 || plannedMana < 0 || plannedHunger < 0) {
            throw new IllegalArgumentException("Cost/allocation must be non-negative.");
        }
        this.portion = portion;
        this.plannedMana = plannedMana;
        this.plannedHunger = plannedHunger;
    }

    public EntityPlayer getPlayer() { return player; }
    public Spell getSpell() { return spell; }
    public ResourceLocation getSpellId() { return spellId; }
    public ItemStack getStack() { return stack.copy(); }
    public EnumHand getHand() { return hand; }
    public boolean isContinuous() { return continuous; }
    public boolean isCreative() { return creative; }
    public int getCastingTick() { return castingTick; }
    public String getUseId() { return useId; }
    public String getAttemptId() { return attemptId; }
    public AcceptedCost getAcceptedCost() { return cost; }
    public int getManaPortion() { return portion; }
    public int getPlannedMana() { return plannedMana; }
    public int getPlannedHunger() { return plannedHunger; }

    /** Accepted physical-use snapshot. NaN denotes unavailable historical provenance. */
    public static final class AcceptedCost {
        private final int total;
        private final double base, contextualFactor, profileMultiplier, efficiencyPoints, scale;
        public AcceptedCost(int total, double base, double contextualFactor,
                double profileMultiplier, double efficiencyPoints, double scale) {
            if (total < 0) throw new IllegalArgumentException("total");
            this.total = total;
            this.base = base;
            this.contextualFactor = contextualFactor;
            this.profileMultiplier = profileMultiplier;
            this.efficiencyPoints = efficiencyPoints;
            this.scale = scale;
        }
        public int getTotalOrPerSecondCost() { return total; }
        public boolean isFormulaAvailable() {
            return Double.isFinite(base) && Double.isFinite(contextualFactor)
                    && Double.isFinite(profileMultiplier) && Double.isFinite(efficiencyPoints)
                    && Double.isFinite(scale);
        }
        public double getConfiguredBase() { return base; }
        public double getContextualFactor() { return contextualFactor; }
        public double getProfileMultiplier() { return profileMultiplier; }
        public double getEfficiencyPoints() { return efficiencyPoints; }
        public double getEfficiencyScale() { return scale; }
    }
}
