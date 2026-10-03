package dev.arachneledger.achievement;

import dev.arachneledger.achievement.AchievementDefinition.Metric;
import dev.arachneledger.ledger.HistoryIndex;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.ledger.RngSince;

/** Proven lifetime observations; manual corrections and prices cannot manufacture progression. */
public record AchievementFacts(
        long countedKills,
        long crystalPlacements,
        long soulString,
        long pets,
        long fangs,
        long legendaryPets,
        long fastestKill,
        long petDryStreak,
        long fangDryStreak) {

    public AchievementFacts(
            long countedKills,
            long crystalPlacements,
            long soulString,
            long pets,
            long fangs,
            long legendaryPets,
            long fastestKill) {
        this(
                countedKills,
                crystalPlacements,
                soulString,
                pets,
                fangs,
                legendaryPets,
                fastestKill,
                0,
                0);
    }

    public static AchievementFacts from(Ledger ledger) {
        HistoryIndex.Snapshot history = HistoryIndex.build(ledger);
        long countedKills = 0, crystals = 0, silk = 0, pets = 0, fangs = 0, legendary = 0;
        long fastest = -1;
        for (HistoryIndex.Fight fight : history.fights()) {
            if (fight.qualifying()) {
                countedKills = plus(countedKills, 1);
                long duration = fight.duration();
                if (duration > 0 && (fastest < 0 || duration < fastest)) {
                    fastest = duration;
                }
            }
        }
        for (Ledger.Entry entry : history.entries()) {
            if (entry.kind() == Ledger.Kind.KILL
                    && entry.fightId() == 0
                    && "ARACHNE".equals(entry.item())
                    && "server".equals(entry.source())) {
                // Pre-fight-history journals prove a counted kill, but provide no trustworthy time.
                countedKills = plus(countedKills, entry.count());
            }
            if (entry.kind() == Ledger.Kind.CRYSTAL
                    && "ARACHNE_CRYSTAL".equals(entry.item())
                    && "server".equals(entry.source())) {
                crystals = plus(crystals, entry.count());
            }
            if (entry.kind() != Ledger.Kind.LOOT || !HistoryIndex.isDetectedReward(entry)) {
                continue;
            }
            switch (entry.item()) {
                case "SOUL_STRING" -> silk = plus(silk, entry.count());
                case "ARACHNE_FANG" -> fangs = plus(fangs, entry.count());
                case "TARANTULA_EPIC" -> pets = plus(pets, entry.count());
                case "TARANTULA_LEGENDARY" -> {
                    pets = plus(pets, entry.count());
                    legendary = plus(legendary, entry.count());
                }
                default -> {
                    // Registry metrics deliberately ignore unrelated reward quantities.
                }
            }
        }
        return new AchievementFacts(
                countedKills,
                crystals,
                silk,
                pets,
                fangs,
                legendary,
                fastest,
                RngSince.longestDryStreak(history, RngSince.Reward.ANY_PET),
                RngSince.longestDryStreak(history, RngSince.Reward.FANG));
    }

    public long value(Metric metric) {
        return switch (metric) {
            case COUNTED_KILLS -> countedKills;
            case CRYSTAL_PLACEMENTS -> crystalPlacements;
            case SOUL_STRING -> soulString;
            case PETS -> pets;
            case FANGS -> fangs;
            case LEGENDARY_PETS -> legendaryPets;
            case FASTEST_KILL -> fastestKill;
            case PET_DRY_STREAK -> petDryStreak;
            case FANG_DRY_STREAK -> fangDryStreak;
        };
    }

    private static long plus(long total, long quantity) {
        return quantity <= 0
                ? total
                : quantity > Long.MAX_VALUE - total ? Long.MAX_VALUE : total + quantity;
    }
}
