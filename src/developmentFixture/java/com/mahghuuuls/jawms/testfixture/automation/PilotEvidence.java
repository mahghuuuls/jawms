package com.mahghuuuls.jawms.testfixture.automation;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Offline fail-closed assertion reader, independent of driver completion. Never qualifies input. */
public final class PilotEvidence {
    public enum Verdict { PASS, FAIL, UNREACHED }
    public static final class Result {
        public final Verdict verdict;
        public final boolean qualified = false;
        public final String scope = "pilot assertions only; physical comparison, transforms and provenance review still required";
        public final List<String> reasons;
        Result(Verdict verdict, List<String> reasons) { this.verdict = verdict; this.reasons = Collections.unmodifiableList(reasons); }
    }
    private final UUID run;
    private final List<JsonObject> records = new ArrayList<>();
    private final List<String> raw = new ArrayList<>();
    private final List<Boolean> rawKey = new ArrayList<>();
    private final List<Integer> rawPress = new ArrayList<>();
    private final List<String> missing = new ArrayList<>(), failures = new ArrayList<>();
    public PilotEvidence(UUID run) { this.run = Objects.requireNonNull(run); }
    public void read(List<String> lines) {
        boolean window = false;
        boolean down = false; int presses = 0;
        for (String line : lines) {
            int offset = line.indexOf("MV02_PILOT ");
            if (offset >= 0) {
                try {
                    JsonObject record = new JsonParser().parse(line.substring(offset + 11)).getAsJsonObject();
                    if (!run.toString().equals(record.get("run").getAsString())) continue;
                    String event = record.get("event").getAsString(); record.getAsJsonObject("data").entrySet();
                    records.add(record);
                    if (event.equals("KEY_TRANSITION")) {
                        down = "true".equals(record.getAsJsonObject("data").get("down").getAsString());
                        if (down) presses++;
                    }
                    if (event.equals("CLIENT_ARMED") || event.equals("SETUP_BEGIN")) window = true;
                    if (event.equals("ACTION_COMPLETE") || event.equals("SERVER_END")) window = false;
                } catch (RuntimeException malformed) { missing.add("malformed/truncated pilot record"); }
            } else if (window) { raw.add(line); rawKey.add(down); rawPress.add(presses); }
        }
    }
    private List<JsonObject> events(String event) {
        List<JsonObject> found = new ArrayList<>();
        for (JsonObject record : records) if (event.equals(record.get("event").getAsString())) found.add(record.getAsJsonObject("data"));
        return found;
    }
    private JsonObject one(String event) {
        List<JsonObject> found = events(event);
        if (found.size() != 1) { missing.add(event + " expected once, found " + found.size()); return new JsonObject(); }
        return found.get(0);
    }
    private String string(JsonObject data, String key) {
        if (!data.has(key) || !data.get(key).isJsonPrimitive()) { missing.add("missing field " + key); return ""; }
        return data.get(key).getAsString();
    }
    private int number(JsonObject data, String key) {
        try { return Integer.parseInt(string(data, key)); }
        catch (NumberFormatException bad) { missing.add("invalid integer " + key); return -1; }
    }
    private void expect(boolean correct, String reason) { if (!correct) failures.add(reason); }
    private void require(boolean reached, String reason) { if (!reached) missing.add(reason); }
    private boolean raw(String text) { for (String line : raw) if (line.contains(text)) return true; return false; }
    private int first(String text) { for (int i = 0; i < raw.size(); i++) if (raw.get(i).contains(text)) return i; return -1; }
    private int count(String text) { int count = 0; for (String line : raw) if (line.contains(text)) count++; return count; }
    private JsonObject latest(String event) {
        JsonObject last = new JsonObject(); int previous = -1;
        for (JsonObject data : events(event)) {
            int sequence = number(data, event.equals("CLIENT_SAMPLE") ? "sample" : "sequence");
            expect(sequence > previous, "duplicate/out-of-order " + event);
            previous = sequence; last = data;
        }
        require(previous >= 1, "missing post-action " + event); return last;
    }
    public Result evaluate() {
        if (!events("UNREACHED").isEmpty() || !events("ABORT").isEmpty()) failures.add("run aborted/unreached; do not retry silently");
        JsonObject armed = one("CLIENT_ARMED"), baseline = one("SERVER_READY");
        one("PREFLIGHT_REQUEST"); one("CLIENT_PREFLIGHT_IDLE"); one("SETUP_BEGIN"); one("ACTION_COMPLETE"); one("SERVER_END");
        PilotSequence.Kind kind = null;
        try { kind = PilotSequence.Kind.valueOf(string(armed, "kind")); }
        catch (IllegalArgumentException bad) { missing.add("invalid pilot kind"); }
        JsonObject server = latest("SERVER_SAMPLE"), client = latest("CLIENT_SAMPLE");
        require(number(client, "sequence") <= number(server, "sequence"), "final client sample has no corresponding live server readback");
        String player = string(armed, "player");
        expect(player.equals(string(baseline, "player")) && player.equals(string(server, "player"))
                && player.equals(string(client, "player")), "player attribution mismatch");
        require(InputPilotHarness.disposable(string(armed, "level")), "disposable-world identity missing");
        for (String key : Arrays.asList("effects", "paid", "claimed", "prepare", "settle", "resolved", "finishEvents", "finishBodies"))
            expect(number(baseline, key) == 0, "nonzero baseline " + key);
        expect(number(baseline, "mana") == 100, "baseline mana is not100");
        String epoch = string(client, "epoch"), intent = string(client, "intent"), use = string(client, "use");
        try { UUID.fromString(epoch); require(Long.parseLong(intent) > 0 && Long.parseLong(use) > 0, "missing owning intent/use"); }
        catch (IllegalArgumentException invalid) { missing.add("invalid epoch/intent/use"); }
        expect(use.equals(string(server, "use")), "server outcome/client use mismatch");
        expect(epoch.equals(string(armed, "baselineEpoch")), "arm/final epoch mismatch");
        String baselineIntent = string(armed, "baselineIntent");
        boolean ownIntentSeen = false;
        for (JsonObject sample : events("CLIENT_SAMPLE")) {
            expect(epoch.equals(string(sample, "epoch")), "epoch changed inside run");
            String candidate = string(sample, "intent");
            if (candidate.equals(intent)) ownIntentSeen = true;
            expect(candidate.equals(intent) || !ownIntentSeen && candidate.equals(baselineIntent), "extra gesture/queued cast inside run");
        }
        expect(number(client, "queued") == 0, "held frames remain");
        for (String key : Arrays.asList("pending", "blocked", "fault", "discarded", "key"))
            expect("false".equals(string(client, key)), "unclean final client " + key);
        require(number(client, "controllerCalls") > 0, "actual controller boundary missing");
        int downs = 0, ups = 0, uses = 0, releases = 0;
        boolean keyDown = false, live = false;
        String hand = string(armed, "hand");
        for (JsonObject record : records) {
            String event = record.get("event").getAsString(); JsonObject data = record.getAsJsonObject("data");
            if (event.equals("LIVE_ACK")) {
                int ack = number(data, "sequence");
                boolean matchingServerSample = false;
                for (JsonObject sample : events("SERVER_SAMPLE")) if (number(sample, "sequence") == ack) matchingServerSample = true;
                require(matchingServerSample, "live acknowledgement has no matching server sample"); live = true;
            }
            if (event.equals("KEY_TRANSITION")) {
                String value = string(data, "down"); boolean next = "true".equals(value);
                expect(value.equals("true") || value.equals("false"), "invalid key boolean");
                expect(next != keyDown, "nonalternating use-key edge");
                if (next) { downs++; require(live, "first input before live server acknowledgement"); } else ups++;
                keyDown = next;
                expect(epoch.equals(string(data, "epoch")), "key transition in wrong epoch");
            }
            if (event.equals("CONTROLLER")) {
                String release = string(data, "release"), actualKey = string(data, "key");
                expect(release.equals("true") || release.equals("false"), "invalid controller release flag");
                expect(hand.equals(string(data, "hand")), "wrong-hand controller boundary");
                expect(actualKey.equals(Boolean.toString(keyDown)), "controller key disagrees with actual input sequence");
                if (release.equals("true")) { releases++; expect(!keyDown, "release controller ran while input held"); }
                else { uses++; expect(keyDown, "use controller ran without held input"); }
            }
        }
        expect(!keyDown, "pilot left use key held");
        expect(number(client, "controllerCalls") == uses && number(client, "releaseCalls") == releases, "controller records/counters mismatch");
        require(downs > 0 && ups > 0, "input press/release not reached");
        require(!events("CONTROLLER").isEmpty(), "raw controller entry observation missing");
        String prefix = "JAWMS client use: event=";
        String identity = " epoch=" + epoch + " intent=" + intent + " use=";
        require(raw(prefix + "intent_sent" + identity), "own production intent not observed");
        require(raw(prefix + "release_sent" + identity), "own production release not observed");
        require(raw("USE_TRANSPORT event=vanilla_receive side=SERVER"), "real server vanilla input not observed");
        require(raw("CONTINUOUS_FIXTURE resolved status=ACCEPTED"), "immutable public outcome log not reached");
        expect("ACCEPTED".equals(string(server, "status")), "last settlement was not ACCEPTED");
        int effects = number(server, "effects");
        for (String key : Arrays.asList("prepare", "settle", "resolved")) expect(number(server, key) == effects, "effect/" + key + " mismatch");
        expect(number(server, "claimed") == 0 && number(server, "release") == 0, "open or unexpected released reservation");
        expect(number(server, "outcomeMana") == 100 - number(server, "mana") && number(server, "outcomeHunger") == 0,
                "independent mana versus immutable outcome mismatch");
        if (kind != null) {
            expect(Arrays.asList("main", "off").contains(hand) && hand.equals(string(baseline, "hand")) && hand.equals(string(server, "hand")), "actual outcome hand mismatch");
            String mode = kind.terminal ? "TERMINAL" : "NORMAL";
            expect(mode.equals(string(baseline, "mode")) && mode.equals(string(server, "mode")), "wrong actual fixture mode");
            int finish = kind.terminal ? 0 : 1;
            expect(effects >= (kind.terminal ? 1 : 2) && (!kind.terminal || effects == 1), "wrong effect count");
            require(number(client, "effects") >= (kind.terminal ? 1 : 2), "independent client effect boundary not reached");
            expect(number(client, "childEffects") == 0 && number(server, "childEffects") == 0, "unexpected child effect in pilot");
            int paid = kind.terminal ? 2 : 3 * effects;
            expect(number(baseline, "units") == (kind.terminal ? 2 : 1_000_000), "wrong independent provider baseline");
            expect(number(server, "paid") == paid && number(server, "units") == (kind.terminal ? 2 : 1_000_000) - paid, "independent fixture debit mismatch");
            if (kind.terminal) expect(number(server, "outcomeMana") == 2, "terminal mana portion mismatch");
            for (String field : Arrays.asList("finishEvents", "finishBodies", "cooldownIncreases"))
                expect(number(client, field) == finish && number(server, field) == finish, "actual " + field + " mismatch");
            require(raw(prefix + (kind.terminal ? "close_TERMINAL" : "normal_permission_consumed") + identity + use + " "), "own close/permission missing");
            expect(count("CONTINUOUS_FIXTURE actual_finish_event side=CLIENT") == finish
                    && count("CONTINUOUS_FIXTURE actual_finish_body side=CLIENT") == finish, "raw callback replay or hidden counter reset");
            Set<String> attempts = new HashSet<>(); int rawMana = 0, receipts = 0;
            java.util.regex.Pattern outcome = java.util.regex.Pattern.compile("CONTINUOUS_FIXTURE resolved status=ACCEPTED stop=(true|false) mana=(\\d+) hunger=(\\d+) use=(\\S+) attempt=(\\S+) participants=1");
            for (String line : raw) {
                java.util.regex.Matcher match = outcome.matcher(line);
                if (!match.find()) continue;
                receipts++; rawMana += Integer.parseInt(match.group(2));
                expect(attempts.add(match.group(5)) && use.equals(match.group(4)) && match.group(1).equals(Boolean.toString(kind.terminal))
                        && match.group(3).equals("0"), "duplicate/wrong-use/incorrect immutable outcome");
            }
            require(receipts == effects, "full immutable outcome series not reached");
            expect(rawMana == number(server, "outcomeMana"), "raw outcome sum versus independent balance mismatch");
            if (!kind.terminal) require(number(client, "releaseCalls") > 0, "normal vanilla release boundary missing");
            if (kind.terminal || kind.delayed) require(raw(prefix + "input_rejected" + identity), "held/premature input rejection not reached");
            if (kind.terminal || kind.delayed) {
                int rejection = first(prefix + "input_rejected" + identity);
                expect(rejection >= 0 && rawKey.get(rejection) && rawPress.get(rejection) == (kind.delayed ? 2 : 1), "rejection was not during the required held press");
            }
            if (kind.terminal && !kind.delayed) {
                int terminalClose = first(prefix + "close_TERMINAL" + identity);
                expect(terminalClose >= 0 && rawKey.get(terminalClose) && rawPress.get(terminalClose) == 1, "terminal close was not while first input held");
            }
            expect(downs == (kind.delayed ? 2 : 1) && ups == downs, "unexpected press/release sequence");
            if (kind.delayed) {
                require(raw("USE_TRANSPORT event=close_held side=CLIENT"), "actual close withholding not reached");
                int rejected = first(prefix + "input_rejected" + identity);
                int closed = first(prefix + (kind.terminal ? "close_TERMINAL" : "normal_permission_consumed") + identity);
                expect(rejected >= 0 && closed > rejected, "close resolved before blocked next input");
                if (!kind.terminal) {
                    require(raw("PUBLICATION_OBSERVER event=actual_paired_install"), "actual paired vanilla installation not reached");
                    require(raw("PUBLICATION_OBSERVER event=paired_decode") && raw("PUBLICATION_OBSERVER event=main_apply_scope"), "real decode/application scope missing");
                    expect(first("PUBLICATION_OBSERVER event=actual_paired_install") < closed, "normal permission preceded required installation");
                }
            }
            if (finish == 1) {
                require(raw("CONTINUOUS_FIXTURE actual_finish_event side=CLIENT") && raw("CONTINUOUS_FIXTURE actual_finish_body side=CLIENT"), "actual client callback/body not reached");
                require(raw("PUBLICATION_OBSERVER event=actual_cooldown_target values=[side, CLIENT"), "actual client cooldown write not reached");
                expect(!raw("PUBLICATION_OBSERVER event=detached_cooldown_target"), "detached cooldown write observed");
            }
        }
        List<String> reasons = new ArrayList<>(failures); reasons.addAll(missing);
        return new Result(!failures.isEmpty() ? Verdict.FAIL : !missing.isEmpty() ? Verdict.UNREACHED : Verdict.PASS, reasons);
    }
    /** Run offline, e.g. through the dedicated Gradle analysis task. Never starts Minecraft. */
    public static void main(String[] args) throws Exception {
        if (args.length < 3 || !Arrays.asList("integrated", "dedicated").contains(args[1]))
            throw new IllegalArgumentException("<run UUID> <integrated|dedicated> <client log> [server log]");
        Set<Path> files = new LinkedHashSet<>();
        for (int i = 2; i < args.length; i++) files.add(Paths.get(args[i]).toRealPath());
        if (args[1].equals("dedicated") && files.size() < 2) throw new IllegalArgumentException("Dedicated evidence needs distinct endpoint logs");
        PilotEvidence reader = new PilotEvidence(UUID.fromString(args[0]));
        for (Path file : files) {
            if (Files.size(file) > 32L * 1024 * 1024) throw new IllegalArgumentException("Slice oversized logs before analysis");
            reader.read(Files.readAllLines(file, StandardCharsets.UTF_8));
        }
        Result result = reader.evaluate(); System.out.println(new Gson().toJson(result));
        if (result.verdict != Verdict.PASS) throw new IllegalStateException("Pilot assertions " + result.verdict);
    }
}
