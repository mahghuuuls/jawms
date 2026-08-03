package com.mahghuuuls.jawms.testfixture;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaCauses;
import com.mahghuuuls.jawms.api.ManaPublicState;
import com.mahghuuuls.jawms.api.ManaStateChangeEvent;
import com.mahghuuuls.jawms.internal.casting.SpellCostService;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.item.ItemManaFlask;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Operator-controlled development-only live balance measurement recorder. */
public final class BalanceRecorder {

    private static final double EPSILON = 1.0E-9D;

    private final Map<UUID, ActiveRun> activeRuns = new HashMap<>();

    public void clear() {
        activeRuns.clear();
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        activeRuns.remove(player.getUniqueID());
        ManaTestFixture.clearPlayerState(player);
    }

    public void setup(EntityPlayerMP player, BalanceRotation rotation) throws CommandException {
        ItemStack stack = player.getHeldItemMainhand();
        if (!(stack.getItem() instanceof ItemWand)) {
            throw new CommandException("Hold the intended Wizardry wand in the main hand.");
        }
        if (rotation.getSteps().size() > ItemWand.BASE_SPELL_SLOTS) {
            throw new CommandException("Rotation has more spells than the wand's base slots.");
        }
        Spell[] spells = new Spell[ItemWand.BASE_SPELL_SLOTS];
        for (int index = 0; index < rotation.getSteps().size(); index++) {
            spells[index] = requireSpell(rotation.getSteps().get(index).getSpellId());
        }
        WandHelper.setSpells(stack, spells);
        WandHelper.selectSpell(stack, 0);
        player.inventoryContainer.detectAndSendChanges();
        player.sendMessage(new TextComponentString(
                "JAWMS balance setup " + rotation.getId()
                        + ": " + rotation.getSteps().size()
                        + " spells installed in frozen order."
        ));
    }

    public void start(EntityPlayerMP player, BalanceRotation rotation, int repetition)
            throws CommandException {
        if (repetition < 1 || repetition > 3) {
            throw new CommandException("Repetition must be 1, 2, or 3.");
        }
        if (activeRuns.containsKey(player.getUniqueID())) {
            throw new CommandException("A balance run is already active for this player.");
        }
        validateStart(player, rotation);
        ManaPublicState state = ManaApi.getManaService().getState(player);
        long startTick = player.world.getTotalWorldTime();
        ActiveRun run = new ActiveRun(
                rotation,
                repetition,
                startTick,
                state,
                countFlasks(player),
                armorDescription(player)
        );
        activeRuns.put(player.getUniqueID(), run);
        announceCue(player, run, 0);
        run.nextCueIndex = 1;
        log("run_start", run,
                "startMana=" + state.getCurrentMana()
                        + ",maximum=" + state.getMaximumMana()
                        + ",regeneration=" + decimal(state.getEffectiveRegeneration())
                        + ",lockoutTicks=" + state.getEffectiveLockoutTicks()
                        + ",postCastPermission=" + state.canRegenerateDuringPostCastLockout()
                        + ",continuousPermission=" + state.canRegenerateDuringContinuousCasting()
                        + ",rulesRevision=" + GameplayRulesManager.current().getRevision()
                        + ",gear=" + run.armor
                        + ",target=" + rotation.getTarget()
                        + ",initialFlasks=" + run.initialFlaskCount
        );
        player.sendMessage(new TextComponentString(
                "JAWMS balance run started: " + run.runId
                        + ". Follow action-bar cues; record hits/misses with /jawms-balance note."
        ));
    }

    public void note(EntityPlayerMP player, String kind, int amount) throws CommandException {
        ActiveRun run = requireRun(player);
        if (amount < 1) {
            throw new CommandException("Note amount must be positive.");
        }
        switch (kind) {
            case "hit":
                run.hits += amount;
                break;
            case "miss":
                run.misses += amount;
                break;
            case "failure":
                run.ownerReportedFailures += amount;
                break;
            case "downtime":
                run.downtimeTicks += amount;
                break;
            default:
                throw new CommandException("Expected hit, miss, failure, or downtime.");
        }
        log("owner_note", run, "kind=" + kind + ",amount=" + amount);
        player.sendMessage(new TextComponentString(
                "JAWMS balance note recorded: " + kind + "=" + amount + "."
        ));
    }

