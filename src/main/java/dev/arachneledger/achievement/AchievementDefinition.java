package dev.arachneledger.achievement;

/** One milestone in the registry; adding a tier does not require another handler or screen. */
public record AchievementDefinition(
        String id,
        String title,
        String description,
        Category category,
        Metric metric,
        long target,
        boolean hidden) {
    public enum Category {
        HUNTING("Hunting"),
        SUMMONING("Summoning"),
        COLLECTION("Collection"),
        RARE("Rare drops"),
        SPEED("Speed");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Metric {
        COUNTED_KILLS,
        CRYSTAL_PLACEMENTS,
        SOUL_STRING,
        PETS,
        FANGS,
        LEGENDARY_PETS,
        FASTEST_KILL,
        PET_DRY_STREAK,
        FANG_DRY_STREAK
    }

    public AchievementDefinition {
        if (id == null
                || id.isBlank()
                || title == null
                || title.isBlank()
                || description == null
                || category == null
                || metric == null
                || target < 1) {
            throw new IllegalArgumentException("Invalid achievement definition.");
        }
    }

    public boolean reached(long value) {
        return metric == Metric.FASTEST_KILL ? value > 0 && value <= target : value >= target;
    }

    public double fraction(long value) {
        if (value <= 0) {
            return 0;
        }
        return Math.min(
                1,
                metric == Metric.FASTEST_KILL ? (double) target / value : (double) value / target);
    }
}
