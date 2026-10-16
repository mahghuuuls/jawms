package com.mahghuuuls.jawms.internal.integration.wizardry.payment;

import com.mahghuuuls.jawms.internal.mana.ManaReservation;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;

/** Short-lived success-only claim on the resources in one payment plan. */
public final class CastPaymentReservation {

    private final ManaReservation manaReservation;
    private final CastPaymentPlan plan;
    private final DemonicSealPolicy policy;
    private final EnumHand hand;
    private final ResourceLocation spellId;
    private final boolean continuous;
    private boolean active = true;
    private boolean committed;

    CastPaymentReservation(
            ManaReservation manaReservation,
            CastPaymentPlan plan,
            DemonicSealPolicy policy,
            EnumHand hand,
            ResourceLocation spellId,
            boolean continuous
    ) {
        this.manaReservation = manaReservation;
        this.plan = plan;
        this.policy = policy;
        this.hand = hand;
        this.spellId = spellId;
        this.continuous = continuous;
    }

    ManaReservation getManaReservation() { return manaReservation; }
    public CastPaymentPlan getPlan() { return plan; }
    public DemonicSealPolicy getPolicy() { return policy; }
    public EnumHand getHand() { return hand; }
    public ResourceLocation getSpellId() { return spellId; }
    public boolean isContinuous() { return continuous; }
    public int getPaidMana() { return plan.getMana(); }
    public int getPaidHunger() { return plan.getHunger(); }
    public boolean isActive() { return active; }
    public boolean isCommitted() { return committed; }

    void resolveCommitted() {
        resolve();
        committed = true;
    }

    void resolve() {
        if (!active) {
            throw new IllegalStateException("Cast payment reservation was already resolved.");
        }
        active = false;
    }
}
