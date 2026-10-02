package dev.arachneledger;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Covers rarity identification, alert value and sequential title lifetime. */
public final class RngAlertsChecks {
    private static int checks;
    private static void yes(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    private static void eq(Object expected, Object value, String label) {
        checks++;
        if (!expected.equals(value)) throw new AssertionError(label + ": expected " + expected + ", got " + value);
    }
    private static void drop(String text, String item, int count) {
        var found = LootLabels.parse(text);
        yes(found != null, "Recognized pet/rare label " + text);
        eq(item, found.item(), "Item id for " + text);
        eq(count, found.count(), "Quantity for " + text);
    }
    public static void main(String[] args) {
        drop("§aArachne's Fang §8x2", "ARACHNE_FANG", 2);
        drop("Arachne Fang", "ARACHNE_FANG", 1);
        drop("§7[Lvl 1] §5Tarantula", "TARANTULA_EPIC", 1);
        drop("§7[Lvl 1] §6Tarantula §8x2", "TARANTULA_LEGENDARY", 2);
        drop("2x §5Tarantula Pet", "TARANTULA_EPIC", 2);
        drop("§5§lTarantula x3", "TARANTULA_EPIC", 3);
        drop("Tarantula Pet (Epic)", "TARANTULA_EPIC", 1);
        drop("Tarantula Pet (Legendary)", "TARANTULA_LEGENDARY", 1);
        yes(LootLabels.parse("[Lvl 1] Tarantula") == null, "Uncoloured pet does not guess rarity");
        yes(LootLabels.parse("§d[Lvl 1] Tarantula") == null, "Mythic colour does not imply Epic");
        yes(LootLabels.parse("§aTarantula") == null, "Unsupported tier ignored");
        yes(LootLabels.parse("§6[Lvl 1] §rTarantula") == null, "Colour reset prevents a false Legendary");
        yes(LootLabels.parse("§5Tarantula Broodfather 100,000❤") == null, "Mob label excluded");
        yes(LootLabels.parse("§6Tarantula x0") == null, "Zero quantity excluded");
        var component = Component.literal("[Lvl 1] ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("Tarantula").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" x2").withStyle(ChatFormatting.DARK_GRAY));
        drop(LootLabels.formatted(component), "TARANTULA_LEGENDARY", 2);
        var inherited = Component.empty().withStyle(ChatFormatting.DARK_PURPLE)
            .append(Component.literal("[Lvl 1] Tarantula"));
        drop(LootLabels.formatted(inherited), "TARANTULA_EPIC", 1);
        eq("", LootLabels.formatted(null), "Null component safe");

        eq(0xFF55FF55, RngAlerts.rarityColor("ARACHNE_FANG"), "Fang uses Uncommon green");
        eq(0xFFAA00AA, RngAlerts.rarityColor("TARANTULA_EPIC"), "Epic uses dark purple");
        eq(0xFFFFAA00, RngAlerts.rarityColor("TARANTULA_LEGENDARY"), "Legendary uses gold");
        eq(0, RngAlerts.rarityColor("STRING"), "Ordinary drops have no RNG title");
        RngAlerts alerts = new RngAlerts();
        long now = 1_000_000;
        yes(alerts.current(now) == null, "Initially empty");
        yes(!alerts.notice("STRING", 1, 1, now), "Regular loot rejected");
        yes(!alerts.notice("ARACHNE_SHARD", 1, 1, now), "Other loot rejected");
        yes(!alerts.notice("UNKNOWN_PET", 1, 1, now), "Unknown loot rejected");
        yes(!alerts.notice(null, 1, 1, now), "Null id rejected");
        yes(!alerts.notice("ARACHNE_FANG", 0, 1, now), "Empty reward rejected");
        yes(alerts.notice("ARACHNE_FANG", 2, 500, now), "Rare title accepted");
        eq("2x Arachne's Fang", alerts.current(now + 200).title(), "Quantity displayed");
        eq("+1.0k coins", alerts.current(now + 200).valueText(), "Value uses count times unit price");
        yes(alerts.current(now).alpha() == 0, "Fades in from zero");
        yes(alerts.current(now + 200).alpha() == 1, "Full opacity after fade in");
        yes(alerts.current(now + 3_650).alpha() == .5, "Fades out over last 700ms");
        yes(alerts.notice("TARANTULA_EPIC", 1, 5_000_000, now + 1), "Simultaneous pet queued");
        eq(2, alerts.queued(), "Both rare drops retained");
        eq("ARACHNE_FANG", alerts.current(now + 3999).item(), "First title remains before expiry");
        eq("TARANTULA_EPIC", alerts.current(now + 4000).item(), "Next title takes over at expiry");
        eq("+5.00m coins", alerts.current(now + 4200).valueText(), "Pet recorded value displayed");
        eq(1, alerts.queued(), "Expired first title removed");
        yes(alerts.current(now + 8000) == null, "Queue expires fully");
        yes(alerts.notice("TARANTULA_LEGENDARY", 1, 0, now + 9000), "Unpriced pet still alerts");
        eq("Unpriced", alerts.current(now + 9200).valueText(), "Unknown value is not displayed as zero profit");
        alerts.clear();
        eq(0, alerts.queued(), "Context reset empties queue");
        alerts.notice("ARACHNE_FANG", 1, Double.NaN, now);
        eq("Unpriced", alerts.current(now + 200).valueText(), "Non-finite price safe");
        alerts.clear();
        alerts.notice("ARACHNE_FANG", 1, -1, now);
        eq("Unpriced", alerts.current(now + 200).valueText(), "Negative price safe");
        alerts.clear();
        for (int i = 0; i < 8; i++) yes(alerts.notice("ARACHNE_FANG", 1, 1, now), "Bounded queue accepts reward " + i);
        yes(!alerts.notice("ARACHNE_FANG", 1, 1, now), "Queue protects against unbounded titles");
        eq(8, alerts.queued(), "Queue bound preserved");

        alerts.clear();
        alerts.notice("TARANTULA_LEGENDARY", 1, 5_000_000, now);
        alerts.setVisible(false, now + 500);
        eq("TARANTULA_LEGENDARY", alerts.current(now + 20_000).item(), "An open menu cannot expire an unseen title");
        yes(alerts.current(now + 20_000).alpha() == 1, "A hidden title keeps its visible fade position");
        alerts.setVisible(true, now + 20_000);
        yes(alerts.current(now + 23_499) != null, "Already-visible time remains part of the four-second lifetime");
        yes(alerts.current(now + 23_500) == null, "Title expires after four actual visible seconds");

        alerts.clear();
        alerts.setVisible(false, now);
        alerts.notice("TARANTULA_EPIC", 1, 2_000_000, now + 1_000);
        alerts.notice("ARACHNE_FANG", 1, 100, now + 2_000);
        alerts.setVisible(false, now + 10_000);
        alerts.setVisible(true, now + 20_000);
        eq("TARANTULA_EPIC", alerts.current(now + 20_200).item(), "A reward arriving in a menu starts when gameplay returns");
        eq(2, alerts.queued(), "Hidden rewards keep their sequential queue");
        eq("ARACHNE_FANG", alerts.current(now + 24_200).item(), "Second hidden reward gets its own four-second slot");
        yes(alerts.current(now + 28_000) == null, "Both deferred titles expire after their visible slots");
        alerts.setVisible(false, now + 30_000);
        alerts.clear();
        alerts.notice("ARACHNE_FANG", 1, 100, now + 31_000);
        yes(alerts.current(now + 35_000) == null, "Context clear resets the deferred clock as well as the queue");
        System.out.println("PASS: " + checks + " rare-drop title and pet-label checks.");
    }
    private RngAlertsChecks() {}
}
