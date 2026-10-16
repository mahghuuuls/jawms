package com.mahghuuuls.jawms.internal.integration.wizardry.payment;

import com.mahghuuuls.jawms.internal.mana.ManaReservation;
import com.mahghuuuls.jawms.internal.mana.ManaService;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread authority for complete mana-plus-hunger cast payments. */
public final class CastPaymentService {

    private static final Map<UUID, Integer> RESERVED_HUNGER = new HashMap<>();
    private static final Map<UUID, PaymentRecord> LAST_PAYMENT = new HashMap<>();

    private CastPaymentService() {
    }

    public static synchronized CastPaymentPlan preview(
            EntityPlayer player,
            int cost,
            DemonicSealPolicy policy,
            boolean continuous,
            EnumHand hand,
            ResourceLocation spellId
    ) {
        ManaService.requireState(player);
        CastPaymentPlan plan = plan(
                player, cost, policy, continuous, reservedHunger(player)
        );
        record(player, policy, plan, hand, spellId,
                plan.isAffordable() ? "preflight_affordable" : "preflight_rejected");
        return plan;
    }

    public static synchronized CastPaymentReservation reserve(
            EntityPlayer player,
            int cost,
            DemonicSealPolicy policy,
            boolean continuous,
            EnumHand hand,
            ResourceLocation spellId
    ) {
        ManaService.requireState(player);
        CastPaymentPlan plan = plan(
                player, cost, policy, continuous, reservedHunger(player)
        );
        if (!plan.isAffordable()) {
            record(player, policy, plan, hand, spellId, "reservation_rejected");
            return null;
        }
        ManaReservation mana = ManaService.reserveMana(player, plan.getMana());
        if (mana == null) {
            record(player, policy, plan, hand, spellId, "reservation_race_rejected");
            return null;
        }
        if (plan.getHunger() > 0) {
            RESERVED_HUNGER.put(
                    player.getUniqueID(),
                    saturatedAdd(reservedHunger(player), plan.getHunger())
            );
        }
        CastPaymentReservation reservation = new CastPaymentReservation(
                mana, plan, policy, hand, spellId, continuous
        );
        record(player, policy, plan, hand, spellId, "reserved");
        return reservation;
    }

    public static synchronized void commit(
            EntityPlayer player,
            CastPaymentReservation reservation,
            boolean continuous,
            EnumHand hand,
            ResourceLocation spellId
    ) {
        requireActive(reservation);
        if (continuous != reservation.isContinuous()
                || hand != reservation.getHand()
                || !equals(spellId, reservation.getSpellId())) {
            throw new IllegalStateException("Cast payment context did not match its reservation.");
        }
        int otherReserved = reservedHunger(player) - reservation.getPaidHunger();
        if (otherReserved < 0) {
            throw new IllegalStateException("Reserved hunger accounting was corrupted.");
        }
        int currentHunger = player.getFoodStats().getFoodLevel();
        if (currentHunger - reservation.getPaidHunger()
                < reservation.getPolicy().getMinimumHunger() + otherReserved) {
            throw new IllegalStateException(
                    "Demonic Seal resources changed before the successful payment boundary."
            );
        }
        ManaService.completeSuccessfulCastPayment(
                player,
                reservation.getManaReservation(),
                reservation.getPaidHunger(),
                reservation.getPolicy().getMinimumHunger(),
                continuous,
                hand,
                spellId
        );
        releaseHungerClaim(player, reservation.getPaidHunger());
        reservation.resolve();
        record(player, reservation.getPolicy(), reservation.getPlan(),
                hand, spellId, "committed");
    }

    /** Validates the accepted allocation; only hunger spenders require a protected food floor. */
    public static synchronized boolean isValid(EntityPlayer player, CastPaymentReservation reservation) {
        if (reservation == null || !reservation.isActive()) return false;
        return ManaService.isReservationValid(player, reservation.getManaReservation())
                && (reservation.getPaidHunger() == 0
                || player.getFoodStats().getFoodLevel() - reservedHunger(player)
                >= reservation.getPolicy().getMinimumHunger());
    }

    /** Local resources/receipt first; accepted-success markers are coordinated separately. */
    public static synchronized void commitParticipating(
            EntityPlayer player, CastPaymentReservation reservation) {
        requireActive(reservation);
        if (!isValid(player, reservation)) {
            throw new IllegalStateException("Participating payment claim was invalidated.");
        }
        ManaService.commitParticipatingCastPayment(player, reservation.getManaReservation(),
                reservation.getPaidHunger(), reservation.getPolicy().getMinimumHunger(), () -> {
                    releaseHungerClaim(player, reservation.getPaidHunger());
                    reservation.resolveCommitted();
                    record(player, reservation.getPolicy(), reservation.getPlan(),
                            reservation.getHand(), reservation.getSpellId(), "committed");
                });
    }

