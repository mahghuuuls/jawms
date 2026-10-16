package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import com.mahghuuuls.jawms.internal.modifier.ResolvedManaProfile;
import com.mahghuuuls.jawms.internal.modifier.ManaProfileEvaluation;
import com.mahghuuuls.jawms.api.ManaProfileExplanation;
import electroblob.wizardry.constants.Element;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;

public final class ManaState {

    private int currentMana;
    private Integer pendingLoadedMana;
    private int effectiveMaximum;
    private double effectiveRegeneration;
    private long effectiveLockoutTicks;
    private double globalSpellEfficiency;
    private final EnumMap<Element, Double> elementSpellEfficiency = new EnumMap<>(Element.class);
    private double globalSpellCostMultiplier = 1.0D;
    private final EnumMap<Element, Double> elementSpellCostMultiplier = new EnumMap<>(Element.class);
    private double regenerationRemainder;
    private long remainingLockoutTicks;
    private int reservedMana;
    private boolean continuousCastActive;
    private boolean continuousCastSuccessful;
    private boolean continuousCastInterrupted;
    private boolean continuousCastManaExhausted;
    private boolean regenerateDuringPostCastLockout;
    private boolean regenerateDuringContinuousCasting;
    private EnumHand continuousCastHand;
    private ResourceLocation continuousSpellId;
    private EnumHand blockedContinuousCastHand;
    private ResourceLocation blockedContinuousSpellId;
    private long lastInsufficientNoticeTick = Long.MIN_VALUE;
    private int lastInsufficientRequired = -1;
    private int lastInsufficientAvailable = -1;
    private long revision;
    private ManaProfileEvaluation acceptedProfileEvaluation;

    public ManaState() {
        GameplayRules rules = GameplayRulesManager.current();
        effectiveMaximum = rules.getBaseMaximum();
        effectiveRegeneration = rules.getBaseRegeneration();
        effectiveLockoutTicks = rules.getRegenerationLockoutTicks();
        currentMana = effectiveMaximum;
    }

    public int getCurrentMana() {
        return currentMana;
    }

    public int getMaximumMana() {
        return effectiveMaximum;
    }

    public int getAvailableMana() {
        return currentMana - reservedMana;
    }

    public double getEffectiveRegeneration() {
        return effectiveRegeneration;
    }

    public long getEffectiveLockoutTicks() {
        return effectiveLockoutTicks;
    }

