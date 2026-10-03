package dev.arachneledger.achievement;

import dev.arachneledger.achievement.AchievementDefinition.Category;
import dev.arachneledger.achievement.AchievementDefinition.Metric;
import dev.arachneledger.ledger.Ledger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Data-driven milestones. Evaluation changes only persistent unlock state, never accounting. */
public final class Achievements {
    private static final int CATALOG_VERSION = 2;
    public static final List<AchievementDefinition> DEFINITIONS = definitions();

    public record Unlock(AchievementDefinition definition, long earnedAt) {
        public String title() {
            return definition.title();
        }
    }

    public record Update(List<Unlock> unlocks, boolean changed) {
        public Update {
            unlocks = List.copyOf(unlocks);
        }
    }

    public record Progress(
            AchievementDefinition definition, long value, boolean earned, long earnedAt) {
        public double fraction() {
            return earned ? 1 : definition.fraction(value);
        }

        public boolean revealed() {
            return !definition.hidden() || earned;
        }
    }

    public record Snapshot(List<Progress> progress, int earned, int total) {
        public Snapshot {
            progress = List.copyOf(progress);
        }
    }

    /**
     * Loading existing history and adding a registry version both backfill silently. Live unlocks
     * are acknowledged even when notifications are disabled, so enabling them never replays
     * history. Call before saving; only deliver returned notifications after the save succeeds.
     */
    public static Update evaluate(Ledger ledger, AchievementState state, long now, boolean notify) {
        AchievementFacts facts = AchievementFacts.from(ledger);
        boolean backfill = !state.initialized || state.catalogVersion != CATALOG_VERSION;
        boolean changed = backfill;
        List<Unlock> unlocks = new ArrayList<>();
        for (AchievementDefinition definition : DEFINITIONS) {
            String id = definition.id();
            if (definition.reached(facts.value(definition.metric()))
                    && !state.earnedAt.containsKey(id)) {
                // Zero marks an imported achievement with no known historical unlock date.
                state.earnedAt.put(id, backfill ? 0 : Math.max(0, now));
                changed = true;
            }
            if (state.earnedAt.containsKey(id) && state.notified.add(id)) {
                changed = true;
                if (!backfill && notify) {
                    unlocks.add(new Unlock(definition, state.earnedAt.get(id)));
                }
            }
        }
        if (changed) {
            state.initialized = true;
            state.catalogVersion = CATALOG_VERSION;
            state.changed();
        }
        return new Update(unlocks, changed);
    }

    public static Snapshot snapshot(Ledger ledger, AchievementState state) {
        AchievementFacts facts = AchievementFacts.from(ledger);
        List<Progress> progress = new ArrayList<>();
        int earned = 0;
        for (AchievementDefinition definition : DEFINITIONS) {
            boolean unlocked = state.earnedAt.containsKey(definition.id());
            if (unlocked) {
                earned++;
            }
            progress.add(
                    new Progress(
                            definition,
                            facts.value(definition.metric()),
                            unlocked,
                            state.earnedAt.getOrDefault(definition.id(), 0L)));
        }
        return new Snapshot(progress, earned, DEFINITIONS.size());
    }

    private static List<AchievementDefinition> definitions() {
        List<AchievementDefinition> definitions = new ArrayList<>();
        tiers(
                definitions,
                "kills",
                "Spider Exterminator",
                "Complete %s counted Arachne fights.",
                Category.HUNTING,
                Metric.COUNTED_KILLS,
                new long[] {10, 100, 1_000});
        tiers(
                definitions,
                "crystals",
                "Summoner",
                "Place %s of your own Arachne Crystals.",
                Category.SUMMONING,
                Metric.CRYSTAL_PLACEMENTS,
                new long[] {10, 100, 500});
        tiers(
                definitions,
                "silk",
                "Silk Merchant",
                "Collect %s Soul String.",
                Category.COLLECTION,
                Metric.SOUL_STRING,
                new long[] {1_000, 10_000, 100_000});
        definitions.add(
                new AchievementDefinition(
                        "pet_first",
                        "Lucky Legs",
                        "Find your first Tarantula Pet.",
                        Category.RARE,
                        Metric.PETS,
                        1,
                        false));
        tiers(
                definitions,
                "fangs",
                "Fang Collector",
                "Collect %s Arachne Fangs.",
                Category.RARE,
                Metric.FANGS,
                new long[] {1, 10, 100});
        definitions.add(
                new AchievementDefinition(
                        "pet_legendary",
                        "Eight-Legged Legend",
                        "Find a Legendary Tarantula Pet.",
                        Category.RARE,
                        Metric.LEGENDARY_PETS,
                        1,
                        true));
        tiers(
                definitions,
                "pet_dry",
                "Looking for a Pet",
                "Complete %s counted Arachne fights in a row without a Tarantula Pet.",
                Category.RARE,
                Metric.PET_DRY_STREAK,
                new long[] {100, 500, 1_000});
        tiers(
                definitions,
                "fang_dry",
                "Fangless",
                "Complete %s counted Arachne fights in a row without an Arachne Fang.",
                Category.RARE,
                Metric.FANG_DRY_STREAK,
                new long[] {50, 100, 250});
        long[] seconds = {60, 45, 30};
        for (int index = 0; index < seconds.length; index++) {
            definitions.add(
                    new AchievementDefinition(
                            "speed_" + seconds[index],
                            "Speed Weaver " + roman(index),
                            "Complete a qualifying fight in "
                                    + seconds[index]
                                    + " seconds or less.",
                            Category.SPEED,
                            Metric.FASTEST_KILL,
                            seconds[index] * 1_000,
                            false));
        }
        Set<String> ids = new HashSet<>();
        for (AchievementDefinition definition : definitions) {
            if (!ids.add(definition.id())) {
                throw new IllegalStateException("Duplicate achievement ID: " + definition.id());
            }
        }
        return List.copyOf(definitions);
    }

    private static void tiers(
            List<AchievementDefinition> definitions,
            String id,
            String name,
            String description,
            Category category,
            Metric metric,
            long[] targets) {
        for (int index = 0; index < targets.length; index++) {
            long target = targets[index];
            definitions.add(
                    new AchievementDefinition(
                            id + "_" + target,
                            name + " " + roman(index),
                            description.formatted(
                                    String.format(java.util.Locale.ROOT, "%,d", target)),
                            category,
                            metric,
                            target,
                            false));
        }
    }

    private static String roman(int index) {
        return switch (index) {
            case 0 -> "I";
            case 1 -> "II";
            case 2 -> "III";
            default -> Integer.toString(index + 1);
        };
    }

    private Achievements() {}
}
