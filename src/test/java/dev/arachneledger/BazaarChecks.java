package dev.arachneledger;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Pricing snapshots and migration checks without network access or a running Minecraft client. */
public final class BazaarChecks {
    private static int checks;
    private static void yes(boolean condition, String why) {
        checks++; if (!condition) throw new AssertionError(why);
    }
    private static void eq(double expected, double actual, String why) {
        checks++; if (!Double.isFinite(actual) || Math.abs(expected-actual) > .00001)
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
    }
    private static void fails(Runnable task, String why) {
        checks++;
        try { task.run(); } catch (IllegalArgumentException ex) { return; }
        throw new AssertionError(why);
    }
    private static String product(String id, String price, long orders) {
        return "\"" + id + "\":{\"product_id\":\"" + id + "\",\"quick_status\":{\"productId\":\""
            + id + "\",\"sellPrice\":" + price + ",\"buyPrice\":123456,\"sellOrders\":" + orders + "}}";
    }
    private static String feed(long at, String products) {
        return "{\"success\":true,\"lastUpdated\":" + at + ",\"products\":{" + products + "}}";
    }
    public static void main(String[] args) throws Exception {
        long now = 1_790_769_600_000L;
        var snapshot = BazaarPrices.parse(feed(now-1000,
            product("SOUL_STRING", "6100.25", 10) + "," + product("ENCHANTED_STRING", "900", 3)
                + "," + product("TARANTULA_LEGENDARY", "999999", 1)), now);
        eq(6100.25, snapshot.prices().get("SOUL_STRING"), "Instant-sell valuation uses sellPrice, not buyPrice");
        yes(!snapshot.prices().containsKey("TARANTULA_LEGENDARY"), "Pets are never guessed from Bazaar data");
        eq(now-1000, snapshot.updatedAt(), "Epoch timestamps above a trillion are valid");
        fails(() -> BazaarPrices.parse("not json", now), "Malformed response rejected");
        fails(() -> BazaarPrices.parse("{}", now), "Missing success rejected");
        fails(() -> BazaarPrices.parse("{\"success\":false}", now), "API failure rejected");
        fails(() -> BazaarPrices.parse("{\"success\":\"true\"}", now), "String success cannot masquerade as boolean");
        fails(() -> BazaarPrices.parse(feed(now-1_800_001, product("SOUL_STRING", "1", 1)), now), "Stale server snapshot rejected");
        fails(() -> BazaarPrices.parse(feed(now+60_001, product("SOUL_STRING", "1", 1)), now), "Future snapshot rejected");
        fails(() -> BazaarPrices.parse(feed(now, ""), now), "Empty products rejected");
        fails(() -> BazaarPrices.parse(feed(now, product("SOUL_STRING", "-1", 1)), now), "Negative price rejected");
        fails(() -> BazaarPrices.parse(feed(now, product("SOUL_STRING", "1e999", 1)), now), "Infinite price rejected");
        fails(() -> BazaarPrices.parse(feed(now, product("SOUL_STRING", "\"5\"", 1)), now), "String price rejected");
        fails(() -> BazaarPrices.parse(feed(now, product("SOUL_STRING", "1", 0)), now), "No orders means no instant-sell estimate");
        fails(() -> BazaarPrices.parse(feed(now, product("SOUL_STRING", "0", 1)), now), "Zero-price market cannot wipe a fallback");
        var partial = BazaarPrices.parse(feed(now, product("SOUL_STRING", "6", 1)
            + "," + product("STRING", "-1", 1)), now);
        yes(partial.prices().size() == 1, "One malformed product does not remove usable prices");

        Config config = new Config(); config.validate();
        config.bazaarPrices = new LinkedHashMap<>(snapshot.prices()); config.bazaarUpdatedAt = now-1000;
        eq(5000, config.price("SOUL_STRING"), "Auto pricing is off by default");
        yes(config.rngTitles && config.rngValue && !config.dashboardFights, "Alert and dashboard settings have migration defaults");
        config.autoBazaar = true;
        eq(5000, config.price("SOUL_STRING"), "Migrated manual value wins over new Bazaar value");
        config.clearManual("SOUL_STRING");
        eq(6100.25, config.price("SOUL_STRING"), "Clearing override enables Bazaar valuation");
        yes(config.priceSource("SOUL_STRING").equals("Bazaar"), "Price source identifies automatic data");
        config.manualSet("SOUL_STRING", 7000);
        eq(7000, config.price("SOUL_STRING"), "Explicit manual override takes priority");
        config.manualSet("SOUL_STRING", 0);
        eq(0, config.price("SOUL_STRING"), "Manual zero remains intentional");
        config.clearManual("SOUL_STRING"); config.autoBazaar = false;
        eq(0, config.price("SOUL_STRING"), "Turning Auto off uses saved manual fallback");
        config.manualSet("TARANTULA_LEGENDARY", 3_000_000);
        config.useBazaarItems();
        eq(3_000_000, config.price("TARANTULA_LEGENDARY"), "Bulk Bazaar opt-in preserves Auction-only overrides");
        eq(6100.25, config.price("SOUL_STRING"), "Bulk opt-in uses supported automatic price");
        eq(500, config.price("ARACHNE_FRAGMENT"), "Bulk opt-in preserves NPC recipe ingredient value");
        eq(2*500 + 16*480 + 16*900, config.recipeCost(), "Recipe uses the same effective ingredient prices");
        config.crystalConfigured = true; config.crystalCost = 1234;
        eq(1234, config.effectiveCrystalCost(), "Explicit crystal cost is not changed by Bazaar");
        config.clearManual("LUXURIOUS_SPOOL");
        yes(config.priceSource("LUXURIOUS_SPOOL").equals("Unpriced"), "Unsupported market has an honest unpriced fallback");
        fails(() -> config.manualSet("UNKNOWN", 1), "Unknown manual item rejected");
        fails(() -> config.manualSet("STRING", -1), "Invalid manual amount rejected");

        AtomicInteger fetches = new AtomicInteger();
        BazaarPrices service = new BazaarPrices(() -> { fetches.incrementAndGet(); return snapshot; }, Runnable::run);
        Config automatic = new Config(); automatic.validate(); automatic.useBazaarItems();
        yes(!service.tick(automatic, now), "Starting a request does not block for or apply its result");
        yes(service.refreshing(), "Outstanding request exposed to UI");
        yes(service.tick(automatic, now+1), "Completed result is applied on a later client tick");
        eq(6100.25, automatic.price("SOUL_STRING"), "Applied market snapshot values future loot");
        eq(5000, automatic.prices.get("SOUL_STRING"), "Refresh never overwrites the saved fallback price");
        yes(!service.requestRefresh(automatic, now+299_999), "Manual refresh obeys five-minute throttle");
        eq(1, fetches.get(), "Repeated client ticks cannot spam requests");
        yes(service.requestRefresh(automatic, now+300_000), "Refresh becomes available after five minutes");
        service.tick(automatic, now+300_001);
        yes(!BazaarPrices.isStale(automatic, now), "Fresh cache status correct");
        yes(BazaarPrices.isStale(automatic, now+900_001), "Old cache is visibly stale");
        yes(service.status(automatic, now+900_001).contains("stale"), "UI reports cached snapshot age");

        BazaarPrices broken = new BazaarPrices(() -> { throw new CompletionException(new java.io.IOException("offline")); }, Runnable::run);
        broken.tick(automatic, now); broken.tick(automatic, now+1);
        eq(6100.25, automatic.price("SOUL_STRING"), "Failed request retains the last usable value");
        yes(broken.status(automatic, now+1).contains("unavailable"), "Failed refresh has visible status");
        BazaarPrices older = new BazaarPrices(() -> new BazaarPrices.Snapshot(now-2000, Map.of("SOUL_STRING", 1.0)), Runnable::run);
        older.tick(automatic, now); older.tick(automatic, now+1);
        eq(6100.25, automatic.price("SOUL_STRING"), "An older server response cannot replace a newer cache");

        Path root = Files.createTempDirectory(Path.of(System.getProperty("test.root", "build")), "arachne-bazaar-");
        Path file = root.resolve("settings.json");
        Files.writeString(file, "{\"prices\":{\"SOUL_STRING\":7123,\"ARACHNE_FANG\":0,\"TARANTULA_EPIC\":2000000}}");
        Config migrated = Store.read(file, Config.class, Config::new, Config::validate);
        migrated.autoBazaar = true; migrated.bazaarPrices.put("SOUL_STRING", 1.0);
        eq(7123, migrated.price("SOUL_STRING"), "Old user price survives file migration and Auto enable");
        yes(migrated.isManualPrice("ARACHNE_FANG"), "Stored zero price survives migration as manual");
        migrated.clearManual("SOUL_STRING"); Store.write(file, migrated);
        Config reopened = Store.read(file, Config.class, Config::new, Config::validate);
        eq(1, reopened.price("SOUL_STRING"), "Explicit Auto choice and market cache survive restart");
        eq(2_000_000, reopened.price("TARANTULA_EPIC"), "Pet override survives save and reload");
        yes(reopened.rngTitles && reopened.rngValue, "Old config receives enabled RNG alert defaults");
        System.out.println("PASS: " + checks + " Bazaar parsing, override, migration, throttle and failure checks.");
    }
}
