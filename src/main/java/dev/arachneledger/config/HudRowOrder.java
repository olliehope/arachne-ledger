package dev.arachneledger.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Stable display keys. New rows join old configurations without replacing a custom order. */
public final class HudRowOrder {
    public enum Group {
        REWARDS("Rewards"),
        METRICS("Summary");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Definition(String id, String label, Group group) {}

    private static final List<Definition> ROWS =
            List.of(
                    new Definition("loot", "Loot items", Group.REWARDS),
                    new Definition("scavenger", "Scavenger coins", Group.REWARDS),
                    new Definition("crystalCosts", "Crystal costs", Group.REWARDS),
                    new Definition("callingCosts", "Calling costs", Group.REWARDS),
                    new Definition("kills", "Bosses killed", Group.REWARDS),
                    new Definition("profit", "Total profit", Group.METRICS),
                    new Definition("hourly", "Profit / hour", Group.METRICS),
                    new Definition("projectedHourly", "Projected / hour", Group.METRICS),
                    new Definition("activeTime", "Active time", Group.METRICS),
                    new Definition("scope", "Session / total", Group.METRICS),
                    new Definition("status", "Tracking status", Group.METRICS),
                    new Definition("unpriced", "Unpriced warning", Group.METRICS),
                    new Definition("regularProfit", "Profit without RNG", Group.METRICS),
                    new Definition("regularHourly", "Without RNG / hour", Group.METRICS));

    private static final Set<String> SUPPORTED =
            Set.copyOf(ROWS.stream().map(Definition::id).toList());

    public static List<String> defaultOrder() {
        return new ArrayList<>(ROWS.stream().map(Definition::id).toList());
    }

    /** Loot first, then the compact activity and coin summary. Custom orders remain independent. */
    public static List<String> lootLedgerOrder() {
        return normalize(
                List.of(
                        "loot",
                        "kills",
                        "scavenger",
                        "crystalCosts",
                        "callingCosts",
                        "profit",
                        "hourly",
                        "activeTime",
                        "projectedHourly",
                        "regularProfit",
                        "regularHourly",
                        "scope",
                        "status",
                        "unpriced"));
    }

    /**
     * Preserve supported custom keys, discard damaged entries, then append newly available rows.
     */
    public static List<String> normalize(List<String> saved) {
        Set<String> result = new LinkedHashSet<>();
        if (saved != null) {
            for (String id : saved) {
                if (id != null && SUPPORTED.contains(id)) {
                    result.add(id);
                }
            }
        }
        result.addAll(defaultOrder());
        return new ArrayList<>(result);
    }

    /** Ordering within each group keeps the Split layout's reward and summary columns distinct. */
    public static List<Definition> rows(List<String> saved, Group group) {
        List<String> normalized = normalize(saved);
        return normalized.stream()
                .flatMap(id -> ROWS.stream().filter(row -> row.id().equals(id)))
                .filter(row -> row.group() == group)
                .toList();
    }

    public static boolean canMove(List<String> saved, String id, int direction) {
        Definition definition = definition(id);
        if (definition == null || direction == 0) {
            return false;
        }
        List<String> group = rows(saved, definition.group()).stream().map(Definition::id).toList();
        int target = group.indexOf(id) + Integer.signum(direction);
        return target >= 0 && target < group.size();
    }

    /** Return a fresh editable order; hidden rows keep their positions until enabled again. */
    public static List<String> move(List<String> saved, String id, int direction) {
        List<String> result = normalize(saved);
        if (!canMove(result, id, direction)) {
            return result;
        }
        Definition definition = definition(id);
        List<String> group = rows(result, definition.group()).stream().map(Definition::id).toList();
        String neighbor = group.get(group.indexOf(id) + Integer.signum(direction));
        int from = result.indexOf(id);
        int to = result.indexOf(neighbor);
        result.set(from, neighbor);
        result.set(to, id);
        return result;
    }

    private static Definition definition(String id) {
        return ROWS.stream().filter(row -> row.id().equals(id)).findFirst().orElse(null);
    }

    private HudRowOrder() {}
}
