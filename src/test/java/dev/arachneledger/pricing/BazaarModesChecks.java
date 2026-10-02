package dev.arachneledger.pricing;

import dev.arachneledger.config.Config;
import dev.arachneledger.config.Store;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;

/** Separate seller-side estimates, cache migration and failure behavior without network access. */
public final class BazaarModesChecks {
    private static int checks;

    private static void yes(boolean condition, String why) {
        checks++;
        if (!condition) {
            throw new AssertionError(why);
        }
    }

    private static void eq(double expected, double actual, String why) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > .00001) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    private static void fails(Runnable action, String why) {
        checks++;
        try {
            action.run();
        } catch (IllegalArgumentException ex) {
            return;
        }
        throw new AssertionError(why);
    }

    private static String product(String id, String bid, String ask, long bids, long asks) {
        return "\""
                + id
                + "\":{\"product_id\":\""
                + id
                + "\",\"quick_status\":{\"productId\":\""
                + id
                + "\",\"sellPrice\":"
                + bid
                + ",\"buyPrice\":"
                + ask
                + ",\"sellOrders\":"
                + bids
                + ",\"buyOrders\":"
                + asks
                + "}}";
    }

    private static String feed(long at, String products) {
        return "{\"success\":true,\"lastUpdated\":" + at + ",\"products\":{" + products + "}}";
    }

    public static void main(String[] args) throws Exception {
        long now = 1_790_769_600_000L;
        var both =
                BazaarPrices.parse(
                        feed(
                                now,
                                product("SOUL_STRING", "6100.25", "7100.75", 10, 12)
                                        + ","
                                        + product("ESSENCE_SPIDER", "5.5", "7.25", 4, 6)
                                        + ","
                                        + product("TARANTULA_EPIC", "100000", "200000", 1, 1)),
                        now);
        eq(
                6100.25,
                both.prices().get("SOUL_STRING"),
                "Instant sell keeps the API's bid-side weighted quote");
        eq(
                7100.75,
                both.sellOfferPrices().get("SOUL_STRING"),
                "Sell offer uses the API's ask-side weighted quote");
        eq(
                7.25,
                both.sellOfferPrices().get("ESSENCE_SPIDER"),
                "Essence supports sell-offer valuation");
        yes(
                !both.sellOfferPrices().containsKey("TARANTULA_EPIC"),
                "Sell-offer mode never invents pet Bazaar prices");
        var legacy = new BazaarPrices.Snapshot(now, Map.of("SOUL_STRING", 6100.25));
        yes(
                legacy.sellOfferPrices().isEmpty(),
                "Legacy snapshots do not relabel instant-sell prices as offers");
        Map<String, Double> mutable = new LinkedHashMap<>(Map.of("STRING", 4.0));
        var immutable = new BazaarPrices.Snapshot(now, mutable, mutable);
        mutable.put("STRING", 100.0);
        eq(4, immutable.prices().get("STRING"), "Instant snapshot copies its source map");
        eq(4, immutable.sellOfferPrices().get("STRING"), "Offer snapshot copies its source map");

        var asksOnly =
                BazaarPrices.parse(feed(now, product("SOUL_STRING", "1", "7100", 0, 2)), now);
        yes(asksOnly.prices().isEmpty(), "No bids produces no instant-sell estimate");
        eq(
                7100,
                asksOnly.sellOfferPrices().get("SOUL_STRING"),
                "Usable asks alone still make a valid response");
        var bidsOnly =
                BazaarPrices.parse(feed(now, product("SOUL_STRING", "6100", "7100", 2, 0)), now);
        eq(
                6100,
                bidsOnly.prices().get("SOUL_STRING"),
                "No asks does not discard usable instant-sell bids");
        yes(
                bidsOnly.sellOfferPrices().isEmpty(),
                "No sell listings produces no sell-offer estimate");
        var badBid =
                BazaarPrices.parse(feed(now, product("SOUL_STRING", "\"bad\"", "7100", 2, 2)), now);
        eq(
                7100,
                badBid.sellOfferPrices().get("SOUL_STRING"),
                "Malformed bid cannot discard a valid ask");
        var badAsk =
                BazaarPrices.parse(feed(now, product("SOUL_STRING", "6100", "1e999", 2, 2)), now);
        eq(6100, badAsk.prices().get("SOUL_STRING"), "Malformed ask cannot discard a valid bid");
        yes(badAsk.sellOfferPrices().isEmpty(), "Non-finite asks do not reach the cache");
        fails(
                () -> BazaarPrices.parse(feed(now, product("SOUL_STRING", "0", "0", 2, 2)), now),
                "Zero quotes cannot erase saved fallback values");
        fails(
                () ->
                        BazaarPrices.parse(
                                feed(now, product("SOUL_STRING", "6100", "7100", 0, 0)), now),
                "No side liquidity rejects an empty response");
        fails(
                () -> BazaarPrices.parse(feed(now, product("SOUL_STRING", "-1", "-2", 2, 2)), now),
                "Negative quotes rejected on both sides");
        fails(
                () ->
                        BazaarPrices.parse(
                                feed(now, product("SOUL_STRING", "6100", "7100", 2, 2))
                                        .replace(
                                                "\"product_id\":\"SOUL_STRING\"",
                                                "\"product_id\":\"STRING\""),
                                now),
                "Mismatched product identity cannot price either mode");

        Config config = new Config();
        config.validate();
        config.useBazaarItems();
        yes(
                config.bazaarMode == Config.BazaarMode.INSTANT_SELL,
                "Existing settings default to instant-sell mode");
        BazaarPrices service = new BazaarPrices(() -> both, Runnable::run);
        yes(!service.tick(config, now), "Both-side fetch remains asynchronous to the caller");
        yes(
                service.tick(config, now + 1),
                "Both market sides are installed in one completed update");
        eq(6100.25, config.price("SOUL_STRING"), "Initial future valuation uses instant sell");
        config.bazaarMode = Config.BazaarMode.SELL_OFFER;
        eq(
                7100.75,
                config.price("SOUL_STRING"),
                "Changing mode selects the cached ask estimate immediately");
        eq(
                7.25,
                config.price("ESSENCE_SPIDER"),
                "Salvage's essence ingredient receives selected market mode");
        eq(
                5000,
                config.prices.get("SOUL_STRING"),
                "Either mode preserves the saved manual fallback");
        config.manualSet("SOUL_STRING", 9000);
        eq(9000, config.price("SOUL_STRING"), "Manual price wins in sell-offer mode");
        config.manualSet("SOUL_STRING", 0);
        eq(0, config.price("SOUL_STRING"), "Intentional manual zero wins in sell-offer mode");
        config.clearManual("SOUL_STRING");
        eq(
                7100.75,
                config.price("SOUL_STRING"),
                "Clearing an override restores the selected automatic side");
        config.autoBazaar = false;
        eq(
                0,
                config.price("SOUL_STRING"),
                "Turning automatic pricing off uses saved fallback, regardless of mode");
        config.autoBazaar = true;
        config.bazaarSellOfferPrices.remove("SOUL_STRING");
        eq(
                0,
                config.price("SOUL_STRING"),
                "Missing offer cache uses saved fallback without silently using instant sell");
        config.bazaarMode = Config.BazaarMode.INSTANT_SELL;
        eq(
                6100.25,
                config.price("SOUL_STRING"),
                "Missing offer data does not affect the distinct instant cache");
        config.bazaarPrices.remove("LUXURIOUS_SPOOL");
        config.bazaarSellOfferPrices.put("LUXURIOUS_SPOOL", 1000.0);
        yes(
                BazaarPrices.supports("LUXURIOUS_SPOOL", config),
                "Cached offer markets support deliberate automatic opt-in");
        config.bazaarSellOfferPrices.put("ARACHNE_FANG", 1200.0);
        yes(
                BazaarPrices.supports("ARACHNE_FANG", config),
                "A non-core catalog item can be supported by offers alone");
        config.bazaarSellOfferPrices.put("TARANTULA_EPIC", 100000.0);
        yes(
                !BazaarPrices.supports("TARANTULA_EPIC", config),
                "Cached pet IDs cannot enable Bazaar pricing");
        config.bazaarSellOfferPrices.remove("TARANTULA_EPIC");

        Config retained = new Config();
        retained.validate();
        retained.useBazaarItems();
        retained.bazaarPrices = new LinkedHashMap<>(both.prices());
        retained.bazaarSellOfferPrices = new LinkedHashMap<>(both.sellOfferPrices());
        retained.bazaarUpdatedAt = now;
        BazaarPrices failed =
                new BazaarPrices(
                        () -> {
                            throw new CompletionException(new java.io.IOException("offline"));
                        },
                        Runnable::run);
        failed.tick(retained, now);
        yes(!failed.tick(retained, now + 1), "Failed fetch cannot partially replace either cache");
        eq(6100.25, retained.bazaarPrices.get("SOUL_STRING"), "Failure keeps the instant cache");
        eq(
                7100.75,
                retained.bazaarSellOfferPrices.get("SOUL_STRING"),
                "Failure keeps the offer cache");
        BazaarPrices old =
                new BazaarPrices(
                        () ->
                                new BazaarPrices.Snapshot(
                                        now - 1,
                                        Map.of("SOUL_STRING", 1.0),
                                        Map.of("SOUL_STRING", 2.0)),
                        Runnable::run);
        old.tick(retained, now);
        yes(!old.tick(retained, now + 1), "Older responses cannot partially replace market sides");
        eq(
                7100.75,
                retained.bazaarSellOfferPrices.get("SOUL_STRING"),
                "Older response preserves current offers");
        var changedOffers =
                new BazaarPrices.Snapshot(now, both.prices(), Map.of("SOUL_STRING", 7200.0));
        BazaarPrices offerChanged = new BazaarPrices(() -> changedOffers, Runnable::run);
        offerChanged.tick(retained, now);
        yes(
                offerChanged.tick(retained, now + 1),
                "An offers-only change requires saving even if instant quotes and time match");
        eq(
                7200,
                retained.bazaarSellOfferPrices.get("SOUL_STRING"),
                "An offers-only cache update applies correctly");
        BazaarPrices oneSide = new BazaarPrices(() -> asksOnly, Runnable::run);
        oneSide.tick(retained, now);
        yes(oneSide.tick(retained, now + 1), "A one-sided snapshot replaces both maps atomically");
        yes(
                retained.bazaarPrices.isEmpty(),
                "Disappearing bid liquidity does not retain an obsolete bid quote");
        eq(
                7100,
                retained.bazaarSellOfferPrices.get("SOUL_STRING"),
                "One-sided snapshot retains its usable ask");

        Path root =
                Files.createTempDirectory(
                        Path.of(System.getProperty("test.root", "build")), "arachne-bazaar-modes-");
        Path file = root.resolve("settings.json");
        Files.writeString(
                file,
                "{\"autoBazaar\":true,\"manualPriceItems\":[],\"bazaarPrices\":{\"SOUL_STRING\":6100}}");
        Config migrated = Store.read(file, Config.class, Config::new, Config::validate);
        yes(
                migrated.bazaarMode == Config.BazaarMode.INSTANT_SELL,
                "Saved legacy settings migrate to the original valuation mode");
        eq(
                6100,
                migrated.price("SOUL_STRING"),
                "Migration preserves the legacy instant-sell cache");
        yes(
                migrated.bazaarSellOfferPrices.isEmpty(),
                "Migration leaves missing sell-offer data visibly unavailable");
        migrated.bazaarMode = Config.BazaarMode.SELL_OFFER;
        eq(
                5000,
                migrated.price("SOUL_STRING"),
                "Selecting offers before refresh uses the saved fallback");
        migrated.bazaarSellOfferPrices.put("SOUL_STRING", 7400.0);
        Store.write(file, migrated);
        Config reopened = Store.read(file, Config.class, Config::new, Config::validate);
        yes(
                reopened.bazaarMode == Config.BazaarMode.SELL_OFFER,
                "Selected sale mode survives restart");
        eq(
                7400,
                reopened.price("SOUL_STRING"),
                "Offer cache survives restart independently of legacy instant data");
        reopened.bazaarMode = Config.BazaarMode.INSTANT_SELL;
        eq(6100, reopened.price("SOUL_STRING"), "Both cached market sides survive restart");
        System.out.println(
                "PASS: "
                        + checks
                        + " Bazaar seller-mode, liquidity, migration and failure checks.");
    }
}