    public double getSpellEfficiency(Element element) {
        Double elementValue = elementSpellEfficiency.get(element);
        double result = globalSpellEfficiency + (elementValue == null ? 0.0D : elementValue);
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    public double getGlobalSpellEfficiency() {
        return globalSpellEfficiency;
    }

    public Map<Element, Double> getElementSpellEfficiency() {
        return java.util.Collections.unmodifiableMap(new EnumMap<>(elementSpellEfficiency));
    }

    public double getSpellCostMultiplier(Element element) {
        Double elementValue = elementSpellCostMultiplier.get(element);
        return elementValue == null ? globalSpellCostMultiplier : elementValue;
    }

    public double getGlobalSpellCostMultiplier() {
        return globalSpellCostMultiplier;
    }

    public Map<Element, Double> getElementSpellCostMultipliers() {
        return java.util.Collections.unmodifiableMap(new EnumMap<>(elementSpellCostMultiplier));
    }

    public long getRevision() {
        return revision;
    }

    public int restore(int amount) {
        requireNonNegative(amount);
        int restored = Math.min(amount, getMaximumMana() - currentMana);
        if (restored > 0) {
            currentMana += restored;
            revision++;
        }
        return restored;
    }

    public int setCurrentMana(int target) {
        requireNonNegative(target);
        int boundedTarget = clamp(target);
        if (boundedTarget < reservedMana) {
            throw new IllegalStateException(
                    "Current mana cannot be set below mana reserved by an active cast."
            );
        }
        int previous = currentMana;
        currentMana = boundedTarget;
        if (currentMana != previous) {
            regenerationRemainder = 0.0D;
            revision++;
        }
        return currentMana - previous;
    }

    public int drainUpTo(int amount) {
        requireNonNegative(amount);
        int drained = Math.min(amount, getAvailableMana());
        if (drained > 0) {
            currentMana -= drained;
            revision++;
        }
        return drained;
    }

    public boolean tryConsume(int amount) {
        requireNonNegative(amount);
        if (amount > getAvailableMana()) {
            return false;
        }
        if (amount > 0) {
            currentMana -= amount;
            revision++;
        }
        return true;
    }

    ManaReservation reserve(int amount) {
        requireNonNegative(amount);
        if (amount > getAvailableMana()) {
            return null;
        }
        reservedMana += amount;
        return new ManaReservation(this, amount);
    }

    void commit(ManaReservation reservation) {
        requireActiveReservation(reservation);
        reservedMana -= reservation.getAmount();
        currentMana -= reservation.getAmount();
        reservation.resolve();
        if (reservation.getAmount() > 0) {
            revision++;
        }
    }

    void release(ManaReservation reservation) {
        requireActiveReservation(reservation);
        reservedMana -= reservation.getAmount();
        reservation.resolve();
    }

    boolean shouldNotifyInsufficient(long currentTick, int required, int available) {
        requireNonNegative(required);
        requireNonNegative(available);
        boolean valuesChanged = required != lastInsufficientRequired
                || available != lastInsufficientAvailable;
        boolean intervalElapsed = currentTick < lastInsufficientNoticeTick
                || currentTick - lastInsufficientNoticeTick >= 10L;
        if (!valuesChanged && !intervalElapsed) {
            return false;
        }
        lastInsufficientNoticeTick = currentTick;
        lastInsufficientRequired = required;
        lastInsufficientAvailable = available;
        return true;
    }

    public int regenerateTick(double manaPerSecond) {
        if (!Double.isFinite(manaPerSecond) || manaPerSecond < 0.0D) {
            throw new IllegalArgumentException("Regeneration rate must be finite and non-negative.");
        }
        if (currentMana >= getMaximumMana() || manaPerSecond == 0.0D) {
            regenerationRemainder = 0.0D;
            return 0;
        }

        double accumulated = regenerationRemainder + manaPerSecond / 20.0D;
        int wholeMana = (int) Math.floor(accumulated + 1.0E-9D);
        regenerationRemainder = accumulated - wholeMana;
        if (wholeMana <= 0) {
            return 0;
        }

        int restored = Math.min(wholeMana, getMaximumMana() - currentMana);
        currentMana += restored;
        if (currentMana >= getMaximumMana()) {
            regenerationRemainder = 0.0D;
        }
        revision++;
        return restored;
    }

    public void startRegenerationLockout(long durationTicks) {
        if (durationTicks < 0L) {
            throw new IllegalArgumentException("Lockout duration must be non-negative.");
        }
        remainingLockoutTicks = durationTicks;
    }

    public void clampLockoutAfterReload(long newDurationTicks) {
        if (newDurationTicks < 0L) {
            throw new IllegalArgumentException("Lockout duration must be non-negative.");
        }
        if (remainingLockoutTicks > newDurationTicks) {
            remainingLockoutTicks = newDurationTicks;
        }
    }

    public boolean advanceRegenerationGate() {
        boolean lockoutBlocked = false;
        if (remainingLockoutTicks > 0L) {
            remainingLockoutTicks--;
            lockoutBlocked = !regenerateDuringPostCastLockout;
        }
        boolean continuousBlocked = continuousCastActive
                && !regenerateDuringContinuousCasting;
        return !lockoutBlocked && !continuousBlocked;
    }

    public boolean isRegenerationBlocked() {
        return (remainingLockoutTicks > 0L && !regenerateDuringPostCastLockout)
                || (continuousCastActive && !regenerateDuringContinuousCasting);
    }

    public boolean isRegenerationEligible() {
        if (currentMana >= getMaximumMana() || effectiveRegeneration <= 0.0D) {
            return false;
        }
        if (remainingLockoutTicks > 0L && !regenerateDuringPostCastLockout) {
            return false;
        }
        return !continuousCastActive || regenerateDuringContinuousCasting;
    }

    public boolean canRegenerateDuringPostCastLockout() {
        return regenerateDuringPostCastLockout;
    }

    public boolean canRegenerateDuringContinuousCasting() {
        return regenerateDuringContinuousCasting;
    }

    public long getRemainingLockoutTicks() {
        return remainingLockoutTicks;
    }

    public void beginContinuousCast(EnumHand hand, ResourceLocation spellId) {
        if (hand == null) {
            throw new NullPointerException("hand");
        }
        if (continuousCastActive
                && hand == continuousCastHand
                && equals(spellId, continuousSpellId)) {
            return;
        }
        continuousCastActive = true;
        continuousCastSuccessful = false;
        continuousCastInterrupted = false;
        continuousCastHand = hand;
        continuousSpellId = spellId;
    }

    public void blockContinuousCastRetry(EnumHand hand, ResourceLocation spellId) {
        if (hand == null) {
            throw new NullPointerException("hand");
        }
        if (spellId == null) {
            throw new NullPointerException("spellId");
        }
        blockedContinuousCastHand = hand;
        blockedContinuousSpellId = spellId;
    }

    public boolean isContinuousCastRetryBlocked(EnumHand hand, ResourceLocation spellId) {
        return hand == blockedContinuousCastHand
                && equals(spellId, blockedContinuousSpellId);
    }

    public void releaseContinuousCastRetryBlock(EnumHand hand, ResourceLocation spellId) {
        if (isContinuousCastRetryBlocked(hand, spellId)) {
            blockedContinuousCastHand = null;
            blockedContinuousSpellId = null;
        }
    }

    public void markContinuousCastSuccessful(EnumHand hand, ResourceLocation spellId) {
        if (matchesContinuousCast(hand, spellId)) {
            continuousCastSuccessful = true;
        }
    }

    public void interruptContinuousCast(EnumHand hand, ResourceLocation spellId) {
        if (matchesContinuousCast(hand, spellId)) {
            continuousCastInterrupted = true;
            blockContinuousCastRetry(hand, spellId);
        }
    }

    public void exhaustContinuousCastMana(EnumHand hand, ResourceLocation spellId) {
        if (matchesContinuousCast(hand, spellId)) {
            continuousCastManaExhausted = true;
            blockContinuousCastRetry(hand, spellId);
        }
    }

    public boolean finishContinuousCast(long lockoutTicks) {
        if (lockoutTicks < 0L) {
            throw new IllegalArgumentException("Lockout duration must be non-negative.");
        }
        boolean shouldStartLockout = finishContinuousCast();
        if (shouldStartLockout) {
            startRegenerationLockout(lockoutTicks);
        }
        return shouldStartLockout;
    }

    public boolean finishContinuousCast() {
        boolean shouldStartLockout = continuousCastActive && continuousCastSuccessful;
        resetContinuousCastingState();
        return shouldStartLockout;
    }

    public boolean isContinuousCastActive() {
        return continuousCastActive;
    }

    public boolean wasContinuousCastSuccessful() {
        return continuousCastSuccessful;
    }

    public boolean wasContinuousCastInterrupted() {
        return continuousCastInterrupted;
    }

    public boolean wasContinuousCastManaExhausted() {
        return continuousCastManaExhausted;
    }

    public boolean matchesContinuousCast(EnumHand hand, ResourceLocation spellId) {
        return continuousCastActive
                && hand == continuousCastHand
                && equals(spellId, continuousSpellId);
    }

    public void clearTransientLifecycleState() {
        regenerationRemainder = 0.0D;
        remainingLockoutTicks = 0L;
        resetTransientCastingState();
        acceptedProfileEvaluation = null;
    }

    public boolean applyEffectiveStats(int maximumMana, double regenerationPerSecond) {
        return applyResolvedProfile(new ResolvedManaProfile(
                maximumMana,
                regenerationPerSecond,
                effectiveLockoutTicks / 20.0D,
                effectiveLockoutTicks,
                globalSpellEfficiency,
                elementSpellEfficiency,
                globalSpellCostMultiplier,
                elementSpellCostMultiplier,
                regenerateDuringPostCastLockout,
                regenerateDuringContinuousCasting
        ));
    }

    public boolean applyResolvedProfile(ResolvedManaProfile profile) {
        if (profile == null) {
            throw new NullPointerException("profile");
        }
        int maximumMana = profile.getMaximumMana();
        double regenerationPerSecond = profile.getRegeneration();
        if (maximumMana < 1) {
            throw new IllegalArgumentException("Effective maximum must be positive.");
        }
        if (!Double.isFinite(regenerationPerSecond) || regenerationPerSecond < 0.0D) {
            throw new IllegalArgumentException("Effective regeneration must be finite and non-negative.");
        }
        acceptedProfileEvaluation = null;
        boolean changed = pendingLoadedMana != null
                || maximumMana != effectiveMaximum
                || Double.compare(regenerationPerSecond, effectiveRegeneration) != 0
                || effectiveLockoutTicks != profile.getLockoutTicks()
                || regenerateDuringPostCastLockout
                != profile.canRegenerateDuringPostCastLockout()
                || regenerateDuringContinuousCasting
                != profile.canRegenerateDuringContinuousCasting()
                || Double.compare(globalSpellEfficiency, profile.getGlobalSpellEfficiency()) != 0
                || !elementSpellEfficiency.equals(profile.getElementSpellEfficiency())
                || Double.compare(globalSpellCostMultiplier,
                profile.getGlobalSpellCostMultiplier()) != 0
                || !elementSpellCostMultiplier.equals(
                profile.getElementSpellCostMultipliers());
        if (!changed) {
            return false;
        }
        effectiveMaximum = maximumMana;
        effectiveRegeneration = regenerationPerSecond;
        effectiveLockoutTicks = profile.getLockoutTicks();
        globalSpellEfficiency = profile.getGlobalSpellEfficiency();
        elementSpellEfficiency.clear();
        for (Map.Entry<Element, Double> entry : profile.getElementSpellEfficiency().entrySet()) {
            elementSpellEfficiency.put(entry.getKey(), entry.getValue());
        }
        globalSpellCostMultiplier = profile.getGlobalSpellCostMultiplier();
        elementSpellCostMultiplier.clear();
        elementSpellCostMultiplier.putAll(profile.getElementSpellCostMultipliers());
        regenerateDuringPostCastLockout = profile.canRegenerateDuringPostCastLockout();
        regenerateDuringContinuousCasting = profile.canRegenerateDuringContinuousCasting();
        int intrinsicMana = pendingLoadedMana == null ? currentMana : pendingLoadedMana;
        pendingLoadedMana = null;
        currentMana = clamp(intrinsicMana);
        if (currentMana >= effectiveMaximum) {
            regenerationRemainder = 0.0D;
        }
        revision++;
        return true;
    }

    public boolean applyProfileEvaluation(ManaProfileEvaluation evaluation) {
        if (evaluation == null) {
            throw new NullPointerException("evaluation");
        }
        boolean changed = applyResolvedProfile(evaluation.getProfile());
        acceptedProfileEvaluation = evaluation;
        return changed;
    }

    public ManaProfileExplanation explainAcceptedProfile() {
        if (acceptedProfileEvaluation == null) {
            throw new IllegalStateException(
                    "No authoritative JAWMS mana-profile evaluation has been accepted yet."
            );
        }
        return acceptedProfileEvaluation.toPublicExplanation();
    }

    public void advanceRevision() {
        revision++;
    }

    public void copyFrom(ManaState original) {
        pendingLoadedMana = null;
        effectiveMaximum = original.effectiveMaximum;
        effectiveRegeneration = original.effectiveRegeneration;
        effectiveLockoutTicks = original.effectiveLockoutTicks;
        globalSpellEfficiency = original.globalSpellEfficiency;
        elementSpellEfficiency.clear();
        elementSpellEfficiency.putAll(original.elementSpellEfficiency);
        globalSpellCostMultiplier = original.globalSpellCostMultiplier;
        elementSpellCostMultiplier.clear();
        elementSpellCostMultiplier.putAll(original.elementSpellCostMultiplier);
        regenerateDuringPostCastLockout = original.regenerateDuringPostCastLockout;
        regenerateDuringContinuousCasting = original.regenerateDuringContinuousCasting;
        currentMana = clamp(original.currentMana);
        regenerationRemainder = 0.0D;
        remainingLockoutTicks = 0L;
        resetTransientCastingState();
        revision = original.revision + 1L;
        acceptedProfileEvaluation = null;
    }

    public NBTTagCompound serializeIntrinsicState() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("currentMana", pendingLoadedMana == null ? currentMana : pendingLoadedMana);
        return tag;
    }

