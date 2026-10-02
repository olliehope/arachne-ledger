package dev.arachneledger;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Optional public Bazaar snapshots. Network work never runs on Minecraft's client thread. */
public final class BazaarPrices {
    public static final String ENDPOINT = "https://api.hypixel.net/v2/skyblock/bazaar";
    public static final long REFRESH_MILLIS = 300_000;
    public static final long STALE_MILLIS = 900_000;
    private static final long MAX_FEED_AGE = 1_800_000;
    private static final Set<String> CORE_ITEMS = Set.of("SOUL_STRING", "STRING", "SPIDER_EYE",
        "ENCHANTED_STRING", "ENCHANTED_SPIDER_EYE", "LUXURIOUS_SPOOL", "ESSENCE_SPIDER");
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor(job -> {
        Thread thread = new Thread(job, "Arachne Bazaar prices"); thread.setDaemon(true); return thread;
    });
    public static final BazaarPrices GLOBAL = new BazaarPrices(BazaarPrices::fetch, NETWORK);
    public record Snapshot(long updatedAt, Map<String, Double> prices, Map<String, Double> sellOfferPrices) {
        public Snapshot {
            prices = Map.copyOf(prices);
            sellOfferPrices = Map.copyOf(sellOfferPrices);
        }
        /** Old callers and saved caches contain instant-sell estimates only. */
        public Snapshot(long updatedAt, Map<String, Double> prices) { this(updatedAt, prices, Map.of()); }
    }
    private final Supplier<Snapshot> fetcher;
    private final Executor executor;
    private CompletableFuture<Snapshot> pending;
    private long nextAttempt;
    private String error = "";

    BazaarPrices(Supplier<Snapshot> fetcher, Executor executor) { this.fetcher = fetcher; this.executor = executor; }
    public static boolean supports(String id, Config config) {
        return CORE_ITEMS.contains(id) || (!id.startsWith("TARANTULA_")
            && (config.bazaarPrices.containsKey(id) || config.bazaarSellOfferPrices.containsKey(id)));
    }

    /** Call on the client thread. True means a cache changed and settings should be saved. */
    public boolean tick(Config config, long now) {
        boolean changed = false;
        if (pending != null && pending.isDone()) {
            try {
                Snapshot snapshot = pending.join();
                if (snapshot.updatedAt() < config.bazaarUpdatedAt)
                    throw new IllegalArgumentException("Older price snapshot; keeping the cached prices");
                if (now - snapshot.updatedAt() > MAX_FEED_AGE || snapshot.updatedAt() > now + 60_000)
                    throw new IllegalArgumentException("Stale price snapshot; keeping the cached prices");
                changed = config.bazaarUpdatedAt != snapshot.updatedAt() || !config.bazaarPrices.equals(snapshot.prices())
                    || !config.bazaarSellOfferPrices.equals(snapshot.sellOfferPrices());
                config.bazaarPrices = new LinkedHashMap<>(snapshot.prices());
                config.bazaarSellOfferPrices = new LinkedHashMap<>(snapshot.sellOfferPrices());
                config.bazaarUpdatedAt = snapshot.updatedAt(); error = "";
            } catch (RuntimeException ex) { error = friendlyError(ex); }
            pending = null;
        }
        if (config.autoBazaar && pending == null && now >= nextAttempt) requestRefresh(config, now);
        return changed;
    }
    /** Respect the five-minute throttle even when a user repeatedly presses Refresh. */
    public boolean requestRefresh(Config config, long now) {
        if (!config.autoBazaar || pending != null || now < nextAttempt) return false;
        nextAttempt = now + REFRESH_MILLIS;
        pending = CompletableFuture.supplyAsync(fetcher, executor);
        return true;
    }
    public boolean refreshing() { return pending != null; }
    public String error() { return error; }
    public String status(Config config, long now) {
        if (!config.autoBazaar) return "Bazaar: off";
        String age = config.bazaarUpdatedAt > 0 ? "updated " + age(now-config.bazaarUpdatedAt) + " ago" : "no prices yet";
        if (!error.isEmpty()) return "Bazaar: " + error + " | " + age;
        if (pending != null) return "Bazaar: refreshing | " + age;
        return "Bazaar: " + (isStale(config, now) && config.bazaarUpdatedAt > 0 ? "stale | " : "") + age;
    }
    public static boolean isStale(Config config, long now) {
        return config.bazaarUpdatedAt <= 0 || now-config.bazaarUpdatedAt > STALE_MILLIS;
    }
    private static String age(long millis) {
        long seconds = Math.max(0, millis/1000);
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return seconds/60 + "m";
        return seconds/3600 + "h";
    }
    private static String friendlyError(Throwable ex) {
        while ((ex instanceof CompletionException || ex instanceof ExecutionException) && ex.getCause() != null) ex = ex.getCause();
        if (ex instanceof IllegalArgumentException) return ex.getMessage();
        if (ex instanceof SocketTimeoutException || ex instanceof TimeoutException) return "request timed out";
        return "unavailable; using saved prices";
    }