    public void status(EntityPlayerMP player) throws CommandException {
        ActiveRun run = requireRun(player);
        ManaPublicState state = ManaApi.getManaService().getState(player);
        long elapsed = player.world.getTotalWorldTime() - run.startTick;
        player.sendMessage(new TextComponentString(
                "JAWMS balance " + run.runId
                        + ": elapsed=" + elapsed + " ticks"
                        + ", mana=" + state.getCurrentMana() + "/" + state.getMaximumMana()
                        + ", cues=" + run.nextCueIndex + "/" + run.rotation.getCueCount()
                        + ", attempts=" + run.attempts
                        + ", successes=" + run.successfulCasts
                        + ", firstRequiredTick=" + run.firstRequiredTick + "."
        ));
    }

    public void profile(EntityPlayerMP player) throws CommandException {
        IManaService service = ManaApi.getManaService();
        ManaPublicState state = service.getState(player);
        Spell fire = requireSpell(new ResourceLocation("ebwizardry:ignite"));
        double fireEfficiency = service.getApplicableSpellEfficiency(player, fire);
        String fields = "player=" + player.getUniqueID()
                + ",maximum=" + state.getMaximumMana()
                + ",current=" + state.getCurrentMana()
                + ",regeneration=" + decimal(state.getEffectiveRegeneration())
                + ",lockoutTicks=" + state.getEffectiveLockoutTicks()
                + ",fireEfficiency=" + decimal(fireEfficiency)
                + ",percentBoost="
                + ManaTestFixture.isBalancePercentBoostEnabled(player)
                + ",gear=" + armorDescription(player)
                + ",rulesRevision=" + GameplayRulesManager.current().getRevision();
        JustAnotherWizardryManaSystemMod.LOGGER.info(
                "JAWMS balance: event=profile, {}.", fields
        );
        player.sendMessage(new TextComponentString("JAWMS balance profile: " + fields + "."));
    }

