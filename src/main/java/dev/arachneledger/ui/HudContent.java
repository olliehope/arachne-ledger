package dev.arachneledger.ui;

import dev.arachneledger.config.HudPreferences;
import dev.arachneledger.config.HudRowOrder;
import dev.arachneledger.ledger.Analytics;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.ledger.ProfitBreakdown;
import dev.arachneledger.ledger.RngSince;
import dev.arachneledger.skyblock.Catalog;
import dev.arachneledger.tracking.Tracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Builds display rows from journal totals. Hiding a row never removes a receipt or changes a rate.
 */
public final class HudContent {
    /** A text segment lets compact rows retain rarity, quantity and rate colors independently. */
    public record Part(String text, int color) {}

    public record Row(
            String id,
            String label,
            String value,
            int labelColor,
            int valueColor,
            List<Part> parts) {
        public Row {
            parts = List.copyOf(parts);
        }

        public Row(String id, String label, String value, int labelColor, int valueColor) {
            this(id, label, value, labelColor, valueColor, List.of());
        }
    }

    public record Snapshot(List<Row> rewards, List<Row> metrics) {
        public Snapshot {
            rewards = List.copyOf(rewards);
            metrics = List.copyOf(metrics);
        }
    }

    public static Snapshot build(Tracker tracker) {
        HudPreferences preferences = tracker.config.hudPreferences;
        boolean lootLedger = preferences.layout == HudPreferences.Layout.LOOT;
        Ledger.Stats stats = tracker.ledger.stats(tracker.config.total);
        Analytics.Snapshot analytics = tracker.ledger.analytics(tracker.config.total);
        List<Row> rewards = new ArrayList<>();
        List<Row> metrics = new ArrayList<>();
        RngSince.Snapshot rareHistory =
                lootLedger && preferences.showLoot && preferences.showRareRates
                        ? tracker.rngSince(tracker.config.total)
                        : null;
        addLoot(rewards, preferences, stats, analytics, rareHistory);
        if (preferences.showScavenger) {
            rewards.add(
                    displayMetric(
                            "scavenger",
                            lootLedger ? "Coins" : "Scavenger coins",
                            Format.coins(tracker.ledger.scavengerCoins(tracker.config.total)),
                            Hud.GOLD,
                            lootLedger,
                            ""));
        }
        if (preferences.showCrystalCosts) {
            rewards.add(
                    metric(
                            "crystalCosts",
                            "Crystal costs (" + stats.crystals() + ")",
                            Format.coins(analytics.crystalSpend()),
                            Graph.RED));
        }
        if (preferences.showCallingCosts) {
            rewards.add(
                    metric(
                            "callingCosts",
                            "Calling costs (" + stats.callings() + ")",
                            Format.coins(analytics.callingSpend()),
                            Graph.RED));
        }
        if (preferences.showKills) {
            String rate =
                    lootLedger && preferences.showKillsPerHour
                            ? stats.elapsed() < 1000
                                    ? "--"
                                    : Hud.decimal(stats.kills() * 3_600_000.0 / stats.elapsed())
                            : "";
            rewards.add(
                    displayMetric(
                            "kills",
                            lootLedger ? "Total bosses" : "Bosses killed",
                            Long.toString(stats.kills()),
                            Hud.TITLE,
                            lootLedger,
                            rate));
        }
        if (preferences.showTotalProfit) {
            metrics.add(
                    displayMetric(
                            "profit",
                            lootLedger ? "Profit" : "Total profit",
                            Format.coins(stats.profit()),
                            stats.profit() >= 0 ? Graph.GREEN : Graph.RED,
                            lootLedger,
                            lootLedger && preferences.showProfitPerHour
                                    ? stats.elapsed() < 1000 ? "--" : Format.coins(stats.hourly())
                                    : ""));
        }
        if (preferences.showProfitPerHour && !(lootLedger && preferences.showTotalProfit)) {
            metrics.add(
                    metric(
                            "hourly",
                            "Profit / hour",
                            stats.elapsed() < 1000 ? "--" : Format.coins(stats.hourly()),
                            Hud.GOLD));
        }
        if (preferences.showProjectedPerHour) {
            metrics.add(
                    metric(
                            "projectedHourly",
                            "Projected / hour",
                            analytics.projectionReady()
                                    ? Format.coins(analytics.projectedHourly())
                                    : "--",
                            analytics.projectionReady() ? Hud.GOLD : Graph.MUTED));
        }
        if (preferences.showRegularProfit || preferences.showRegularPerHour) {
            ProfitBreakdown.Snapshot profit = tracker.ledger.profitBreakdown(tracker.config.total);
            if (preferences.showRegularProfit) {
                metrics.add(
                        metric(
                                "regularProfit",
                                "Profit without RNG",
                                Format.coins(profit.ordinaryNet()),
                                profit.ordinaryNet() >= 0 ? Graph.GREEN : Graph.RED));
            }
            if (preferences.showRegularPerHour) {
                metrics.add(
                        metric(
                                "regularHourly",
                                "Without RNG / hour",
                                profit.elapsed() < 1000
                                        ? "--"
                                        : Format.coins(profit.ordinaryHourly()),
                                Hud.GOLD));
            }
        }
        if (preferences.showActiveTime) {
            metrics.add(
                    displayMetric(
                            "activeTime",
                            lootLedger ? "Playtime" : "Active time",
                            Hud.shortTime(stats.elapsed()),
                            0xFF55FFFF,
                            lootLedger,
                            ""));
        }
        if (preferences.showScope) {
            metrics.add(
                    metric(
                            "scope",
                            "Display",
                            tracker.config.total ? "Total" : "This session",
                            Graph.MUTED));
        }
        if (preferences.showStatus) {
            metrics.add(metric("status", "Status", status(tracker), Graph.MUTED));
        }
        if (preferences.showUnpricedWarning && stats.unpriced() > 0) {
            metrics.add(
                    new Row(
                            "unpriced",
                            stats.unpriced()
                                    + " unpriced drop"
                                    + (stats.unpriced() == 1 ? "" : "s"),
                            "",
                            Hud.TITLE,
                            Hud.TITLE));
        }
        List<String> order = HudRowOrder.normalize(preferences.rowOrder);
        Comparator<Row> byDisplayOrder =
                Comparator.comparingInt(row -> order.indexOf(orderKey(row)));
        rewards.sort(byDisplayOrder);
        metrics.sort(byDisplayOrder);
        return new Snapshot(rewards, metrics);
    }

