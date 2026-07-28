package com.mahghuuuls.jawms.internal.casting;

import com.mahghuuuls.jawms.internal.mana.ManaReservation;
import com.mahghuuuls.jawms.internal.mana.ManaService;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.Deque;

public final class WizardryWandBridge {

    private static final ThreadLocal<Deque<CastAttempt>> ATTEMPTS =
            ThreadLocal.withInitial(ArrayDeque::new);

    private WizardryWandBridge() {
    }

    public static int replaceCalculatedCost(
            int wizardryCost,
            Spell spell,
            EntityPlayer caster,
            SpellModifiers modifiers
    ) {
        if (spell.isContinuous || caster.world.isRemote) {
            return wizardryCost;
        }
        return SpellCostService.getFinalCost(spell, modifiers);
    }

    public static int getAvailableManaForPrecheck(
            int wizardryWandMana,
            Spell spell,
            EntityPlayer caster,
            EnumHand hand,
            SpellModifiers modifiers
    ) {
        if (spell.isContinuous) {
            return wizardryWandMana;
        }
        if (caster.world.isRemote || caster.isCreative()) {
            return Integer.MAX_VALUE;
        }

        int required = SpellCostService.getFinalCost(spell, modifiers);
        int available = ManaService.getAvailableMana(caster);
        if (required > available) {
            notifyInsufficient(caster, spell, hand, required, available);
            CastingDiagnostics.trace(
                    "precheck_rejected",
                    caster,
                    spell.getRegistryName(),
                    hand,
                    required,
                    available
            );
        }
        return available;
    }

    public static boolean castWithReservation(
            Spell spell,
            World world,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers
    ) {
        if (world.isRemote || spell.isContinuous || caster.isCreative()) {
            return spell.cast(world, caster, hand, castingTick, modifiers);
        }

        int cost = SpellCostService.getFinalCost(spell, modifiers);
        ManaReservation reservation = ManaService.reserveMana(caster, cost);
        if (reservation == null) {
            int available = ManaService.getAvailableMana(caster);
            notifyInsufficient(caster, spell, hand, cost, available);
            CastingDiagnostics.trace(
                    "reservation_rejected",
                    caster,
                    spell.getRegistryName(),
                    hand,
                    cost,
                    available
            );
            return false;
        }

        CastAttempt attempt = new CastAttempt(reservation, spell.getRegistryName(), hand);
        ATTEMPTS.get().push(attempt);
        CastingDiagnostics.trace(
                "reservation_created",
                caster,
                attempt.spellId,
                hand,
                cost,
                ManaService.getAvailableMana(caster)
        );
        try {
            boolean successful = spell.cast(world, caster, hand, castingTick, modifiers);
            if (!successful) {
                releaseTopAttempt(caster, attempt, "spell_failed");
            }
            return successful;
        } catch (RuntimeException | Error throwable) {
            releaseTopAttempt(caster, attempt, "spell_threw");
            throw throwable;
        }
    }

    public static void commitFromSuccessfulDeduction(
            ItemWand wand,
            ItemStack stack,
            int wizardryCost,
            EntityLivingBase wielder,
            Spell spell
    ) {
        if (spell.isContinuous) {
            wand.consumeMana(stack, wizardryCost, wielder);
            return;
        }
        if (!(wielder instanceof EntityPlayer)) {
            throw new IllegalStateException("Non-continuous wand deduction did not have a player caster.");
        }
        EntityPlayer player = (EntityPlayer) wielder;
        if (player.world.isRemote || player.isCreative()) {
            return;
        }
        commitTopAttempt(player, spell, true);
    }

    public static void commitZeroCostIfPending(EntityPlayer caster, Spell spell) {
        if (caster.world.isRemote || caster.isCreative() || spell.isContinuous) {
            return;
        }
        commitTopAttempt(caster, spell, false);
    }

    public static int captureAttemptDepth() {
        return ATTEMPTS.get().size();
    }

    public static void cleanupToDepth(EntityPlayer caster, int depth) {
        Deque<CastAttempt> attempts = ATTEMPTS.get();
        while (attempts.size() > depth) {
            CastAttempt attempt = attempts.pop();
            ManaService.releaseReservation(attempt.reservation);
            CastingDiagnostics.trace(
                    "reservation_cleanup",
                    caster,
                    attempt.spellId,
                    attempt.hand,
                    attempt.reservation.getAmount(),
                    ManaService.getAvailableMana(caster)
            );
        }
        if (attempts.isEmpty()) {
            ATTEMPTS.remove();
        }
    }

    private static void commitTopAttempt(EntityPlayer caster, Spell spell, boolean required) {
        Deque<CastAttempt> attempts = ATTEMPTS.get();
        CastAttempt attempt = attempts.peek();
        if (attempt == null) {
            if (required) {
                throw new IllegalStateException(
                        "Successful Wizardry wand deduction had no JAWMS reservation."
                );
            }
            return;
        }
        ResourceLocation spellId = spell.getRegistryName();
        if (!equals(attempt.spellId, spellId)) {
            throw new IllegalStateException(
                    "JAWMS reservation spell mismatch: expected "
                            + attempt.spellId + " but received " + spellId + "."
            );
        }

        ManaService.completeSuccessfulWandCast(caster, attempt.reservation);
        attempts.pop();
        CastingDiagnostics.trace(
                "reservation_committed",
                caster,
                attempt.spellId,
                attempt.hand,
                attempt.reservation.getAmount(),
                ManaService.getAvailableMana(caster)
        );
        if (attempts.isEmpty()) {
            ATTEMPTS.remove();
        }
    }

    private static void releaseTopAttempt(
            EntityPlayer caster,
            CastAttempt expected,
            String event
    ) {
        Deque<CastAttempt> attempts = ATTEMPTS.get();
        if (attempts.peek() != expected) {
            throw new IllegalStateException("JAWMS wand reservation stack was corrupted.");
        }
        attempts.pop();
        ManaService.releaseReservation(expected.reservation);
        CastingDiagnostics.trace(
                event,
                caster,
                expected.spellId,
                expected.hand,
                expected.reservation.getAmount(),
                ManaService.getAvailableMana(caster)
        );
        if (attempts.isEmpty()) {
            ATTEMPTS.remove();
        }
    }

    private static void notifyInsufficient(
            EntityPlayer caster,
            Spell spell,
            EnumHand hand,
            int required,
            int available
    ) {
        if (ManaService.shouldNotifyInsufficient(
                caster,
                caster.world.getTotalWorldTime(),
                required,
                available
        )) {
            caster.sendStatusMessage(
                    new TextComponentTranslation(
                            "message.jawms.insufficient_mana",
                            required,
                            available
                    ),
                    true
            );
        }
    }

    private static boolean equals(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static final class CastAttempt {

        private final ManaReservation reservation;
        private final ResourceLocation spellId;
        private final EnumHand hand;

        private CastAttempt(
                ManaReservation reservation,
                ResourceLocation spellId,
                EnumHand hand
        ) {
            this.reservation = reservation;
            this.spellId = spellId;
            this.hand = hand;
        }
    }
}
