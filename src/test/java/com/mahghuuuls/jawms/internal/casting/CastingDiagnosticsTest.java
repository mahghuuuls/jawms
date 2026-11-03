package com.mahghuuuls.jawms.internal.casting;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.api.CastAttemptContext;
import com.mahghuuuls.jawms.api.CastAttemptOutcome;
import com.mahghuuuls.jawms.api.CastParticipantOutcome;
import electroblob.wizardry.spell.Spell;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CastingDiagnosticsTest {
    private final List<String> lines = new ArrayList<>();
    private final Logger logger = (Logger) JustAnotherWizardryManaSystemMod.LOGGER;
    private Level previousLevel;
    private final AbstractAppender appender = new AbstractAppender("casting-test", null, null, true) {
        @Override public void append(LogEvent event) {
            lines.add(event.getMessage().getFormattedMessage());
        }
    };

    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @BeforeEach void capture() {
        CastingDiagnostics.clear();
        CastingDiagnostics.setDevelopmentEnabled(false);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach void restore() {
        logger.removeAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
        CastingDiagnostics.clear();
    }

    @Test void detailOffStillWarnsAtTheExactFaultWindowBoundary() {
        CastAttemptOutcome healthy = outcome(CastAttemptOutcome.Status.ACCEPTED,
                result("paid", CastParticipantOutcome.Status.PAID, CastParticipantOutcome.Failure.NONE, true, 2));
        CastingDiagnostics.participation(healthy, 0, 0, null);
        assertTrue(lines.isEmpty());

        CastAttemptOutcome fault = outcome(CastAttemptOutcome.Status.CLEANUP_FAULT,
                result("broken", CastParticipantOutcome.Status.FAULT, CastParticipantOutcome.Failure.CLEANUP, false, 0));
        for (int tick : new int[]{0, 1, 199}) CastingDiagnostics.participation(fault, tick, 0, null);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("participant=test:broken, failure=CLEANUP"));
        CastingDiagnostics.participation(fault, 200, 0, null);
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).contains("suppressed=2"));
        CastingDiagnostics.clear();
        CastingDiagnostics.participation(fault, 201, 0, null);
        assertEquals(3, lines.size());
        assertTrue(lines.get(2).contains("suppressed=0"));
    }

    @Test void capacityDoesNotEvictAnActiveFaultWindow() {
        CastAttemptOutcome first = outcome(CastAttemptOutcome.Status.CLEANUP_FAULT,
                result("first", CastParticipantOutcome.Status.FAULT, CastParticipantOutcome.Failure.CLEANUP, false, 0));
        CastingDiagnostics.participation(first, 0, 0, null);
        CastParticipantOutcome[] faults = new CastParticipantOutcome[257];
        for (int i = 0; i < faults.length; i++) {
            faults[i] = result("extra" + i, CastParticipantOutcome.Status.FAULT,
                    CastParticipantOutcome.Failure.CLEANUP, false, 0);
        }
        CastingDiagnostics.participation(outcome(CastAttemptOutcome.Status.CLEANUP_FAULT, faults), 0, 0, null);
        assertEquals(257, lines.size());
        CastingDiagnostics.participation(first, 199, 0, null);
        assertEquals(257, lines.size());
        CastingDiagnostics.participation(first, 200, 0, null);
        assertTrue(lines.get(257).contains("participant=test:first"));
        assertTrue(lines.get(257).contains("suppressed=1"));
        CastingDiagnostics.participation(outcome(CastAttemptOutcome.Status.CLEANUP_FAULT, faults[256]), 200, 0, null);
        assertEquals(1, lines.stream().filter(line -> line.contains("participant=test:extra256")).count());
    }

    @Test void effectAndNotificationFaultsRemainVisibleAlongsideCleanupWithDetailOff() {
        CastAttemptOutcome cleanup = outcome(CastAttemptOutcome.Status.CLEANUP_FAULT,
                result("broken", CastParticipantOutcome.Status.FAULT, CastParticipantOutcome.Failure.CLEANUP, false, 0));
        CastingDiagnostics.notificationFault(cleanup, new ResourceLocation("test:broken"), 0);
        CastingDiagnostics.thrownEffect(cleanup, 0, 0, null);
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("participant=test:broken, failure=NOTIFICATION"));
        assertTrue(lines.get(1).contains("participant=jawms:local, failure=EFFECT_EXCEPTION"));
        assertTrue(lines.get(2).contains("participant=test:broken, failure=CLEANUP"));
    }

    @Test void detailKeepsInvalidatedAndUnknownDistinctWithoutStackData() {
        enableDetail();
        CastAttemptOutcome partial = outcome(CastAttemptOutcome.Status.SETTLEMENT_FAULT,
                result("paid", CastParticipantOutcome.Status.PAID, CastParticipantOutcome.Failure.NONE, true, 2),
                result("invalid", CastParticipantOutcome.Status.INVALIDATED, CastParticipantOutcome.Failure.NONE, true, 0),
                result("broken", CastParticipantOutcome.Status.FAULT, CastParticipantOutcome.Failure.SETTLEMENT, false, 0));
        CastingDiagnostics.participation(partial, 0, 0, null);
        String output = String.join("\n", lines);
        assertTrue(output.contains("use=test/use, attempt=test/attempt, spell=test:spell, hand=MAIN_HAND, status=SETTLEMENT_FAULT"));
        assertTrue(output.contains("participant=test:paid, status=PAID, failure=NONE, amount=2.0"));
        assertTrue(output.contains("participant=test:invalid, status=INVALIDATED, failure=NONE, amount=0.0"));
        assertTrue(output.contains("participant=test:broken, status=FAULT, failure=SETTLEMENT, amount=unknown"));
        assertFalse(output.contains("private-nbt-value"));
    }

    @Test void continuousUseEmitsOneSummaryCountingZeroPortions() {
        enableDetail();
        CastingDiagnostics.UseSummary summary = new CastingDiagnostics.UseSummary();
        CastAttemptOutcome free = outcome(CastAttemptOutcome.Status.ACCEPTED,
                result("free", CastParticipantOutcome.Status.FREE, CastParticipantOutcome.Failure.NONE, true, 0));
        for (int tick = 0; tick < 20; tick++) CastingDiagnostics.participation(free, tick, 0, summary);
        CastingDiagnostics.participation(free, 20, 1, summary);
        assertTrue(lines.isEmpty());
        CastParticipantOutcome terminal = new CastParticipantOutcome(new ResourceLocation("test:stopper"),
                CastParticipantOutcome.Status.PAID, CastParticipantOutcome.Failure.NONE, true, 2, true, "");
        CastingDiagnostics.participation(outcome(CastAttemptOutcome.Status.ACCEPTED,
                free.getParticipants().get(0), terminal), 21, 0, summary);
        summary.emit(ParticipantUseMessages.CloseCause.TERMINAL);
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("attempts=22, zeroPortions=21"));
        assertTrue(lines.get(1).contains("knownPaid=0.0, unknownResults=0, lastStatus=FREE"));
        assertTrue(lines.get(1).contains("terminalAttempt=null"));
        assertTrue(lines.get(2).contains("participant=test:stopper, knownPaid=2.0"));
        assertTrue(lines.get(2).contains("terminalAttempt=test/attempt"));
    }

    @Test void terminalAttributionSurvivesTheParticipantSummaryCap() {
        enableDetail();
        CastingDiagnostics.UseSummary summary = new CastingDiagnostics.UseSummary();
        CastParticipantOutcome[] results = new CastParticipantOutcome[17];
        for (int i = 0; i < 16; i++) {
            results[i] = result("free" + i, CastParticipantOutcome.Status.FREE, CastParticipantOutcome.Failure.NONE, true, 0);
        }
        results[16] = new CastParticipantOutcome(new ResourceLocation("test:stopper"),
                CastParticipantOutcome.Status.PAID, CastParticipantOutcome.Failure.NONE, true, 2, true, "");
        CastingDiagnostics.participation(outcome(CastAttemptOutcome.Status.ACCEPTED, results), 0, 0, summary);
        summary.emit(ParticipantUseMessages.CloseCause.TERMINAL);
        assertEquals(18, lines.size());
        assertTrue(lines.get(0).contains("omittedResults=1"));
        assertTrue(lines.get(17).contains("attempt=test/attempt, participant=test:stopper, status=PAID, amount=2.0"));
        assertTrue(lines.get(17).contains("stopAfterSuccess=true"));

        lines.clear();
        summary = new CastingDiagnostics.UseSummary();
        results[16] = result("invalid", CastParticipantOutcome.Status.INVALIDATED,
                CastParticipantOutcome.Failure.NONE, false, 0);
        CastingDiagnostics.participation(outcome(CastAttemptOutcome.Status.INVALID_AFTER_EFFECT, results), 1, 0, summary);
        summary.emit(ParticipantUseMessages.CloseCause.INVALIDATED);
        assertEquals(18, lines.size());
        assertTrue(lines.get(17).contains("attempt=test/attempt, participant=test:invalid, status=INVALIDATED, amount=unknown"));
    }

    private void enableDetail() {
        CastingDiagnostics.setDevelopmentEnabled(true);
        lines.clear();
    }

    private static CastParticipantOutcome result(String id, CastParticipantOutcome.Status status,
            CastParticipantOutcome.Failure failure, boolean known, double amount) {
        return new CastParticipantOutcome(new ResourceLocation("test", id), status, failure, known, amount, false, "");
    }

    private static CastAttemptOutcome outcome(CastAttemptOutcome.Status status, CastParticipantOutcome... results) {
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        when(player.getUniqueID()).thenReturn(UUID.randomUUID());
        ItemStack stack = new ItemStack(Items.STICK);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("private", "private-nbt-value");
        stack.setTagCompound(tag);
        CastAttemptContext context = new CastAttemptContext(player, mock(Spell.class),
                new ResourceLocation("test:spell"), stack, EnumHand.MAIN_HAND, false, false, 0,
                "test/use", "test/attempt", new CastAttemptContext.AcceptedCost(0, 0, 1, 1, 0, 100), 0, 0, 0);
        return new CastAttemptOutcome(context, status, true, true, true, 0, 0, Arrays.asList(results));
    }
}
