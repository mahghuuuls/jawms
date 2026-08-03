package com.mahghuuuls.jawms.testfixture;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Pure tick accounting oracle for the frozen balance rotations. */
public final class BalanceSimulator {

    private BalanceSimulator() {
    }

    public static Result simulate(BalanceRotation rotation) {
        int mana = rotation.getExpectedMaximum();
        int spent = 0;
        int regenerated = 0;
        int successfulActions = 0;
        long remainingLockout = 0L;
        int continuousUntil = -1;
        double regenerationRemainder = 0.0D;
        List<Portion> pendingPortions = new ArrayList<>();

        for (int tick = 0; tick < rotation.getDurationTicks(); tick++) {
            if (tick == continuousUntil) {
                continuousUntil = -1;
                remainingLockout = rotation.getExpectedLockoutTicks();
            }

            Iterator<Portion> portions = pendingPortions.iterator();
            while (portions.hasNext()) {
                Portion portion = portions.next();
                if (portion.tick != tick) {
                    continue;
                }
                if (portion.cost > mana) {
                    return Result.depleted(tick, successfulActions, mana, spent, regenerated);
                }
                mana -= portion.cost;
                spent += portion.cost;
                portions.remove();
            }

            if (tick % rotation.getCadenceTicks() == 0) {
                int cue = tick / rotation.getCadenceTicks();
                BalanceRotation.Step step = rotation.getStep(cue);
                if (step.isContinuous()) {
                    int firstPortion = firstHalf(step.getExpectedFinalCost());
                    if (firstPortion > mana) {
                        return Result.depleted(tick, successfulActions, mana, spent, regenerated);
                    }
                    mana -= firstPortion;
                    spent += firstPortion;
                    for (int offset = 10; offset < step.getContinuousHoldTicks(); offset += 10) {
                        int portionCost = offset % 20 == 0
                                ? firstHalf(step.getExpectedFinalCost())
                                : secondHalf(step.getExpectedFinalCost());
                        pendingPortions.add(new Portion(tick + offset, portionCost));
                    }
                    continuousUntil = tick + step.getContinuousHoldTicks();
                } else {
                    if (step.getExpectedFinalCost() > mana) {
                        return Result.depleted(tick, successfulActions, mana, spent, regenerated);
                    }
                    mana -= step.getExpectedFinalCost();
                    spent += step.getExpectedFinalCost();
                    remainingLockout = rotation.getExpectedLockoutTicks();
                }
                successfulActions++;
            }

            boolean blocked = false;
            if (remainingLockout > 0L) {
                remainingLockout--;
                blocked = true;
            }
            if (continuousUntil > tick) {
                blocked = true;
            }
            if (!blocked && mana < rotation.getExpectedMaximum()) {
                double accumulated = regenerationRemainder
                        + rotation.getExpectedRegeneration() / 20.0D;
                int whole = (int) Math.floor(accumulated + 1.0E-9D);
                regenerationRemainder = accumulated - whole;
                if (whole > 0) {
                    int restored = Math.min(whole, rotation.getExpectedMaximum() - mana);
                    mana += restored;
                    regenerated += restored;
                    if (mana == rotation.getExpectedMaximum()) {
                        regenerationRemainder = 0.0D;
                    }
                }
            }
        }

        return Result.completed(
                rotation.getDurationTicks(),
                successfulActions,
                mana,
                spent,
                regenerated
        );
    }

    private static int firstHalf(int perSecondCost) {
        return perSecondCost / 2 + perSecondCost % 2;
    }

    private static int secondHalf(int perSecondCost) {
        return perSecondCost / 2;
    }

    private static final class Portion {

        private final int tick;
        private final int cost;

        private Portion(int tick, int cost) {
            this.tick = tick;
            this.cost = cost;
        }
    }

    public static final class Result {

        private final int elapsedTicks;
        private final int firstUnaffordableTick;
        private final int successfulActions;
        private final int endingMana;
        private final int spentMana;
        private final int regeneratedMana;

        private Result(
                int elapsedTicks,
                int firstUnaffordableTick,
                int successfulActions,
                int endingMana,
                int spentMana,
                int regeneratedMana
        ) {
            this.elapsedTicks = elapsedTicks;
            this.firstUnaffordableTick = firstUnaffordableTick;
            this.successfulActions = successfulActions;
            this.endingMana = endingMana;
            this.spentMana = spentMana;
            this.regeneratedMana = regeneratedMana;
        }

        private static Result depleted(
                int tick,
                int successfulActions,
                int endingMana,
                int spentMana,
                int regeneratedMana
        ) {
            return new Result(
                    tick,
                    tick,
                    successfulActions,
                    endingMana,
                    spentMana,
                    regeneratedMana
            );
        }

        private static Result completed(
                int elapsedTicks,
                int successfulActions,
                int endingMana,
                int spentMana,
                int regeneratedMana
        ) {
            return new Result(
                    elapsedTicks,
                    -1,
                    successfulActions,
                    endingMana,
                    spentMana,
                    regeneratedMana
            );
        }

        public int getElapsedTicks() {
            return elapsedTicks;
        }

        public int getFirstUnaffordableTick() {
            return firstUnaffordableTick;
        }

        public int getSuccessfulActions() {
            return successfulActions;
        }

        public int getEndingMana() {
            return endingMana;
        }

        public int getSpentMana() {
            return spentMana;
        }

        public int getRegeneratedMana() {
            return regeneratedMana;
        }
    }
}