    private static Snapshot fetch() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(ENDPOINT).toURL().openConnection();
            connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "ArachneLedger/Fabric");
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != 200) throw new IOException("Bazaar endpoint unavailable");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream body = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (body.size() + count > 8_388_608) throw new IOException("Price response too large");
                    if (System.nanoTime() > deadline) throw new SocketTimeoutException("Price response timed out");
                    body.write(buffer, 0, count);
                }
                return parse(body.toString(StandardCharsets.UTF_8), System.currentTimeMillis());
            }
        } catch (IOException ex) { throw new CompletionException(ex); }
        finally { if (connection != null) connection.disconnect(); }
    }

    /** Validate only exact catalog IDs present in the server response; do not estimate Auction prices. */
    public static Snapshot parse(String json, long now) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonElement success = root.get("success");
            if (success == null || !success.isJsonPrimitive() || !success.getAsJsonPrimitive().isBoolean() || !success.getAsBoolean())
                throw new IllegalArgumentException("Bazaar API reported failure");
            long updatedAt = integer(root.get("lastUpdated"));
            if (updatedAt <= 0 || updatedAt > now + 60_000 || now-updatedAt > MAX_FEED_AGE)
                throw new IllegalArgumentException("Stale price snapshot; keeping the cached prices");
            JsonObject products = root.getAsJsonObject("products");
            if (products == null || products.isEmpty()) throw new IllegalArgumentException("Missing Bazaar products");
            Map<String, Double> result = new LinkedHashMap<>(), offers = new LinkedHashMap<>();
            for (String id : Catalog.ITEMS.keySet()) {
                if (id.startsWith("TARANTULA_")) continue;
                JsonElement element = products.get(id);
                if (element == null || !element.isJsonObject()) continue;
                try {
                    JsonObject product = element.getAsJsonObject();
                    if (product.has("product_id") && !id.equals(product.get("product_id").getAsString())) continue;
                    JsonObject quick = product.getAsJsonObject("quick_status");
                    if (quick == null || (quick.has("productId") && !id.equals(quick.get("productId").getAsString()))) continue;
                    // The API names sides by the instant transaction: sellPrice quotes buyer bids,
                    // while buyPrice quotes seller asks. Both are top-2%-volume weighted estimates.
                    // Validate sides separately: a one-sided market must not erase the usable side.
                    addPrice(result, id, quick, "sellPrice", "sellOrders");
                    addPrice(offers, id, quick, "buyPrice", "buyOrders");
                } catch (RuntimeException ignored) { /* One bad product cannot invalidate other valid products. */ }
            }
            if (result.isEmpty() && offers.isEmpty()) throw new IllegalArgumentException("No usable Bazaar prices");
            return new Snapshot(updatedAt, result, offers);
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid Bazaar response"); }
    }
    private static void addPrice(Map<String, Double> target, String id, JsonObject quick, String priceKey, String ordersKey) {
        try {
            double price = number(quick.get(priceKey));
            if (price > 0 && integer(quick.get(ordersKey)) > 0) target.put(id, price);
        } catch (RuntimeException ignored) { /* A malformed or illiquid side cannot replace a saved fallback. */ }
    }
    private static double number(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Invalid price number");
        return Config.amount(element.getAsString());
    }
    private static long integer(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Invalid price timestamp");
        try { return element.getAsBigDecimal().longValueExact(); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("Invalid price timestamp"); }
    }
}
