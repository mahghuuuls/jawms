package com.mahghuuuls.jawms.testfixture.automation;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PilotEvidenceTest {
    private final UUID run = UUID.randomUUID(), epoch = UUID.randomUUID(), player = UUID.randomUUID();
    private String record(String event, Map<String, String> data) {
        Map<String, Object> r = new LinkedHashMap<>(); r.put("run", run.toString()); r.put("event", event); r.put("data", data);
        return "MV02_PILOT " + new Gson().toJson(r);
    }
    private Map<String, String> values(String... pairs) {
        Map<String, String> data = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) data.put(pairs[i], pairs[i+1]);
        return data;
    }
    private List<String> normal() {
        List<String> lines = new ArrayList<>();
        lines.add(record("PREFLIGHT_REQUEST", values("level", "JAWMS-C007-MV02-I-P")));
        lines.add(record("CLIENT_PREFLIGHT_IDLE", values("epoch", epoch.toString())));
        lines.add(record("SETUP_BEGIN", values("level", "JAWMS-C007-MV02-I-P")));
        lines.add(record("SERVER_READY", values("units", "1000000", "hand", "main", "mode", "NORMAL", "player", player.toString(), "effects", "0", "paid", "0", "claimed", "0", "prepare", "0", "settle", "0", "resolved", "0", "finishEvents", "0", "finishBodies", "0", "mana", "100")));
        lines.add(record("CLIENT_ARMED", values("kind", "NORMAL", "hand", "main", "baselineIntent", "0", "baselineEpoch", epoch.toString(), "player", player.toString(), "level", "JAWMS-C007-MV02-I-P")));
        lines.add(record("SERVER_SAMPLE", values("sequence", "1")));
        lines.add(record("LIVE_ACK", values("sequence", "1")));
        lines.add(record("KEY_TRANSITION", values("down", "true", "epoch", epoch.toString())));
        lines.add(record("CONTROLLER", values("key", "true", "release", "false", "hand", "main")));
        lines.add("JAWMS client use: event=intent_sent epoch=" + epoch + " intent=1 use=0");
        lines.add("USE_TRANSPORT event=vanilla_receive side=SERVER");
        lines.add("CONTINUOUS_FIXTURE resolved status=ACCEPTED stop=false mana=2 hunger=0 use=2 attempt=10 participants=1");
        lines.add("CONTINUOUS_FIXTURE resolved status=ACCEPTED stop=false mana=0 hunger=0 use=2 attempt=11 participants=1");
        lines.add(record("KEY_TRANSITION", values("down", "false", "epoch", epoch.toString())));
        lines.add(record("CONTROLLER", values("key", "false", "release", "true", "hand", "main")));
        lines.add("JAWMS client use: event=release_sent epoch=" + epoch + " intent=1 use=2");
        lines.add("JAWMS client use: event=normal_permission_consumed epoch=" + epoch + " intent=1 use=2 pending=false");
        lines.add("CONTINUOUS_FIXTURE actual_finish_event side=CLIENT");
        lines.add("CONTINUOUS_FIXTURE actual_finish_body side=CLIENT");
        lines.add("PUBLICATION_OBSERVER event=actual_cooldown_target values=[side, CLIENT, stack, 4, value, 20]");
        lines.add(record("SERVER_SAMPLE", values("hand", "main", "mode", "NORMAL", "sequence", "2", "player", player.toString(), "use", "2", "status", "ACCEPTED", "effects", "2", "childEffects", "0", "prepare", "2", "settle", "2", "resolved", "2", "claimed", "0", "release", "0", "outcomeMana", "2", "outcomeHunger", "0", "mana", "98", "paid", "6", "units", "999994", "finishEvents", "1", "finishBodies", "1", "cooldownIncreases", "1")));
        lines.add(record("CLIENT_SAMPLE", values("sample", "1", "effects", "2", "childEffects", "0", "sequence", "2", "player", player.toString(), "epoch", epoch.toString(), "intent", "1", "use", "2", "queued", "0", "pending", "false", "blocked", "false", "fault", "false", "discarded", "false", "key", "false", "controllerCalls", "1", "releaseCalls", "1", "finishEvents", "1", "finishBodies", "1", "cooldownIncreases", "1")));
        lines.add(record("ACTION_COMPLETE", values("verdict", "NOT_QUALIFIED")));
        lines.add(record("SERVER_END", values("sequence", "2")));
        return lines;
    }
    private PilotEvidence.Result evaluate(List<String> lines) { PilotEvidence reader = new PilotEvidence(run); reader.read(lines); return reader.evaluate(); }
    @Test void completeAssertionEvidenceNeverClaimsRuntimeQualification() {
        PilotEvidence.Result result = evaluate(normal());
        assertEquals(PilotEvidence.Verdict.PASS, result.verdict, result.reasons.toString()); assertFalse(result.qualified);
    }
    @Test void driverCompletionAloneIsUnreachedAndWrongRunCannotSupplyReadiness() {
        assertNotEquals(PilotEvidence.Verdict.PASS, evaluate(Collections.singletonList(record("ACTION_COMPLETE", values()))).verdict);
        List<String> wrong = new ArrayList<>(); for (String line : normal()) wrong.add(line.replace(run.toString(), UUID.randomUUID().toString()));
        assertNotEquals(PilotEvidence.Verdict.PASS, evaluate(wrong).verdict);
    }
    @Test void removingEachIndependentBoundaryPreventsPass() {
        for (String boundary : Arrays.asList("PREFLIGHT_REQUEST", "CLIENT_PREFLIGHT_IDLE", "LIVE_ACK", "SERVER_READY", "CLIENT_ARMED", "CONTROLLER", "KEY_TRANSITION", "intent_sent", "release_sent",
                "vanilla_receive", "resolved status", "normal_permission_consumed", "actual_finish_event", "actual_finish_body", "actual_cooldown_target", "SERVER_SAMPLE", "CLIENT_SAMPLE", "ACTION_COMPLETE", "SERVER_END")) {
            List<String> broken = normal(); broken.removeIf(s -> s.contains(boundary));
            assertNotEquals(PilotEvidence.Verdict.PASS, evaluate(broken).verdict, boundary);
        }
    }
    @Test void wrongAmountsExtraEffectsFinishReplayAndOpenClaimsFailRatherThanBecomeNewExpectations() {
        for (String[] change : new String[][] { {"\"paid\":\"6\"", "\"paid\":\"3\""}, {"\"claimed\":\"0\"", "\"claimed\":\"1\""},
                {"\"finishBodies\":\"1\"", "\"finishBodies\":\"2\""}, {"\"use\":\"2\"", "\"use\":\"9\""},
                {"\"effects\":\"2\"", "\"effects\":\"3\""}, {"\"mana\":\"98\"", "\"mana\":\"97\""} }) {
            List<String> broken = normal();
            for (int i = 0; i < broken.size(); i++) if (broken.get(i).contains("SERVER_SAMPLE")) broken.set(i, broken.get(i).replace(change[0], change[1]));
            assertEquals(PilotEvidence.Verdict.FAIL, evaluate(broken).verdict, Arrays.toString(change));
        }
    }
    @Test void truncatedRecordsDuplicateSamplesOrAbortCannotPass() {
        List<String> truncated = normal(); truncated.add("MV02_PILOT {\"run\":");
        assertNotEquals(PilotEvidence.Verdict.PASS, evaluate(truncated).verdict);
        List<String> duplicate = normal(); duplicate.add(duplicate.stream().filter(s -> s.contains("SERVER_SAMPLE")).findFirst().get());
        assertEquals(PilotEvidence.Verdict.FAIL, evaluate(duplicate).verdict);
        List<String> aborted = normal(); aborted.add(record("UNREACHED", values("reason", "reset_failure")));
        assertEquals(PilotEvidence.Verdict.FAIL, evaluate(aborted).verdict);
    }
    @Test void outOfWindowOrOtherRunProductionTextCannotFillAMissingBoundary() {
        List<String> broken = normal(); broken.removeIf(s -> s.contains("normal_permission_consumed"));
        broken.add("JAWMS client use: event=normal_permission_consumed epoch=" + epoch + " intent=1 use=2");
        assertNotEquals(PilotEvidence.Verdict.PASS, evaluate(broken).verdict);
    }
    private List<String> terminal(boolean delayed) {
        List<String> lines = normal();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("CLIENT_ARMED")) line = line.replace("\"NORMAL\"", delayed ? "\"DELAYED_TERMINAL\"" : "\"TERMINAL\"");
            if (line.contains("SERVER_READY") || line.contains("SERVER_SAMPLE")) line = line.replace("\"mode\":\"NORMAL\"", "\"mode\":\"TERMINAL\"")
                    .replace("\"units\":\"1000000\"", "\"units\":\"2\"");
            if (line.contains("SERVER_SAMPLE")) line = line.replace("\"effects\":\"2\"", "\"effects\":\"1\"")
                    .replace("\"prepare\":\"2\"", "\"prepare\":\"1\"").replace("\"settle\":\"2\"", "\"settle\":\"1\"")
                    .replace("\"resolved\":\"2\"", "\"resolved\":\"1\"").replace("\"paid\":\"6\"", "\"paid\":\"2\"").replace("\"units\":\"999994\"", "\"units\":\"0\"");
            if (line.contains("SERVER_SAMPLE") || line.contains("CLIENT_SAMPLE")) line = line.replace("\"finishEvents\":\"1\"", "\"finishEvents\":\"0\"")
                    .replace("\"finishBodies\":\"1\"", "\"finishBodies\":\"0\"").replace("\"cooldownIncreases\":\"1\"", "\"cooldownIncreases\":\"0\"");
            line = line.replace("normal_permission_consumed", "close_TERMINAL").replace("stop=false mana=2", "stop=true mana=2");
            lines.set(i, line);
        }
        lines.removeIf(s -> s.contains("attempt=11") || s.contains("actual_finish_") || s.contains("actual_cooldown_target"));
        int close = 0; for (int i = 0; i < lines.size(); i++) if (lines.get(i).contains("event=close_TERMINAL")) close = i;
        String rejection = "JAWMS client use: event=input_rejected epoch=" + epoch + " intent=1 use=2 pending=true";
        if (delayed) {
            lines.add(close, "USE_TRANSPORT event=close_held side=CLIENT");
            lines.add(close + 1, record("KEY_TRANSITION", values("down", "true", "epoch", epoch.toString())));
            lines.add(close + 2, rejection);
            lines.add(close + 4, record("KEY_TRANSITION", values("down", "false", "epoch", epoch.toString())));
        } else {
            String up = lines.stream().filter(s -> s.contains("KEY_TRANSITION") && s.contains("false")).findFirst().get();
            String release = lines.stream().filter(s -> s.contains("event=release_sent")).findFirst().get();
            lines.remove(up); lines.remove(release);
            lines.removeIf(s -> s.contains("CONTROLLER") && s.contains("\"release\":\"true\""));
            for (int i = 0; i < lines.size(); i++) if (lines.get(i).contains("CLIENT_SAMPLE"))
                lines.set(i, lines.get(i).replace("\"releaseCalls\":\"1\"", "\"releaseCalls\":\"0\""));
            for (int i = 0; i < lines.size(); i++) if (lines.get(i).contains("event=close_TERMINAL")) { close = i; break; }
            lines.add(close + 1, rejection); lines.add(close + 2, up); lines.add(close + 3, release);
        }
        return lines;
    }
    @Test void terminalAndDelayedTerminalRequireActualRejectedInputAndNoFinish() {
        for (boolean delayed : new boolean[] { false, true }) {
            List<String> good = terminal(delayed);
            assertEquals(PilotEvidence.Verdict.PASS, evaluate(good).verdict, evaluate(good).reasons.toString());
            List<String> broken = new ArrayList<>(good); broken.removeIf(s -> s.contains("input_rejected"));
            assertNotEquals(PilotEvidence.Verdict.PASS, evaluate(broken).verdict);
            if (delayed) {
                broken = new ArrayList<>(good); String close = broken.stream().filter(s -> s.contains("event=close_TERMINAL")).findFirst().get();
                broken.remove(close); broken.add(4, close);
                assertEquals(PilotEvidence.Verdict.FAIL, evaluate(broken).verdict);
            }
        }
    }
    @Test void rawFinishReplayOrOutcomeAttributionCannotBeHiddenByFinalCounters() {
        for (String injected : Arrays.asList("CONTINUOUS_FIXTURE actual_finish_event side=CLIENT",
                "CONTINUOUS_FIXTURE resolved status=ACCEPTED stop=false mana=0 hunger=0 use=9 attempt=10 participants=1")) {
            List<String> broken = normal(); broken.add(5, injected);
            assertEquals(PilotEvidence.Verdict.FAIL, evaluate(broken).verdict, injected);
        }
    }
    @Test void delayedNormalNeedsRealInstallationBeforePermissionAndRejectedSecondInput() {
        List<String> lines = normal();
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).contains("CLIENT_ARMED")) lines.set(i, lines.get(i).replace("\"NORMAL\"", "\"DELAYED_NORMAL\""));
        int close = 0; for (int i = 0; i < lines.size(); i++) if (lines.get(i).contains("normal_permission_consumed")) close = i;
        lines.addAll(close, Arrays.asList("USE_TRANSPORT event=close_held side=CLIENT",
                "PUBLICATION_OBSERVER event=paired_decode", "PUBLICATION_OBSERVER event=main_apply_scope", "PUBLICATION_OBSERVER event=actual_paired_install",
                record("KEY_TRANSITION", values("down", "true", "epoch", epoch.toString())),
                "JAWMS client use: event=input_rejected epoch=" + epoch + " intent=1 use=2 pending=true",
                record("KEY_TRANSITION", values("down", "false", "epoch", epoch.toString()))));
        assertEquals(PilotEvidence.Verdict.PASS, evaluate(lines).verdict, evaluate(lines).reasons.toString());
        List<String> missing = new ArrayList<>(lines); missing.removeIf(s -> s.contains("actual_paired_install"));
        assertNotEquals(PilotEvidence.Verdict.PASS, evaluate(missing).verdict);
        List<String> early = new ArrayList<>(lines); String permission = early.stream().filter(s -> s.contains("normal_permission_consumed")).findFirst().get();
        early.remove(permission); early.add(5, permission);
        assertEquals(PilotEvidence.Verdict.FAIL, evaluate(early).verdict);
    }
    @Test void controllerFieldsNonalternatingEdgesAndRejectionAfterReleaseFailClosed() {
        for (String[] mutation : new String[][] { {"\"key\":\"true\"", "\"key\":\"false\""},
                {"\"hand\":\"main\"", "\"hand\":\"off\""}, {"\"release\":\"true\"", "\"release\":\"false\""} }) {
            List<String> broken = normal();
            for (int i = 0; i < broken.size(); i++) if (broken.get(i).contains("CONTROLLER")) broken.set(i, broken.get(i).replace(mutation[0], mutation[1]));
            assertNotEquals(PilotEvidence.Verdict.PASS, evaluate(broken).verdict);
        }
        List<String> repeated = normal();
        repeated.add(9, record("KEY_TRANSITION", values("down", "true", "epoch", epoch.toString())));
        assertNotEquals(PilotEvidence.Verdict.PASS, evaluate(repeated).verdict);
        List<String> afterRelease = terminal(false);
        String rejection = afterRelease.stream().filter(s -> s.contains("input_rejected")).findFirst().get();
        afterRelease.remove(rejection); afterRelease.add(afterRelease.size() - 3, rejection);
        assertNotEquals(PilotEvidence.Verdict.PASS, evaluate(afterRelease).verdict);
    }
    @Test void independentClientEffectsAndUnexpectedChildrenCannotBeOmittedOrZeroed() {
        for (String replacement : Arrays.asList("\"effects\":\"0\"", "\"unused\":\"2\"")) {
            List<String> broken = normal();
            for (int i = 0; i < broken.size(); i++) if (broken.get(i).contains("CLIENT_SAMPLE")) broken.set(i, broken.get(i).replace("\"effects\":\"2\"", replacement));
            assertNotEquals(PilotEvidence.Verdict.PASS, evaluate(broken).verdict);
        }
        List<String> child = normal();
        for (int i = 0; i < child.size(); i++) if (child.get(i).contains("CLIENT_SAMPLE")) child.set(i, child.get(i).replace("\"childEffects\":\"0\"", "\"childEffects\":\"1\""));
        assertNotEquals(PilotEvidence.Verdict.PASS, evaluate(child).verdict);
    }
    @Test void clientSamplesMayShareALiveServerAckButNeverReuseTheirOwnSampleId() {
        List<String> lines = normal();
        String sample = lines.stream().filter(s -> s.contains("CLIENT_SAMPLE")).findFirst().get();
        int position = lines.indexOf(sample);
        lines.add(position + 1, sample.replace("\"sample\":\"1\"", "\"sample\":\"2\""));
        assertEquals(PilotEvidence.Verdict.PASS, evaluate(lines).verdict, evaluate(lines).reasons.toString());
        lines.add(position + 1, sample);
        assertEquals(PilotEvidence.Verdict.FAIL, evaluate(lines).verdict);
    }
}