    private static String orderKey(Row row) {
        return row.id().startsWith("loot:") || row.id().equals("emptyLoot") ? "loot" : row.id();
    }

    /** Filters and sorting affect the HUD list only; all loot remains in the selected totals. */
    public static List<String> visibleLoot(
            HudPreferences preferences, Ledger.Stats stats, Analytics.Snapshot analytics) {
        if (!preferences.showLoot || preferences.maxLootRows == 0) {
            return List.of();
        }
        Comparator<String> order =
                switch (preferences.sort) {
                    case VALUE ->
                            Comparator.<String>comparingDouble(
                                            id -> analytics.lootRevenue().getOrDefault(id, 0.0))
                                    .reversed();
                    case QUANTITY ->
                            Comparator.<String>comparingLong(
                                            id -> stats.loot().getOrDefault(id, 0L))
                                    .reversed();
                    case NAME -> Comparator.comparing(Catalog::name, String.CASE_INSENSITIVE_ORDER);
                };
        return stats.loot().keySet().stream()
                .filter(id -> !preferences.hiddenItems.contains(id))
                .sorted(order.thenComparing(id -> id))
                .limit(preferences.maxLootRows)
                .toList();
    }

    private static void addLoot(
            List<Row> rows,
            HudPreferences preferences,
            Ledger.Stats stats,
            Analytics.Snapshot analytics,
            RngSince.Snapshot rareHistory) {
        if (!preferences.showLoot || preferences.maxLootRows == 0) {
            return;
        }
        List<String> items = visibleLoot(preferences, stats, analytics);
        for (String id : items) {
            double value = analytics.lootRevenue().getOrDefault(id, 0.0);
            String price = preferences.showLootValues && value > 0 ? Format.coins(value) : "";
            if (preferences.layout == HudPreferences.Layout.LOOT) {
                List<Part> parts = new ArrayList<>();
                parts.add(new Part(Catalog.name(id) + ": ", Hud.itemColor(id)));
                parts.add(
                        new Part(
                                String.format(Locale.ROOT, "%,d", stats.loot().get(id)),
                                0xFF55FFFF));
                RngSince.Reward rare = rareReward(id);
                if (rareHistory != null && rare != null && rareHistory.qualifiedKills() > 0) {
                    parts.add(
                            new Part(
                                    String.format(
                                            Locale.ROOT,
                                            " (%.2f%%)",
                                            rareHistory.row(rare).dropsPer100Kills()),
                                    0xFF55FFFF));
                }
                rows.add(
                        new Row(
                                "loot:" + id,
                                Catalog.name(id),
                                price,
                                Hud.itemColor(id),
                                Hud.GOLD,
                                parts));
                continue;
            }
            String label =
                    String.format(Locale.ROOT, "%,dx ", stats.loot().get(id)) + Catalog.name(id);
            rows.add(new Row("loot:" + id, label, price, Hud.itemColor(id), Hud.GOLD));
        }
        if (items.isEmpty()) {
            rows.add(
                    new Row(
                            "emptyLoot",
                            stats.loot().isEmpty() ? "No drops recorded yet" : "No visible drops",
                            "",
                            Graph.MUTED,
                            Graph.MUTED));
        }
    }

    private static Row metric(String id, String label, String value, int color) {
        return new Row(id, label, value, Graph.MUTED, color);
    }

    private static Row displayMetric(
            String id, String label, String value, int color, boolean compact, String hourly) {
        if (!compact) {
            return metric(id, label, value, color);
        }
        List<Part> parts = new ArrayList<>();
        parts.add(new Part(label + ": ", Hud.WHITE));
        parts.add(new Part(value, color));
        String rate = hourly.isEmpty() ? "" : " [" + hourly + "/hr]";
        if (!rate.isEmpty()) {
            parts.add(new Part(rate, Graph.MUTED));
        }
        return new Row(id, label, value + rate, Hud.WHITE, color, parts);
    }

    private static RngSince.Reward rareReward(String item) {
        return switch (item) {
            case "TARANTULA_EPIC" -> RngSince.Reward.EPIC_PET;
            case "TARANTULA_LEGENDARY" -> RngSince.Reward.LEGENDARY_PET;
            case "ARACHNE_FANG" -> RngSince.Reward.FANG;
            default -> null;
        };
    }

    private static String status(Tracker tracker) {
        if (tracker.config.paused) {
            return "Paused";
        }
        if (!tracker.inArena) {
            return "Waiting";
        }
        if (tracker.isSummoning()) {
            return "Summoning";
        }
        if (tracker.isAfk()) {
            return "AFK";
        }
        return tracker.waitingForSpawn() ? "Waiting for spawn" : "Tracking";
    }

    private HudContent() {}
}