    public void stop(EntityPlayerMP player, String reason) throws CommandException {
        if (!"completed".equals(reason)
                && !"depleted".equals(reason)
                && !"aborted".equals(reason)) {
            throw new CommandException("Expected completed, depleted, or aborted.");
        }
        ActiveRun run = requireRun(player);
        finish(player, run, reason);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void guardDuplicateCueCast(SpellCastEvent.Pre event) {
        if (!(event.getCaster() instanceof EntityPlayerMP)
                || event.getCaster().world.isRemote
                || event.getSource() != SpellCastEvent.Source.WAND) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getCaster();
        ActiveRun run = activeRuns.get(player.getUniqueID());
        if (run == null || (!run.activeCueSucceeded && run.firstRequiredTick < 0L)) {
            return;
        }
        event.setCanceled(true);
        run.duplicateAttemptsBlocked++;
        log("duplicate_attempt_blocked", run,
                "cue=" + run.activeCueIndex
                        + ",spell=" + event.getSpell().getRegistryName()
        );
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onSpellPre(SpellCastEvent.Pre event) {
        if (!(event.getCaster() instanceof EntityPlayerMP)
                || event.getCaster().world.isRemote
                || event.getSource() != SpellCastEvent.Source.WAND) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getCaster();
        ActiveRun run = activeRuns.get(player.getUniqueID());
        if (run == null) {
            return;
        }
        if (event.isCanceled()
                && (run.activeCueSucceeded || run.firstRequiredTick >= 0L)) {
            return;
        }
        Spell spell = event.getSpell();
        ManaPublicState state = ManaApi.getManaService().getState(player);
        GameplayRules rules = GameplayRulesManager.current();
        int cueIndex = run.activeCueIndex;
        BalanceRotation.Step expected = run.rotation.getStep(Math.max(0, cueIndex));
        int finalCost = SpellCostService.getFinalCost(player, spell, event.getModifiers());
        int requiredCost = requiredCostForAttempt(finalCost, spell.isContinuous);
        double configured = SpellCostService.getConfiguredBaseCost(rules, spell);
        double efficiency = ManaApi.getManaService()
                .getApplicableSpellEfficiency(player, spell);
        double profileMultiplier = ManaApi.getManaService()
                .getApplicableSpellCostMultiplier(player, spell);
        double retainedFactor = SpellCostService.retainedContextualFactor(
                event.getModifiers().get(SpellModifiers.COST));
        long elapsed = player.world.getTotalWorldTime() - run.startTick;
        long scheduled = Math.max(0, cueIndex) * (long) run.rotation.getCadenceTicks();
        boolean expectedSpell = spell.getRegistryName().equals(expected.getSpellId());
        run.attempts++;
        if (!expectedSpell) {
            run.wrongSpellAttempts++;
        }
        boolean inputsMatch = Math.abs(configured - expected.getConfiguredBase()) <= EPSILON
                && Math.abs(retainedFactor - 1.0D) <= EPSILON
                && Math.abs(profileMultiplier - 1.0D) <= EPSILON
                && Math.abs(efficiency - run.rotation.getExpectedEfficiency()) <= EPSILON
                && finalCost == expected.getExpectedFinalCost();
        if (expectedSpell && !inputsMatch) {
            run.runtimeInputMismatches++;
        }
        if (event.isCanceled()) {
            run.canceledAttempts++;
        }
        run.pendingAttempt = new Attempt(
                cueIndex,
                spell.getRegistryName(),
                finalCost,
                spell.isContinuous,
                expectedSpell && inputsMatch
        );
        log("cast_attempt", run,
                "cue=" + cueIndex
                        + ",scheduledTick=" + scheduled
                        + ",elapsedTick=" + elapsed
                        + ",cadenceErrorTicks=" + (elapsed - scheduled)
                        + ",expectedSpell=" + expected.getSpellId()
                        + ",actualSpell=" + spell.getRegistryName()
                        + ",tier=" + spell.getTier().name().toLowerCase(Locale.ROOT)
                        + ",configuredBase=" + decimal(configured)
                        + ",retainedFactor=" + decimal(retainedFactor)
                        + ",profileMultiplier=" + decimal(profileMultiplier)
                        + ",efficiency=" + decimal(efficiency)
                        + ",finalCost=" + finalCost
                        + ",requiredCost=" + requiredCost
                        + ",available=" + state.getCurrentMana()
                        + ",continuous=" + spell.isContinuous
                        + ",cooldownTicks=" + spell.getCooldown()
                        + ",canceled=" + event.isCanceled()
        );
        if (!event.isCanceled()
                && expectedSpell
                && inputsMatch
                && requiredCost > state.getCurrentMana()
                && run.firstRequiredTick < 0L) {
            run.firstRequiredTick = elapsed;
            log("first_unaffordable", run,
                    "cue=" + cueIndex
                            + ",spell=" + spell.getRegistryName()
                            + ",required=" + requiredCost
                            + ",available=" + state.getCurrentMana()
            );
            player.sendStatusMessage(new TextComponentString(
                    run.rotation.isWaitForFlask()
                            ? "JAWMS: FLASK REQUIRED — use one medium flask"
                            : "JAWMS: DEPLETED — rotation complete"
            ), true);
            if (!run.rotation.isWaitForFlask()) {
                finish(player, run, "first_unaffordable");
            }
        }
    }

    @SubscribeEvent
    public void onSpellPost(SpellCastEvent.Post event) {
        if (!(event.getCaster() instanceof EntityPlayerMP)
                || event.getCaster().world.isRemote
                || event.getSource() != SpellCastEvent.Source.WAND) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getCaster();
        ActiveRun run = activeRuns.get(player.getUniqueID());
        if (run == null) {
            return;
        }
        Attempt attempt = run.pendingAttempt;
        if (attempt == null || !event.getSpell().getRegistryName().equals(attempt.spellId)) {
            run.unmatchedSuccessfulCasts++;
            log("cast_success_unmatched", run, "spell=" + event.getSpell().getRegistryName());
            return;
        }
        if (attempt.validForCue) {
            run.successfulCasts++;
        }
        if (attempt.validForCue && attempt.cueIndex == run.activeCueIndex) {
            run.activeCueSucceeded = true;
        }
        if (attempt.validForCue && attempt.continuous) {
            BalanceRotation.Step step = run.rotation.getStep(Math.max(0, attempt.cueIndex));
            run.continuousStartTick = player.world.getTotalWorldTime();
            run.continuousExpectedHoldTicks = step.getContinuousHoldTicks();
            run.releaseCueTick = player.world.getTotalWorldTime()
                    + step.getContinuousHoldTicks();
            run.releaseCueSent = false;
        }
        log("cast_success", run,
                "cue=" + attempt.cueIndex
                        + ",spell=" + attempt.spellId
                        + ",finalCost=" + attempt.finalCost
                        + ",continuous=" + attempt.continuous
                        + ",validForCue=" + attempt.validForCue
        );
        run.pendingAttempt = null;
    }

    @SubscribeEvent
    public void onSpellFinish(SpellCastEvent.Finish event) {
        if (!(event.getCaster() instanceof EntityPlayerMP)
                || event.getCaster().world.isRemote
                || event.getSource() != SpellCastEvent.Source.WAND) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getCaster();
        ActiveRun run = activeRuns.get(player.getUniqueID());
        if (run == null || run.continuousStartTick < 0L) {
            return;
        }
        long now = player.world.getTotalWorldTime();
        long actualHoldTicks = now - run.continuousStartTick;
        run.continuousFinishes++;
        boolean early = actualHoldTicks + 1L < run.continuousExpectedHoldTicks;
        if (early) {
            run.continuousEarlyStops++;
        }
        log("continuous_finish", run,
                "spell=" + event.getSpell().getRegistryName()
                        + ",actualHoldTicks=" + actualHoldTicks
                        + ",expectedHoldTicks=" + run.continuousExpectedHoldTicks
                        + ",early=" + early
        );
        run.releaseCueTick = -1L;
        run.continuousStartTick = -1L;
        if (early
                && ManaApi.getManaService().getCurrentMana(player) <= 0
                && run.firstRequiredTick < 0L) {
            run.firstRequiredTick = now - run.startTick;
            log("first_unaffordable_continuous", run,
                    "elapsedTick=" + run.firstRequiredTick
                            + ",available=" + ManaApi.getManaService().getCurrentMana(player)
            );
            if (!run.rotation.isWaitForFlask()) {
                finish(player, run, "continuous_exhaustion");
            }
        }
    }

    @SubscribeEvent
    public void onManaStateChange(ManaStateChangeEvent event) {
        if (!(event.getPlayer() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getPlayer();
        ActiveRun run = activeRuns.get(player.getUniqueID());
        if (run == null) {
            return;
        }
        int delta = event.getActualManaDelta();
        run.totalEventDelta += delta;
        if (ManaCauses.SPELL_COST.equals(event.getCause())) {
            run.spellSpent += -delta;
            run.spellCostTransactions++;
            if (event.getOldState().isContinuousCastActive()
                    || event.getNewState().isContinuousCastActive()) {
                run.continuousPortions++;
            }
            log("mana_spell_cost", run,
                    "delta=" + delta
                            + ",current=" + event.getNewState().getCurrentMana()
                            + ",transactions=" + run.spellCostTransactions
                            + ",continuousPortions=" + run.continuousPortions
            );
        } else if (ManaCauses.REGENERATION.equals(event.getCause())) {
            run.regenerated += Math.max(0, delta);
        } else if (ManaCauses.FLASK.equals(event.getCause())) {
            run.flaskRestored += Math.max(0, delta);
            if (run.firstFlaskTick < 0L) {
                run.firstFlaskTick = player.world.getTotalWorldTime() - run.startTick;
            }
            log("flask", run,
                    "delta=" + delta
                            + ",current=" + event.getNewState().getCurrentMana()
                            + ",firstFlaskTick=" + run.firstFlaskTick
            );
            run.finishAfterFlask = run.rotation.isWaitForFlask();
            run.flaskEventWorldTick = player.world.getTotalWorldTime();
        } else {
            run.otherManaDelta += delta;
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof EntityPlayerMP)
                || event.player.world.isRemote) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        ActiveRun run = activeRuns.get(player.getUniqueID());
        if (run == null) {
            return;
        }
        long now = player.world.getTotalWorldTime();
        long elapsed = now - run.startTick;
        if (run.finishAfterFlask && now > run.flaskEventWorldTick) {
            finish(player, run, "first_flask");
            return;
        }
        if (run.releaseCueTick >= 0L && !run.releaseCueSent && now >= run.releaseCueTick) {
            player.sendStatusMessage(new TextComponentString(
                    "JAWMS: RELEASE continuous spell now"
            ), true);
            run.releaseCueSent = true;
            log("release_cue", run, "elapsedTick=" + elapsed);
        }
        if (run.rotation.isWaitForFlask() && run.firstRequiredTick >= 0L) {
            return;
        }
        while (run.nextCueIndex < run.rotation.getCueCount()
                && elapsed >= run.nextCueIndex * (long) run.rotation.getCadenceTicks()) {
            closeMissedCue(run);
            announceCue(player, run, run.nextCueIndex);
            run.nextCueIndex++;
        }
        if (elapsed >= run.rotation.getDurationTicks()) {
            closeMissedCue(run);
            finish(player, run,
                    run.skippedCues == 0 ? "planned_window_complete" : "timeout_with_skips");
        }
    }

    private void validateStart(EntityPlayerMP player, BalanceRotation rotation)
            throws CommandException {
        IManaService service = ManaApi.getManaService();
        ManaPublicState state = service.getState(player);
        if (state.getCurrentMana() != state.getMaximumMana()) {
            throw new CommandException("Start at full mana: expected "
                    + state.getMaximumMana() + ", found " + state.getCurrentMana() + ".");
        }
        if (state.getMaximumMana() != rotation.getExpectedMaximum()
                || Math.abs(state.getEffectiveRegeneration()
                - rotation.getExpectedRegeneration()) > EPSILON
                || state.getEffectiveLockoutTicks() != rotation.getExpectedLockoutTicks()
                || state.getRemainingLockoutTicks() != 0L
                || state.canRegenerateDuringPostCastLockout()
                || state.canRegenerateDuringContinuousCasting()
                || state.isContinuousCastActive()) {
            throw new CommandException("Profile mismatch for " + rotation.getId()
                    + ": expected max/regen/lockout="
                    + rotation.getExpectedMaximum() + "/"
                    + rotation.getExpectedRegeneration() + "/"
                    + rotation.getExpectedLockoutTicks()
                    + " with closed gates and zero remaining lockout; found " + state + ".");
        }
        GameplayRules rules = GameplayRulesManager.current();
        for (BalanceRotation.Step step : rotation.getSteps()) {
            Spell spell = requireSpell(step.getSpellId());
            double configured = SpellCostService.getConfiguredBaseCost(rules, spell);
            double efficiency = service.getApplicableSpellEfficiency(player, spell);
            double multiplier = service.getApplicableSpellCostMultiplier(player, spell);
            int finalCost = SpellCostService.getFinalCost(player, spell, new SpellModifiers());
            if (Math.abs(configured - step.getConfiguredBase()) > EPSILON
                    || Math.abs(efficiency - rotation.getExpectedEfficiency()) > EPSILON
                    || Math.abs(multiplier - 1.0D) > EPSILON
                    || finalCost != step.getExpectedFinalCost()
                    || spell.isContinuous != step.isContinuous()) {
                throw new CommandException("Spell input mismatch for " + step.getSpellId()
                        + ": configured=" + configured
                        + ", efficiency=" + efficiency
                        + ", multiplier=" + multiplier
                        + ", final=" + finalCost
                        + ", continuous=" + spell.isContinuous + ".");
            }
        }
    }

    private void announceCue(EntityPlayerMP player, ActiveRun run, int cueIndex) {
        run.activeCueIndex = cueIndex;
        run.activeCueSucceeded = false;
        BalanceRotation.Step step = run.rotation.getStep(cueIndex);
        int selectedSlot = cueIndex % run.rotation.getSteps().size();
        ItemStack held = player.getHeldItemMainhand();
        if (held.getItem() instanceof ItemWand) {
            WandHelper.selectSpell(held, selectedSlot);
            player.inventoryContainer.detectAndSendChanges();
        }
        String suffix = step.isContinuous()
                ? " — HOLD " + step.getContinuousHoldTicks() + " ticks"
                : " — CAST NOW";
        player.sendStatusMessage(new TextComponentString(
                "JAWMS " + run.rotation.getId() + " #" + (cueIndex + 1)
                        + ": " + step.getSpellId().getPath() + suffix
        ), true);
        log("cue", run,
                "cue=" + cueIndex
                        + ",scheduledTick="
                        + cueIndex * (long) run.rotation.getCadenceTicks()
                        + ",spell=" + step.getSpellId()
                        + ",holdTicks=" + step.getContinuousHoldTicks()
                        + ",selectedSlot=" + selectedSlot
        );
    }

    private void closeMissedCue(ActiveRun run) {
        if (run.activeCueIndex >= 0 && !run.activeCueSucceeded) {
            run.skippedCues++;
            log("cue_without_success", run, "cue=" + run.activeCueIndex);
        }
    }

    private void finish(EntityPlayerMP player, ActiveRun run, String reason) {
        if (activeRuns.remove(player.getUniqueID()) != run) {
            return;
        }
        ManaPublicState end = ManaApi.getManaService().getState(player);
        long elapsed = player.world.getTotalWorldTime() - run.startTick;
        int directDelta = end.getCurrentMana() - run.startState.getCurrentMana();
        int endingFlasks = countFlasks(player);
        log("run_summary", run,
                "reason=" + reason
                        + ",elapsedTicks=" + elapsed
                        + ",elapsedSeconds=" + decimal(elapsed / 20.0D)
                        + ",startMana=" + run.startState.getCurrentMana()
                        + ",endMana=" + end.getCurrentMana()
                        + ",directDelta=" + directDelta
                        + ",eventDelta=" + run.totalEventDelta
                        + ",spellSpent=" + run.spellSpent
                        + ",regenerated=" + run.regenerated
                        + ",flaskRestored=" + run.flaskRestored
                        + ",otherDelta=" + run.otherManaDelta
                        + ",attempts=" + run.attempts
                        + ",successfulCasts=" + run.successfulCasts
                        + ",spellCostTransactions=" + run.spellCostTransactions
                        + ",continuousPortions=" + run.continuousPortions
                        + ",continuousFinishes=" + run.continuousFinishes
                        + ",continuousEarlyStops=" + run.continuousEarlyStops
                        + ",wrongSpellAttempts=" + run.wrongSpellAttempts
                        + ",runtimeInputMismatches=" + run.runtimeInputMismatches
                        + ",canceledAttempts=" + run.canceledAttempts
                        + ",duplicateAttemptsBlocked=" + run.duplicateAttemptsBlocked
                        + ",unmatchedSuccesses=" + run.unmatchedSuccessfulCasts
                        + ",skippedCues=" + run.skippedCues
                        + ",hits=" + run.hits
                        + ",misses=" + run.misses
                        + ",ownerFailures=" + run.ownerReportedFailures
                        + ",downtimeTicks=" + run.downtimeTicks
                        + ",firstRequiredTick=" + run.firstRequiredTick
                        + ",firstFlaskTick=" + run.firstFlaskTick
                        + ",initialFlasks=" + run.initialFlaskCount
                        + ",endingFlasks=" + endingFlasks
                        + ",flaskCountDelta=" + (endingFlasks - run.initialFlaskCount)
        );
        player.sendMessage(new TextComponentString(
                "JAWMS balance run finished: " + run.runId
                        + ", reason=" + reason
                        + ", elapsed=" + elapsed + " ticks"
                        + ", mana=" + end.getCurrentMana() + "/" + end.getMaximumMana()
                        + ". Summary retained in latest.log."
        ));
    }

    private static Spell requireSpell(ResourceLocation id) throws CommandException {
        Spell spell = Spell.registry.getValue(id);
        if (spell == null) {
            throw new CommandException("Unknown spell in frozen rotation: " + id);
        }
        return spell;
    }

    private ActiveRun requireRun(EntityPlayerMP player) throws CommandException {
        ActiveRun run = activeRuns.get(player.getUniqueID());
        if (run == null) {
            throw new CommandException("No balance run is active for this player.");
        }
        return run;
    }

    private static int countFlasks(EntityPlayerMP player) {
        int count = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack.getItem() instanceof ItemManaFlask) {
                count += stack.getCount();
            }
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            if (stack.getItem() instanceof ItemManaFlask) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static String armorDescription(EntityPlayerMP player) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < player.inventory.armorInventory.size(); index++) {
            ItemStack stack = player.inventory.armorInventory.get(index);
            if (stack.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append('+');
            }
            result.append(stack.getItem().getRegistryName());
        }
        return result.length() == 0 ? "none" : result.toString();
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    static int requiredCostForAttempt(int finalCost, boolean continuous) {
        if (finalCost < 0) {
            throw new IllegalArgumentException("Final cost must not be negative.");
        }
        return continuous ? finalCost / 2 + finalCost % 2 : finalCost;
    }

    private static void log(String event, ActiveRun run, String fields) {
        JustAnotherWizardryManaSystemMod.LOGGER.info(
                "JAWMS balance: event={}, runId={}, rotation={}, repetition={}, {}.",
                event,
                run.runId,
                run.rotation.getId(),
                run.repetition,
                fields
        );
    }

    private static final class Attempt {

        private final int cueIndex;
        private final ResourceLocation spellId;
        private final int finalCost;
        private final boolean continuous;
        private final boolean validForCue;

        private Attempt(
                int cueIndex,
                ResourceLocation spellId,
                int finalCost,
                boolean continuous,
                boolean validForCue
        ) {
            this.cueIndex = cueIndex;
            this.spellId = spellId;
            this.finalCost = finalCost;
            this.continuous = continuous;
            this.validForCue = validForCue;
        }
    }

    private static final class ActiveRun {

        private final BalanceRotation rotation;
        private final int repetition;
        private final long startTick;
        private final ManaPublicState startState;
        private final int initialFlaskCount;
        private final String armor;
        private final String runId;

        private int nextCueIndex;
        private int activeCueIndex = -1;
        private boolean activeCueSucceeded;
        private long releaseCueTick = -1L;
        private boolean releaseCueSent;
        private Attempt pendingAttempt;
        private int attempts;
        private int successfulCasts;
        private int wrongSpellAttempts;
        private int runtimeInputMismatches;
        private int canceledAttempts;
        private int duplicateAttemptsBlocked;
        private int unmatchedSuccessfulCasts;
        private int skippedCues;
        private int hits;
        private int misses;
        private int ownerReportedFailures;
        private int downtimeTicks;
        private int spellSpent;
        private int regenerated;
        private int flaskRestored;
        private int otherManaDelta;
        private int totalEventDelta;
        private int spellCostTransactions;
        private int continuousPortions;
        private int continuousFinishes;
        private int continuousEarlyStops;
        private long continuousStartTick = -1L;
        private int continuousExpectedHoldTicks;
        private long firstRequiredTick = -1L;
        private long firstFlaskTick = -1L;
        private boolean finishAfterFlask;
        private long flaskEventWorldTick = -1L;

        private ActiveRun(
                BalanceRotation rotation,
                int repetition,
                long startTick,
                ManaPublicState startState,
                int initialFlaskCount,
                String armor
        ) {
            this.rotation = rotation;
            this.repetition = repetition;
            this.startTick = startTick;
            this.startState = startState;
            this.initialFlaskCount = initialFlaskCount;
            this.armor = armor;
            this.runId = rotation.getId() + "-r" + repetition + "-t" + startTick;
        }
    }
}
