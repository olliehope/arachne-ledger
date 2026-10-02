package dev.arachneledger;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/** Replays personal pet rewards from floating labels, pickups and Pets-menu receipts. */
public final class PetRewardChecks {
    private static final long BASE = 1_000_000;
    private static final long DOWN = BASE + 2_000;
    private static final String EPIC = "TARANTULA_EPIC", LEGENDARY = "TARANTULA_LEGENDARY";
    private static final String CLAIM_TAIL =
            "! You can manage your Pets in the Pets Menu in your SkyBlock Menu.";
    // The original server receipt is coloured; rarity is absent from its visible pet name.
    private static final String SERVER_CLAIM =
            "§aYou claimed a §5Tarantula Pet§a! §r§aYou can manage your Pets in the §r§fPets Menu§r§a in your §r§fSkyBlock Menu§r§a.";
    private static int checks;

    private static void yes(boolean actual, String why) {
        checks++;
        if (!actual) {
            throw new AssertionError(why);
        }
    }

    private static void eq(Object expected, Object actual, String why) {
        checks++;
        if (!expected.equals(actual)) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    private static void amount(double expected, double actual, String why) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > .00001) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    private static UUID stand(long id) {
        return new UUID(317, id);
    }

    private static String claim(String pet) {
        return "You claimed a " + pet + CLAIM_TAIL;
    }

    private static void drop(String label, String item, int count) {
        var found = LootLabels.parse(label);
        yes(found != null, "Recognized reward label: " + label);
        eq(item, found.item(), "Label identifies pet rarity: " + label);
        eq(count, found.count(), "Label preserves reward quantity: " + label);
    }

    private static void receipt(String message, String item) {
        var found = PetDrops.claim(message);
        yes(found != null, "Recognized personal receipt: " + message);
        eq(item, found.item(), "Receipt identifies pet rarity");
        eq(1, found.count(), "Personal receipt is one pet");
    }

    private static Tracker tracker() throws Exception {
        Tracker tracker = new Tracker(Files.createTempDirectory("arachne-pet-rewards-"));
        tracker.account("pet-test-account");
        tracker.config.manualSet(EPIC, 2_000_000);
        tracker.config.manualSet(LEGENDARY, 5_000_000);
        tracker.updateLocation(true, true, true, "Arachne's Sanctuary", "sidebar", BASE);
        return tracker;
    }

    private static void start(Tracker tracker, long now) {
        tracker.message("[BOSS] Arachne: With your sacrifice.", "Owner", now);
    }

    private static void down(Tracker tracker, long now) {
        tracker.message("ARACHNE DOWN!", "Owner", now);
        tracker.message("Your Damage: 10,000 (Position #1)", "Owner", now + 10);
    }

    private static Tracker rewardWindow() throws Exception {
        Tracker tracker = tracker();
        start(tracker, BASE + 1_000);
        down(tracker, DOWN);
        return tracker;
    }

    private static long petCount(Tracker tracker, String item) {
        return tracker.ledger.stats(true).loot().getOrDefault(item, 0L);
    }

    private static List<Ledger.Entry> rewards(Tracker tracker) {
        return tracker.ledger.entries.stream().filter(e -> e.kind() == Ledger.Kind.LOOT).toList();
    }

    private static void source(Tracker tracker, char source, String item, long now, UUID id) {
        switch (source) {
            case 'S' ->
                    tracker.observeLootStand(
                            id,
                            item.equals(EPIC) ? "Epic Tarantula Pet" : "Legendary Tarantula Pet",
                            now);
            case 'P' -> tracker.pickup(item, 1, now);
            case 'C' ->
                    tracker.message(
                            claim(
                                    item.equals(EPIC)
                                            ? "Epic Tarantula Pet"
                                            : "Legendary Tarantula Pet"),
                            "Owner",
                            now);
            default -> throw new AssertionError("Unknown fixture source");
        }
    }

    private static void expectIgnored(Tracker tracker, long now, String why) {
        long before = petCount(tracker, LEGENDARY);
        source(tracker, 'S', LEGENDARY, now, UUID.randomUUID());
        source(tracker, 'P', LEGENDARY, now + 1, UUID.randomUUID());
        source(tracker, 'C', LEGENDARY, now + 2, UUID.randomUUID());
        eq(before, petCount(tracker, LEGENDARY), why);
        eq(0, tracker.rng.queued(), why + " leaves no popup");
    }