    public void deserializeIntrinsicState(NBTTagCompound tag) {
        acceptedProfileEvaluation = null;
        if (tag.hasKey("currentMana", 3)) {
            pendingLoadedMana = Math.max(0, tag.getInteger("currentMana"));
            currentMana = clamp(pendingLoadedMana);
        } else {
            pendingLoadedMana = null;
            currentMana = getMaximumMana();
        }
        regenerationRemainder = 0.0D;
        remainingLockoutTicks = 0L;
        resetTransientCastingState();
        revision++;
    }

    boolean hasPendingLoadedMana() {
        return pendingLoadedMana != null;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(value, getMaximumMana()));
    }

    private static void requireNonNegative(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Mana amount must not be negative.");
        }
    }

    boolean hasPayableReservation(ManaReservation reservation) {
        return reservation != null && reservation.belongsTo(this) && reservation.isActive()
                && reservation.getAmount() <= reservedMana && currentMana >= reservedMana;
    }

    private void requireActiveReservation(ManaReservation reservation) {
        if (reservation == null || !reservation.belongsTo(this) || !reservation.isActive()) {
            throw new IllegalStateException("Mana reservation is not active for this state.");
        }
        if (reservation.getAmount() > reservedMana) {
            throw new IllegalStateException("Mana reservation accounting is inconsistent.");
        }
    }

    private void resetTransientCastingState() {
        reservedMana = 0;
        lastInsufficientNoticeTick = Long.MIN_VALUE;
        lastInsufficientRequired = -1;
        lastInsufficientAvailable = -1;
        resetContinuousCastingState();
        blockedContinuousCastHand = null;
        blockedContinuousSpellId = null;
    }

    private void resetContinuousCastingState() {
        continuousCastActive = false;
        continuousCastSuccessful = false;
        continuousCastInterrupted = false;
        continuousCastManaExhausted = false;
        continuousCastHand = null;
        continuousSpellId = null;
    }

    private static boolean equals(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }
}
