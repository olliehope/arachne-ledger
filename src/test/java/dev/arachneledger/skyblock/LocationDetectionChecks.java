package dev.arachneledger.skyblock;

import java.util.List;

/** Pure fixtures for the formatted text Hypixel sends; no live server is required. */
public final class LocationDetectionChecks {
    private static int checks;

    private static void yes(boolean result, String label) {
        checks++;
        if (!result) {
            throw new AssertionError(label);
        }
    }

    private static LocationDetection.Result detect(
            String title, List<String> sidebar, List<String> tab) {
        return LocationDetection.detect("mc.hypixel.net:25565", "", title, sidebar, tab);
    }

    public static void main(String[] args) {
        yes(
                detect("SKYBLOCK", List.of("\u23e3 Arachne's Sanctuary"), List.of()).sanctuary(),
                "Standard sidebar location");
        String liveScore = "\ue067 Arachne's\u00a7v Sanctuary";
        var live =
                detect(
                        "SKYBLOCK ♲",
                        List.of(
                                "09/29/26 m30\u00a7zBS",
                                "\u00a7y",
                                "Late Spring 17th",
                                "4:20pm ☀\u00a7w",
                                liveScore,
                                "\u00a7u",
                                "Purse: 40,143,\u00a7t816"),
                        List.of("Area: Spider's Den"));
        yes(
                live.sanctuary() && live.location().equals("Arachne's Sanctuary"),
                "Captured live scoreboard sub-area overrides tab island despite custom section codes");
        yes(
                LocationDetection.clean(liveScore).equals("\ue067 Arachne's Sanctuary"),
                "Custom section code inside location is removed without losing adjacent text");
        yes(
                detect(
                                "\u00a76SKYBLOCK CO-OP",
                                List.of(
                                        "\u00a77\u23e3 \u00a7cArachne\ud83c\udf1f's Sanct\ud83d\udc7duary"),
                                List.of())
                        .sanctuary(),
                "Owner emoji splitting location inside words");
        yes(
                detect("SKYBLOCK", List.of("\u23e3 Arachne\u2019s\u00a0Sanctuary"), List.of())
                        .sanctuary(),
                "Curly apostrophe and nonbreaking space");
        yes(
                detect("SKYBLOCK", List.of("\u23e3 Arachne\u02BCs\u202fSanctuary"), List.of())
                        .sanctuary(),
                "Modifier apostrophe and narrow space");
        yes(
                detect("SKYBLOCK", List.of("\u23e3 Arac\u200Bhne's Sanctuary"), List.of())
                        .sanctuary(),
                "Invisible format marker");
        yes(
                detect("", List.of(), List.of("Area: Arachne's Sanctuary")).sanctuary(),
                "Tab fallback without sidebar");
        yes(
                detect(
                                "",
                                List.of(),
                                List.of("Area: Spider's Den", "Location: Arachne's Sanctuary"))
                        .sanctuary(),
                "Tab sub-area beats earlier island");
        yes(
                !detect(
                                "SKYBLOCK",
                                List.of(),
                                List.of("Area: Arachne's Sanctuary", "Location: Village"))
                        .sanctuary(),
                "Explicit current sub-area beats tab area");
        yes(
                detect("", List.of(), List.of("\u2726 Loc\ud83d\udc7dation: Arachne's Sanctuary"))
                        .sanctuary(),
                "Decorated tab location label");
        yes(
                detect("", List.of(), List.of("Area:", "Location: Arachne's Sanctuary"))
                        .sanctuary(),
                "Empty preceding area ignored");
        yes(
                !detect("SKYBLOCK", List.of(), List.of("Favorite Location: Arachne's Sanctuary"))
                        .sanctuary(),
                "Unrelated labels cannot assert location");
        yes(
                detect("\u00a7eSKY\ud83c\udf6bBLOCK", List.of(), List.of()).skyBlock(),
                "Scoreboard title split symbols ignored");
        yes(
                detect("SKYBLOCK", List.of("\ud83d\udc7d\u23e3 Arachne's Sanctuary"), List.of())
                        .sanctuary(),
                "Owner symbol before location marker");
        yes(
                !detect("SKYBLOCK", List.of("Visit \u23e3 Arachne's Sanctuary"), List.of())
                        .sanctuary(),
                "Location marker in quest text excluded");
        yes(
                detect("", List.of(), List.of("Area: Spider's Den")).skyBlock(),
                "Tab island confirms SkyBlock");
        yes(
                !detect("", List.of(), List.of("Area: Spider's Den")).sanctuary(),
                "Island alone does not claim Sanctuary");
        yes(detect("", List.of(), List.of("Game: SKYBLOCK")).skyBlock(), "Explicit tab game");
        yes(!detect("BED WARS", List.of(), List.of()).skyBlock(), "Other Hypixel game excluded");
        yes(
                !detect("SKYBLOCK", List.of("Travel Scroll To Arachne's Sanctuary"), List.of())
                        .sanctuary(),
                "Item names cannot assert location");
        yes(
                !detect("SKYBLOCK", List.of("Quest: Visit Arachne's Sanctuary"), List.of())
                        .sanctuary(),
                "Quest text cannot assert location");
        yes(
                !detect(
                                "SKYBLOCK",
                                List.of("\u23e3 Spider Mound"),
                                List.of("Location: Arachne's Sanctuary"))
                        .sanctuary(),
                "Current sidebar beats stale tab area");
        yes(
                detect(
                                "SKYBLOCK",
                                List.of("\u23e3 Arachne's Sanctuary"),
                                List.of("Area: Spider's Den"))
                        .sanctuary(),
                "Sub-area beats tab island");
        yes(
                detect("SKYBLOCK", List.of("\u23e3 Village"), List.of())
                        .location()
                        .equals("Village"),
                "Explicit different location surfaced");
        yes(
                detect("SKYBLOCK", List.of(), List.of())
                        .detectionReason()
                        .contains("waiting for location"),
                "Helpful missing-location reason");
        yes(
                LocationDetection.hypixelHost("MC.HYPIXEL.NET.:25565"),
                "Host casing, port and trailing dot");
        yes(
                !LocationDetection.hypixelHost("hypixel.net.evil.example"),
                "Suffix spoof not accepted");
        yes(!LocationDetection.hypixelHost("nothypixel.net"), "Similar domain not accepted");
        yes(
                LocationDetection.detect(
                                "alias.example",
                                "Hypixel BungeeCord (1.0)",
                                "SKYBLOCK",
                                List.of("\u23e3 Arachne's Sanctuary"),
                                List.of())
                        .sanctuary(),
                "Server brand supports connection aliases");
        yes(
                !LocationDetection.detect(
                                "example.net",
                                "vanilla",
                                "SKYBLOCK",
                                List.of("\u23e3 Arachne's Sanctuary"),
                                List.of())
                        .sanctuary(),
                "Other servers cannot trigger tracking");
        yes(
                !LocationDetection.detect(
                                "example.net",
                                "nothypixel",
                                "SKYBLOCK",
                                List.of("\u23e3 Arachne's Sanctuary"),
                                List.of())
                        .sanctuary(),
                "Similar brand not accepted");
        yes(LocationDetection.clean(null).isEmpty(), "Missing HUD text safe");
        yes(
                LocationDetection.clean(
                                "\u00a7x\u00a71\u00a72\u00a73\u00a74\u00a75\u00a76Location: Arachne's Sanctuary")
                        .equals("Location: Arachne's Sanctuary"),
                "Hex color formatting stripped");
        yes(
                LocationDetection.definitelyElsewhere("Spider Mound"),
                "Nearby sub-area clears boss fallback");
        yes(
                !LocationDetection.definitelyElsewhere("Spider's Den"),
                "Island-only text allows boss fallback");
        yes(!LocationDetection.definitelyElsewhere(""), "Missing location allows boss fallback");
        System.out.println("PASS: " + checks + " Sanctuary detection checks.");
    }

    private LocationDetectionChecks() {}
}