    public static synchronized void release(
            EntityPlayer player, CastPaymentReservation reservation
    ) {
        if (reservation == null || !reservation.isActive()) {
            return;
        }
        ManaService.releaseReservation(reservation.getManaReservation());
        releaseHungerClaim(player, reservation.getPaidHunger());
        reservation.resolve();
        record(player, reservation.getPolicy(), reservation.getPlan(),
                reservation.getHand(), reservation.getSpellId(), "released");
    }

    public static synchronized int getReservedHunger(EntityPlayer player) {
        ManaService.requireState(player);
        return reservedHunger(player);
    }

    public static synchronized void clear(EntityPlayer player) {
        if (player != null) {
            RESERVED_HUNGER.remove(player.getUniqueID());
            LAST_PAYMENT.remove(player.getUniqueID());
        }
    }

    public static synchronized void recordCreative(
            EntityPlayer player,
            int cost,
            DemonicSealPolicy policy,
            boolean continuous,
            EnumHand hand,
            ResourceLocation spellId,
            boolean successful
    ) {
        CastPaymentPlan plan = CastPaymentPlan.free(cost);
        record(player, policy, plan, hand, spellId,
                successful ? "creative_success" : "creative_failed");
    }

    public static synchronized void clearLastPayment(EntityPlayer player) {
        if (player != null) {
            LAST_PAYMENT.remove(player.getUniqueID());
        }
    }

    public static synchronized String describeLastPayment(EntityPlayer player) {
        if (player == null) {
            throw new NullPointerException("player");
        }
        PaymentRecord record = LAST_PAYMENT.get(player.getUniqueID());
        return record == null ? "none" : record.describe();
    }

    private static CastPaymentPlan plan(
            EntityPlayer player,
            int cost,
            DemonicSealPolicy policy,
            boolean continuous,
            int reservedHunger
    ) {
        return CastPaymentPlan.calculate(
                cost,
                ManaService.getAvailableMana(player),
                player.getFoodStats().getFoodLevel(),
                reservedHunger,
                policy,
                continuous
        );
    }

    private static int reservedHunger(EntityPlayer player) {
        Integer reserved = RESERVED_HUNGER.get(player.getUniqueID());
        return reserved == null ? 0 : reserved;
    }

    private static void releaseHungerClaim(EntityPlayer player, int amount) {
        if (amount == 0) {
            return;
        }
        int remaining = reservedHunger(player) - amount;
        if (remaining < 0) {
            throw new IllegalStateException("Reserved hunger accounting was corrupted.");
        }
        if (remaining == 0) {
            RESERVED_HUNGER.remove(player.getUniqueID());
        } else {
            RESERVED_HUNGER.put(player.getUniqueID(), remaining);
        }
    }

    private static int saturatedAdd(int first, int second) {
        long sum = (long) first + (long) second;
        return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    private static void requireActive(CastPaymentReservation reservation) {
        if (reservation == null || !reservation.isActive()) {
            throw new IllegalStateException("No active cast payment reservation.");
        }
    }

    private static void record(
            EntityPlayer player,
            DemonicSealPolicy policy,
            CastPaymentPlan plan,
            EnumHand hand,
            ResourceLocation spellId,
            String outcome
    ) {
        LAST_PAYMENT.put(
                player.getUniqueID(), new PaymentRecord(
                        policy, plan, hand, spellId, outcome
                )
        );
    }

    private static final class PaymentRecord {
        private final DemonicSealPolicy policy;
        private final CastPaymentPlan plan;
        private final EnumHand hand;
        private final ResourceLocation spellId;
        private final String outcome;

        private PaymentRecord(
                DemonicSealPolicy policy,
                CastPaymentPlan plan,
                EnumHand hand,
                ResourceLocation spellId,
                String outcome
        ) {
            this.policy = policy;
            this.plan = plan;
            this.hand = hand;
            this.spellId = spellId;
            this.outcome = outcome;
        }

        private String describe() {
            boolean committed = "committed".equals(outcome);
            return "outcome=" + outcome
                    + ", spell=" + spellId
                    + ", hand=" + hand
                    + ", policyActive=" + policy.isActive()
                    + ", policyRevision=" + policy.getRulesRevision()
                    + ", manaPerHunger=" + policy.getManaPerHunger()
                    + ", minimumHunger=" + policy.getMinimumHunger()
                    + ", allowContinuous=" + policy.isContinuousAllowed()
                    + ", cost=" + plan.getCost()
                    + ", affordable=" + plan.isAffordable()
                    + ", sealConsidered=" + plan.wasSealConsidered()
                    + ", plannedMana=" + plan.getMana()
                    + ", plannedHunger=" + plan.getHunger()
                    + ", paidMana=" + (committed ? plan.getMana() : 0)
                    + ", paidHunger=" + (committed ? plan.getHunger() : 0)
                    + ", usableHunger=" + plan.getUsableHunger();
        }
    }

    private static boolean equals(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }
}