    public static void main(String[] args) throws Exception {
        // Regression first: this is the exact floating name reported by the user.
        drop("Legendary Tarantula Pet", LEGENDARY, 1);
        drop("Epic Tarantula Pet", EPIC, 1);
        drop("§6Legendary Tarantula Pet", LEGENDARY, 1);
        drop("§5Epic Tarantula Pet §8x2", EPIC, 2);
        drop("2 × Legendary Tarantula Pet", LEGENDARY, 2);
        drop("[Lvl 1] Legendary Tarantula Pet", LEGENDARY, 1);
        drop("[Lvl 100] Epic Tarantula", EPIC, 1);
        drop("legendary tarantula pet", LEGENDARY, 1);
        drop("Tarantula Pet (Legendary)", LEGENDARY, 1);
        drop("§7[Lvl 1] §5Tarantula", EPIC, 1);
        drop("§7[Lvl 1] §6Tarantula Pet §8x3", LEGENDARY, 3);
        // Explicit rarity also survives a mod changing display colours.
        drop("§fLegendary Tarantula Pet", LEGENDARY, 1);
        var explicit =
                Component.literal("Legendary ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Tarantula Pet").withStyle(ChatFormatting.GOLD));
        drop(LootLabels.formatted(explicit), LEGENDARY, 1);
        var splitName =
                Component.literal("[Lvl 1] ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Taran").withStyle(ChatFormatting.DARK_PURPLE))
                        .append(
                                Component.literal("tula Pet")
                                        .withStyle(ChatFormatting.DARK_PURPLE));
        drop(LootLabels.formatted(splitName), EPIC, 1);
        for (String invalid :
                List.of(
                        "Tarantula Pet",
                        "Rare Tarantula Pet",
                        "Mythic Tarantula Pet",
                        "§aTarantula Pet",
                        "Legendary Tarantula Broodfather",
                        "Legendary Tarantula Pet x0",
                        "Epic Tarantula Pet (Legendary)",
                        "Legendary Tarantula Pet (Epic)")) {
            yes(
                    LootLabels.parse(invalid) == null,
                    "Invalid or ambiguous label rejected: " + invalid);
        }

        receipt(SERVER_CLAIM, EPIC);
        receipt(SERVER_CLAIM.replace("§5Tarantula", "§6Tarantula"), LEGENDARY);
        receipt(claim("Legendary Tarantula Pet"), LEGENDARY);
        receipt("You claimed an Epic Tarantula Pet" + CLAIM_TAIL, EPIC);
        receipt("You claimed a §5Taran§5tula Pet" + CLAIM_TAIL, EPIC);
        receipt("You claimed a [Lvl 1] §6Tarantula" + CLAIM_TAIL, LEGENDARY);
        var claimComponent =
                Component.literal("You claimed a ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal("Tarantula Pet").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(CLAIM_TAIL).withStyle(ChatFormatting.GREEN));
        receipt(LootLabels.formatted(claimComponent), LEGENDARY);
        var placement =
                Component.literal("☄ ")
                        .append(
                                Component.object(
                                        new PlayerSprite(
                                                ResolvableProfile.createUnresolved("realpoopy123"),
                                                true)))
                        .append("realpoopy123 placed an Arachne Crystal! Something is awakening!");
        String formattedPlacement = LootLabels.formatted(placement);
        yes(
                Messages.isSummoning(formattedPlacement),
                "Preserving component colours retains the inline-head awakening cue");
        eq(
                Messages.Event.CRYSTAL,
                Messages.parse(formattedPlacement, "realpoopy123"),
                "Formatted real player-head component retains Crystal ownership");
        for (String invalid :
                List.of(
                        "Party > Owner: " + claim("Legendary Tarantula Pet"),
                        "[MVP+] Owner: " + claim("Legendary Tarantula Pet"),
                        "Other claimed a Legendary Tarantula Pet" + CLAIM_TAIL,
                        "You claimed a Legendary Tarantula Pet!",
                        claim("Tarantula Pet"),
                        claim("§dTarantula Pet"),
                        claim("Legendary Scatha Pet"),
                        claim("Arachne's Fang"))) {
            yes(
                    PetDrops.claim(invalid) == null,
                    "Non-personal or unsupported receipt rejected: " + invalid);
        }
        yes(PetDrops.claim(null) == null, "Null receipt is safe");

        for (String item : List.of(EPIC, LEGENDARY)) {
            for (String order : List.of("SPC", "SCP", "PSC", "PCS", "CSP", "CPS")) {
                Tracker tracker = rewardWindow();
                UUID id = stand(1);
                long first = DOWN + 100;
                for (int i = 0; i < order.length(); i++) {
                    source(tracker, order.charAt(i), item, first + 100 * i, id);
                }
                eq(1L, petCount(tracker, item), item + " counted once for source order " + order);
                eq(1, rewards(tracker).size(), "One journal entry for " + order);
                eq(1, tracker.rng.queued(), "One popup for " + order);
                eq(
                        item,
                        tracker.rng.current(first + 400).item(),
                        "Popup follows first detected source for " + order);
                eq(
                        RngAlerts.rarityColor(item),
                        tracker.rng.current(first + 400).color(),
                        "Popup uses recorded rarity for " + order);
                amount(
                        item.equals(EPIC) ? 2_000_000 : 5_000_000,
                        tracker.ledger.stats(false).revenue(),
                        "One pet valuation for " + order);
                eq(
                        tracker.ledger.fights.getFirst().id,
                        rewards(tracker).getFirst().fightId(),
                        "Pet belongs to completed fight for " + order);
                // Repeated entity scans and a relayed claim must not create a fresh title.
                source(tracker, 'S', item, first + 500, id);
                source(tracker, 'C', item, first + 600, id);
                eq(1L, petCount(tracker, item), "Repeated stand and claim suppressed for " + order);
                eq(1, tracker.rng.queued(), "Repeated sources leave one queued title for " + order);
                yes(
                        tracker.rng.current(first + RngAlerts.DURATION_MILLIS) == null,
                        "Duplicates do not extend title lifetime for " + order);
            }
        }

        Tracker delayed = rewardWindow();
        source(delayed, 'S', LEGENDARY, DOWN + 100, stand(2));
        source(delayed, 'P', LEGENDARY, DOWN + 15_000, stand(2));
        source(delayed, 'C', LEGENDARY, DOWN + 44_900, stand(2));
        eq(
                1L,
                petCount(delayed, LEGENDARY),
                "Late claim and pickup pair over the full reward window");
        eq(1, rewards(delayed).size(), "Late third source adds no journal entry");
        yes(
                delayed.rng.current(DOWN + 44_900) == null,
                "Late duplicates cannot produce a second popup");

        Tracker distinct = rewardWindow();
        source(distinct, 'S', EPIC, DOWN + 100, stand(3));
        source(distinct, 'S', LEGENDARY, DOWN + 200, stand(4));
        source(distinct, 'C', EPIC, DOWN + 300, stand(3));
        source(distinct, 'C', LEGENDARY, DOWN + 400, stand(4));
        eq(1L, petCount(distinct, EPIC), "Epic receipt does not suppress Legendary reward");
        eq(1L, petCount(distinct, LEGENDARY), "Legendary receipt does not suppress Epic reward");
        eq(2, distinct.rng.queued(), "Distinct rare rewards retain separate titles");
        eq(EPIC, distinct.rng.current(DOWN + 500).item(), "First rare reward displays first");
        eq(
                LEGENDARY,
                distinct.rng.current(DOWN + 4_200).item(),
                "Next rare reward displays after first expires");

        Tracker laterFight = rewardWindow();
        source(laterFight, 'C', LEGENDARY, DOWN + 100, stand(5));
        start(laterFight, DOWN + 10_000);
        down(laterFight, DOWN + 11_000);
        source(laterFight, 'C', LEGENDARY, DOWN + 11_100, stand(6));
        eq(
                2L,
                petCount(laterFight, LEGENDARY),
                "Same claim in a later boss reward window is a new reward");
        eq(2, rewards(laterFight).size(), "Both fights retain a pet journal entry");
        yes(
                rewards(laterFight).getFirst().fightId() != rewards(laterFight).getLast().fightId(),
                "Each pet is attributed to its own fight");

        Tracker beforeDeath = tracker();
        start(beforeDeath, BASE + 1_000);
        source(beforeDeath, 'S', LEGENDARY, BASE + 1_100, stand(7));
        source(beforeDeath, 'C', LEGENDARY, BASE + 1_200, stand(7));
        eq(
                0L,
                petCount(beforeDeath, LEGENDARY),
                "Floating pet and personal claim require a boss death");
        eq(0, beforeDeath.rng.queued(), "Pre-death claim does not show a popup");

        Tracker boundary = rewardWindow();
        source(boundary, 'C', LEGENDARY, DOWN + 45_000, stand(8));
        eq(1L, petCount(boundary, LEGENDARY), "Receipt at exactly 45 seconds is eligible");
        Tracker expired = rewardWindow();
        expectIgnored(expired, DOWN + 45_001, "All pet sources after 45 seconds are ignored");
        Tracker idle = tracker();
        expectIgnored(idle, BASE + 1_000, "Idle sanctuary cannot count stale pet rewards");

        Tracker paused = rewardWindow();
        source(paused, 'C', LEGENDARY, DOWN + 100, stand(9));
        paused.togglePause();
        expectIgnored(paused, DOWN + 200, "Paused tracker ignores pet rewards");
        paused.togglePause();
        expectIgnored(paused, DOWN + 300, "Unpausing cannot revive the old reward window");
        eq(1L, petCount(paused, LEGENDARY), "Pause preserves previously recorded pet");

        Tracker area = rewardWindow();
        area.updateLocation(true, true, false, "Hub", "sidebar", DOWN + 100);
        expectIgnored(area, DOWN + 200, "Outside sanctuary ignores pet rewards");
        area.updateLocation(true, true, true, "Arachne's Sanctuary", "sidebar", DOWN + 300);
        expectIgnored(area, DOWN + 400, "Returning to sanctuary does not revive old pet window");

        Tracker session = rewardWindow();
        source(session, 'C', LEGENDARY, DOWN + 100, stand(10));
        session.newSession();
        expectIgnored(session, DOWN + 200, "New session clears pet reward context");
        eq(
                0L,
                session.ledger.stats(false).loot().getOrDefault(LEGENDARY, 0L),
                "New session starts without pet loot");
        eq(1L, petCount(session, LEGENDARY), "Lifetime retains pet across session boundary");

        Tracker profile = rewardWindow();
        source(profile, 'C', LEGENDARY, DOWN + 100, stand(11));
        profile.profile("another");
        expectIgnored(profile, DOWN + 200, "Changing profile clears pet reward context");
        eq(0L, petCount(profile, LEGENDARY), "Another profile starts without pet loot");
        profile.profile("default");
        expectIgnored(
                profile, DOWN + 300, "Returning to profile does not revive pet reward context");
        eq(1L, petCount(profile, LEGENDARY), "Original profile retains recorded pet");

        Tracker disconnected = rewardWindow();
        disconnected.resetContext();
        expectIgnored(disconnected, DOWN + 100, "Disconnect clears pet reward context");

        Tracker zero = rewardWindow();
        zero.config.manualSet(LEGENDARY, 0);
        source(zero, 'S', LEGENDARY, DOWN + 100, stand(12));
        eq(1L, petCount(zero, LEGENDARY), "Explicit zero price still records pet");
        amount(
                0,
                rewards(zero).getFirst().unit(),
                "Explicit zero is saved without a guessed value");
        eq(1L, zero.ledger.stats(false).unpriced(), "Zero valuation remains visible as unpriced");
        eq(
                "",
                zero.rng.current(DOWN + 300).valueText(),
                "Unpriced pet title omits the price caption");

        Tracker unpriced = rewardWindow();
        unpriced.config.prices.remove(EPIC);
        unpriced.message(SERVER_CLAIM, "Owner", DOWN + 100);
        eq(1L, petCount(unpriced, EPIC), "Claim records an unpriced pet");
        amount(0, unpriced.ledger.stats(false).revenue(), "Unpriced pet does not fabricate profit");
        eq(
                "",
                unpriced.rng.current(DOWN + 300).valueText(),
                "Original receipt triggers a title without a guessed price");

        Tracker noTitles = rewardWindow();
        noTitles.config.rngTitles = false;
        noTitles.message(SERVER_CLAIM, "Owner", DOWN + 100);
        noTitles.observeLootStand(stand(13), "Epic Tarantula Pet", DOWN + 200);
        noTitles.pickup(EPIC, 1, DOWN + 300);
        eq(
                1L,
                petCount(noTitles, EPIC),
                "Disabling rare titles still records and deduplicates pet loot");
        eq(0, noTitles.rng.queued(), "Disabled titles do not queue a popup");
        amount(
                2_000_000,
                noTitles.ledger.stats(false).revenue(),
                "Disabled titles retain pet profit");

        Tracker persisted = rewardWindow();
        persisted.message(SERVER_CLAIM, "Owner", DOWN + 100);
        persisted.save();
        var file = Files.createTempFile("arachne-pet-receipt-", ".json");
        Store.write(file, persisted.ledger);
        Ledger restored = Store.read(file, Ledger.class, Ledger::new, Ledger::validate);
        eq(1L, restored.stats(true).loot().get(EPIC), "Pet receipt survives storage round trip");
        eq(
                "pet_claim",
                restored.entries.stream()
                        .filter(e -> e.kind() == Ledger.Kind.LOOT)
                        .findFirst()
                        .orElseThrow()
                        .source(),
                "Receipt source is preserved for review");
        amount(
                2_000_000,
                restored.fightStats(restored.fights.getFirst().id).revenue(),
                "Persisted receipt retains fight valuation");
        System.out.println("PASS: " + checks + " pet reward, receipt, popup and isolation checks.");
    }

    private PetRewardChecks() {}
}
