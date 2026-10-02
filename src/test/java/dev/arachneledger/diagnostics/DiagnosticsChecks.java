package dev.arachneledger.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Diagnostic observation and export must stay bounded, readable and independent of accounting. */
public final class DiagnosticsChecks {
    private static int checks;

    private static void yes(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }

    private static void same(Object expected, Object actual, String why) {
        checks++;
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    private static void immutable(Runnable change, String why) {
        checks++;
        try {
            change.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError(why);
    }

    private static void record(TrackingDiagnostics diagnostics, long now, String detail) {
        diagnostics.record(
                now,
                TrackingDiagnostics.Kind.LOOT,
                TrackingDiagnostics.Result.IGNORED,
                detail,
                "Already recorded");
    }

    public static void main(String[] args) {
        capacityAndSnapshots();
        consecutiveCoalescing();
        identitySuppressionAndClear();
        textBounds();
        reportIsolation();
        System.out.println(
                "PASS: "
                        + checks
                        + " diagnostic capacity, coalescing, identity and export checks.");
    }

    private static void capacityAndSnapshots() {
        TrackingDiagnostics diagnostics = new TrackingDiagnostics();
        same(List.of(), diagnostics.newestFirst(), "A fresh diagnostic log is empty");
        same(0L, diagnostics.revision(), "A fresh diagnostic log starts at revision zero");
        record(diagnostics, 1_000, "Initial observation");
        List<TrackingDiagnostics.Event> snapshot = diagnostics.newestFirst();
        immutable(snapshot::clear, "Observation snapshots cannot be cleared by callers");
        immutable(
                () -> snapshot.add(snapshot.getFirst()),
                "Observation snapshots cannot be appended by callers");
        for (int index = 0; index < TrackingDiagnostics.CAPACITY + 10; index++) {
            record(diagnostics, 2_000 + index, "Drop " + index);
        }
        List<TrackingDiagnostics.Event> retained = diagnostics.newestFirst();
        same(
                TrackingDiagnostics.CAPACITY,
                retained.size(),
                "Overflow retains only the bounded history");
        same(
                "Drop " + (TrackingDiagnostics.CAPACITY + 9),
                retained.getFirst().detail(),
                "Newest observations lead the display");
        same(
                "Drop 10",
                retained.getLast().detail(),
                "Overflow removes the oldest observations first");
        same(
                "Initial observation",
                snapshot.getFirst().detail(),
                "A captured snapshot never changes when the log grows");
        same(1, snapshot.size(), "Captured snapshot length is independent of future observations");
        same(
                (long) TrackingDiagnostics.CAPACITY + 11,
                diagnostics.revision(),
                "Every recorded observation advances the revision");
        yes(
                retained.stream().allMatch(event -> event.repeats() == 1),
                "Different drop identities remain distinct observations");
    }

    private static void consecutiveCoalescing() {
        TrackingDiagnostics diagnostics = new TrackingDiagnostics();
        record(diagnostics, 10_000, "Soul String x44");
        List<TrackingDiagnostics.Event> original = diagnostics.newestFirst();
        record(diagnostics, 10_100, "Soul String x44");
        record(diagnostics, 10_200, "Soul String x44");
        same(1, diagnostics.newestFirst().size(), "Consecutive identical scans occupy one row");
        TrackingDiagnostics.Event repeated = diagnostics.newestFirst().getFirst();
        same(10_000L, repeated.firstAt(), "A coalesced row retains its initial observation time");
        same(10_200L, repeated.at(), "A coalesced row records its latest observation time");
        same(3L, repeated.repeats(), "A coalesced row records every repeated scan");
        same(
                1L,
                original.getFirst().repeats(),
                "Coalescing cannot mutate an earlier snapshot event");
        same(3L, diagnostics.revision(), "Coalescing still invalidates the screen snapshot");

        diagnostics.record(
                10_300,
                TrackingDiagnostics.Kind.SPAWN,
                TrackingDiagnostics.Result.ACCEPTED,
                "Arachne",
                "Confirmed welcome");
        record(diagnostics, 10_400, "Soul String x44");
        same(
                3,
                diagnostics.newestFirst().size(),
                "An intervening event separates otherwise identical observations");
        diagnostics.record(
                10_500,
                TrackingDiagnostics.Kind.LOOT,
                TrackingDiagnostics.Result.ACCEPTED,
                "Soul String x44",
                "Already recorded");
        diagnostics.record(
                10_600,
                TrackingDiagnostics.Kind.LOOT,
                TrackingDiagnostics.Result.ACCEPTED,
                "Soul String x44",
                "Reward window open");
        diagnostics.record(
                10_700,
                TrackingDiagnostics.Kind.DAMAGE,
                TrackingDiagnostics.Result.ACCEPTED,
                "Soul String x44",
                "Reward window open");
        same(
                6,
                diagnostics.newestFirst().size(),
                "Different result, reason or event kind never coalesce");
        same(
                TrackingDiagnostics.Kind.DAMAGE,
                diagnostics.newestFirst().getFirst().kind(),
                "The latest event kind is retained");
    }

    private static void identitySuppressionAndClear() {
        TrackingDiagnostics diagnostics = new TrackingDiagnostics();
        diagnostics.once(
                "stand-a",
                1_000,
                TrackingDiagnostics.Kind.LOOT,
                TrackingDiagnostics.Result.IGNORED,
                "String x10",
                "Reward window closed");
        diagnostics.once(
                "stand-a",
                2_000,
                TrackingDiagnostics.Kind.LOOT,
                TrackingDiagnostics.Result.ACCEPTED,
                "String x20",
                "Changed metadata");
        same(
                1,
                diagnostics.newestFirst().size(),
                "An observed entity identity is logged only once despite changed metadata");
        same(1L, diagnostics.revision(), "Suppressed identities do not trigger screen rebuilds");
        same(
                "String x10",
                diagnostics.newestFirst().getFirst().detail(),
                "Identity suppression preserves the first observation");
        diagnostics.once(
                "stand-b",
                3_000,
                TrackingDiagnostics.Kind.LOOT,
                TrackingDiagnostics.Result.ACCEPTED,
                "Spider Eye x30",
                "Reward window open");
        same(
                2,
                diagnostics.newestFirst().size(),
                "Different entity identities are independently observed");
        List<TrackingDiagnostics.Event> beforeClear = diagnostics.newestFirst();
        long revision = diagnostics.revision();
        diagnostics.clear();
        same(List.of(), diagnostics.newestFirst(), "Clear removes every visible observation");
        same(revision + 1, diagnostics.revision(), "Clear advances the screen revision");
        same(2, beforeClear.size(), "Clear never changes previously captured snapshots");
        diagnostics.once(
                "stand-a",
                4_000,
                TrackingDiagnostics.Kind.LOOT,
                TrackingDiagnostics.Result.ACCEPTED,
                "String x10",
                "A fresh observation");
        same(1, diagnostics.newestFirst().size(), "Clear also releases identity suppression");

        diagnostics.clear();
        for (int index = 0; index <= TrackingDiagnostics.CAPACITY * 2; index++) {
            diagnostics.once(
                    "stand-" + index,
                    5_000 + index,
                    TrackingDiagnostics.Kind.LOOT,
                    TrackingDiagnostics.Result.IGNORED,
                    "Drop " + index,
                    "Outside reward window");
        }
        long boundedRevision = diagnostics.revision();
        diagnostics.once(
                "stand-" + TrackingDiagnostics.CAPACITY * 2,
                8_000,
                TrackingDiagnostics.Kind.LOOT,
                TrackingDiagnostics.Result.IGNORED,
                "Recent duplicate",
                "Outside reward window");
        same(
                boundedRevision,
                diagnostics.revision(),
                "Recent identities stay suppressed after capacity overflow");
        diagnostics.once(
                "stand-0",
                8_001,
                TrackingDiagnostics.Kind.LOOT,
                TrackingDiagnostics.Result.IGNORED,
                "Old identity reused",
                "Outside reward window");
        same(
                boundedRevision + 1,
                diagnostics.revision(),
                "Old identities are eventually evicted so suppression memory stays bounded");
        same(
                TrackingDiagnostics.CAPACITY,
                diagnostics.newestFirst().size(),
                "Identity churn cannot expand the observation log");
    }

    private static void textBounds() {
        TrackingDiagnostics diagnostics = new TrackingDiagnostics();
        diagnostics.record(
                1,
                TrackingDiagnostics.Kind.LOCATION,
                TrackingDiagnostics.Result.INFO,
                "Line\nTab\tNull\u0000Del\u007fEnd",
                "Reason\r\nText");
        TrackingDiagnostics.Event event = diagnostics.newestFirst().getFirst();
        same(
                "Line Tab Null Del End",
                event.detail(),
                "Control characters in observation details become spaces");
        same(
                "Reason  Text",
                event.reason(),
                "Control characters in observation reasons become spaces");
        diagnostics.record(
                2,
                TrackingDiagnostics.Kind.LOCATION,
                TrackingDiagnostics.Result.INFO,
                "Line Tab Null Del End",
                "Reason  Text");
        same(
                2L,
                diagnostics.newestFirst().getFirst().repeats(),
                "Equivalent sanitized messages coalesce");
        diagnostics.record(
                3,
                TrackingDiagnostics.Kind.DAMAGE,
                TrackingDiagnostics.Result.IGNORED,
                "x".repeat(1_000),
                "y".repeat(1_000));
        event = diagnostics.newestFirst().getFirst();
        same("x".repeat(220), event.detail(), "Details are capped at 220 characters");
        same("y".repeat(220), event.reason(), "Reasons are capped independently at 220 characters");
        diagnostics.record(
                4, TrackingDiagnostics.Kind.SESSION, TrackingDiagnostics.Result.INFO, null, null);
        event = diagnostics.newestFirst().getFirst();
        same("", event.detail(), "Absent detail becomes an empty string");
        same("", event.reason(), "Absent reason becomes an empty string");
    }

    private static void reportIsolation() {
        TrackingDiagnostics diagnostics = new TrackingDiagnostics();
        diagnostics.record(
                1_000,
                TrackingDiagnostics.Kind.SPAWN,
                TrackingDiagnostics.Result.ACCEPTED,
                "Arachne appeared",
                "Confirmed spawn");
        diagnostics.record(
                2_000,
                TrackingDiagnostics.Kind.LOOT,
                TrackingDiagnostics.Result.IGNORED,
                "Legendary Tarantula Pet",
                "Already recorded");
        diagnostics.record(
                2_100,
                TrackingDiagnostics.Kind.LOOT,
                TrackingDiagnostics.Result.IGNORED,
                "Legendary Tarantula Pet",
                "Already recorded");
        List<TrackingDiagnostics.Event> events = diagnostics.newestFirst();
        List<String> sidebar =
                new ArrayList<>(
                        List.of("SKYBLOCK", "Arachne's Sanctuary", "Purse: 40,806,301 (+2,317)"));
        List<String> tab = new ArrayList<>(List.of("Area: Spider's Den"));
        List<String> sidebarBefore = List.copyOf(sidebar);
        List<String> tabBefore = List.copyOf(tab);
        DetectionSnapshot state =
                new DetectionSnapshot(
                        "Tracking Arachne",
                        "Arachne's Sanctuary",
                        "Sidebar location matched",
                        true,
                        true,
                        true,
                        false,
                        "Between fights",
                        42,
                        "COUNTED",
                        123_456,
                        10_000,
                        true,
                        true,
                        12_345);
        long revision = diagnostics.revision();
        String report = DiagnosticReport.create("1.2.0", state, events, sidebar, tab, 3_000);
        List<String> expected =
                List.of(
                        "Arachne Ledger 1.2.0 tracking diagnostics",
                        "1970-01-01T00:00:03Z",
                        "Status: Tracking Arachne",
                        "Ready: true | SkyBlock: true | Sanctuary: true | Paused: false",
                        "Location: Arachne's Sanctuary",
                        "Reason: Sidebar location matched",
                        "Timer: Between fights",
                        "Fight: 42 / COUNTED",
                        "Damage: 123456 / 10000",
                        "Pickup window: true | Name-tag window: true",
                        "Reward milliseconds left: 12345",
                        "LOOT | IGNORED | Legendary Tarantula Pet | Already recorded (x2)",
                        "SPAWN | ACCEPTED | Arachne appeared | Confirmed spawn",
                        "Visible sidebar:\nSKYBLOCK\nArachne's Sanctuary",
                        "Purse: 40,806,301 (+2,317)",
                        "Visible tab list:\nArea: Spider's Den");
        for (String field : expected)
            yes(report.contains(field), "The exported report includes " + field);
        yes(
                report.indexOf("Legendary Tarantula Pet") < report.indexOf("Arachne appeared"),
                "Export preserves newest-first event order");
        same(events, diagnostics.newestFirst(), "Export never changes logged observations");
        same(revision, diagnostics.revision(), "Export does not trigger diagnostic changes");
        same(sidebarBefore, sidebar, "Export never mutates the supplied sidebar snapshot");
        same(tabBefore, tab, "Export never mutates the supplied tab snapshot");
        same(
                report,
                DiagnosticReport.create("1.2.0", state, events, sidebar, tab, 3_000),
                "The same snapshots produce a stable report");
        String empty =
                DiagnosticReport.create("1.2.0", state, List.of(), List.of(), List.of(), 3_000);
        yes(
                empty.contains("Recent observations (newest first):"),
                "Empty histories retain a clear section label");
        yes(empty.contains("Visible sidebar:\n\n"), "Empty sidebar snapshots remain readable");
        yes(
                !empty.contains("Legendary Tarantula Pet"),
                "Empty report snapshots do not leak an earlier export's events");
    }

    private DiagnosticsChecks() {}
}
