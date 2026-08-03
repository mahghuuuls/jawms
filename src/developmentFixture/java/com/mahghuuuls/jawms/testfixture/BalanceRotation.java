package com.mahghuuuls.jawms.testfixture;

import net.minecraft.util.ResourceLocation;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Frozen development-only rotation inputs for IMP-014 measurements. */
public final class BalanceRotation {

    private static final Map<String, BalanceRotation> ROTATIONS;

    static {
        Map<String, BalanceRotation> rotations = new LinkedHashMap<>();
        add(rotations, new BalanceRotation(
                "low_novice",
                "no_gear",
                "stationary_practice_target",
                50,
                2.0D,
                40L,
                0.0D,
                100,
                1200,
                false,
                step("ebwizardry:magic_missile", "novice", 5, 5, 0)
        ));
        add(rotations, new BalanceRotation(
                "representative_fire",
                "full_pyromancer_wizard",
                "stationary_practice_target",
                100,
                4.0D,
                40L,
                40.0D,
                75,
                3000,
                true,
                step("ebwizardry:ignite", "novice", 5, 4, 0),
                step("ebwizardry:fireball", "apprentice", 10, 8, 0),
                step("ebwizardry:greater_fireball", "advanced", 20, 15, 0),
                step("ebwizardry:fire_breath", "master", 15, 11, 20),
                step("ebwizardry:firebolt", "apprentice", 10, 8, 0)
        ));
        add(rotations, new BalanceRotation(
                "aggressive_fire",
                "full_pyromancer_wizard",
                "stationary_practice_target",
                100,
                4.0D,
                40L,
                40.0D,
                70,
                1200,
                false,
                step("ebwizardry:fireball", "apprentice", 10, 8, 0),
                step("ebwizardry:greater_fireball", "advanced", 20, 15, 0),
                step("ebwizardry:flame_ray", "apprentice", 5, 4, 40),
                step("ebwizardry:meteor", "master", 100, 72, 0),
                step("ebwizardry:ignite", "novice", 5, 4, 0)
        ));
        ROTATIONS = Collections.unmodifiableMap(rotations);
    }

    private final String id;
    private final String gear;
    private final String target;
    private final int expectedMaximum;
    private final double expectedRegeneration;
    private final long expectedLockoutTicks;
    private final double expectedEfficiency;
    private final int cadenceTicks;
    private final int durationTicks;
    private final boolean waitForFlask;
    private final List<Step> steps;

    private BalanceRotation(
            String id,
            String gear,
            String target,
            int expectedMaximum,
            double expectedRegeneration,
            long expectedLockoutTicks,
            double expectedEfficiency,
            int cadenceTicks,
            int durationTicks,
            boolean waitForFlask,
            Step... steps
    ) {
        this.id = id;
        this.gear = gear;
        this.target = target;
        this.expectedMaximum = expectedMaximum;
        this.expectedRegeneration = expectedRegeneration;
        this.expectedLockoutTicks = expectedLockoutTicks;
        this.expectedEfficiency = expectedEfficiency;
        this.cadenceTicks = cadenceTicks;
        this.durationTicks = durationTicks;
        this.waitForFlask = waitForFlask;
        this.steps = Collections.unmodifiableList(Arrays.asList(steps.clone()));
    }

    private static Step step(
            String spellId,
            String tier,
            int configuredBase,
            int expectedFinalCost,
            int continuousHoldTicks
    ) {
        return new Step(
                new ResourceLocation(spellId),
                tier,
                configuredBase,
                expectedFinalCost,
                continuousHoldTicks
        );
    }

    private static void add(Map<String, BalanceRotation> rotations, BalanceRotation rotation) {
        if (rotations.put(rotation.id, rotation) != null) {
            throw new IllegalStateException("Duplicate balance rotation: " + rotation.id);
        }
    }

    public static BalanceRotation find(String id) {
        return ROTATIONS.get(id);
    }

    public static Collection<BalanceRotation> all() {
        return ROTATIONS.values();
    }

    public String getId() {
        return id;
    }

    public String getGear() {
        return gear;
    }

    public String getTarget() {
        return target;
    }

    public int getExpectedMaximum() {
        return expectedMaximum;
    }

    public double getExpectedRegeneration() {
        return expectedRegeneration;
    }

    public long getExpectedLockoutTicks() {
        return expectedLockoutTicks;
    }

    public double getExpectedEfficiency() {
        return expectedEfficiency;
    }

    public int getCadenceTicks() {
        return cadenceTicks;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public boolean isWaitForFlask() {
        return waitForFlask;
    }

    public List<Step> getSteps() {
        return steps;
    }

    public int getCueCount() {
        return (durationTicks - 1) / cadenceTicks + 1;
    }

    public Step getStep(int cueIndex) {
        return steps.get(cueIndex % steps.size());
    }

    public static final class Step {

        private final ResourceLocation spellId;
        private final String tier;
        private final int configuredBase;
        private final int expectedFinalCost;
        private final int continuousHoldTicks;

        private Step(
                ResourceLocation spellId,
                String tier,
                int configuredBase,
                int expectedFinalCost,
                int continuousHoldTicks
        ) {
            this.spellId = spellId;
            this.tier = tier;
            this.configuredBase = configuredBase;
            this.expectedFinalCost = expectedFinalCost;
            this.continuousHoldTicks = continuousHoldTicks;
        }

        public ResourceLocation getSpellId() {
            return spellId;
        }

        public String getTier() {
            return tier;
        }

        public int getConfiguredBase() {
            return configuredBase;
        }

        public int getExpectedFinalCost() {
            return expectedFinalCost;
        }

        public int getContinuousHoldTicks() {
            return continuousHoldTicks;
        }

        public boolean isContinuous() {
            return continuousHoldTicks > 0;
        }
    }
}
