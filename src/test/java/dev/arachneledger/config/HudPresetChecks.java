package dev.arachneledger.config;

import dev.arachneledger.ledger.Ledger;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Presets are detached presentation snapshots, including damaged and legacy saved data. */
public final class HudPresetChecks {
    private static int checks;

    private static void yes(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void same(Object expected, Object actual, String message) {
        yes(Objects.equals(expected, actual), message + ": " + expected + " / " + actual);
    }

    public static void main(String[] args) throws Exception {
        Config config = new Config();
        config.validate();
        config.hudPreferences.applyPreset(HudPreferences.Layout.SPLIT);
        config.hudPreferences.pinnedAchievement = "kill_first";
        config.hudPreferences.showCountdown = true;
        config.hudPreferences.style = HudPreferences.Style.BORDER;
        config.hudPreferences.hiddenItems.add("STRING");
        config.hudPreferences.rowOrder =
                HudRowOrder.move(config.hudPreferences.rowOrder, "profit", 1);
        config.hudX = .3;
        config.hudY = .7;
        config.hudScale = 1.4;
        config.hudBackground = true;
        config.hudAlwaysShow = true;
        config.manualSet("SOUL_STRING", 777);
        Ledger ledger = new Ledger();
        ledger.add(Ledger.Kind.LOOT, "SOUL_STRING", 2, 10, "server", 1);
        var preset = HudPreset.capture("Farming", config);
        config.hudPreferences.hiddenItems.clear();
        config.hudPreferences.pinnedAchievement = "";
        config.hudX = .9;
        same(
                List.of("STRING"),
                List.copyOf(preset.preferences.hiddenItems),
                "Capture does not alias live filters");
        preset.apply(config);
        same(.3, config.hudX, "Apply restores position");
        same(.7, config.hudY, "Apply restores vertical position");
        same(1.4, config.hudScale, "Apply restores scale");
        yes(config.hudBackground && config.hudAlwaysShow, "Apply restores display context");
        same("kill_first", config.hudPreferences.pinnedAchievement, "Apply restores goal");
        yes(config.hudPreferences.showCountdown, "Apply restores HUD countdown");
        same(
                HudPreferences.Style.BORDER,
                config.hudPreferences.style,
                "Apply restores presentation style");
        config.hudPreferences.hiddenItems.clear();
        same(
                List.of("STRING"),
                List.copyOf(preset.preferences.hiddenItems),
                "Apply also creates detached filters");
        same(777.0, config.price("SOUL_STRING"), "Preset leaves prices intact");
        same(20.0, ledger.stats(true).profit(), "Preset leaves recorded income intact");
        config.hudPresets.add(preset);
        var file = Files.createTempDirectory("arachne-hud-presets").resolve("config.json");
        Store.write(file, config);
        Config restored = Store.read(file, Config.class, Config::new, Config::validate);
        same("Farming", restored.hudPresets.getFirst().name, "Named presets survive JSON");
        same(
                HudPreferences.Style.BORDER,
                restored.hudPresets.getFirst().preferences.style,
                "Saved presentation style survives JSON");
        same(
                "kill_first",
                restored.hudPresets.getFirst().preferences.pinnedAchievement,
                "Saved goal survives JSON");
        var invalid = new HudPreset();
        invalid.name = "§Farming\n";
        invalid.preferences = null;
        invalid.scale = Double.NaN;
        invalid.x = Double.POSITIVE_INFINITY;
        invalid.y = 7;
        var repaired = HudPreset.normalize(List.of(invalid));
        same("Farming", repaired.getFirst().name, "Name formatting and controls are stripped");
        same(1.0, repaired.getFirst().scale, "Nonfinite scale is repaired");
        same(-1.0, repaired.getFirst().x, "Nonfinite position is repaired");
        same(1.0, repaired.getFirst().y, "Oversized position clamps");
        yes(invalid.preferences == null, "Normalization does not mutate original records");
        var duplicate = preset.copy();
        duplicate.name = "fArMiNg";
        same(
                1,
                HudPreset.normalize(List.of(preset, duplicate)).size(),
                "Names are case-insensitively unique");
        List<HudPreset> oversized = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            HudPreset extra = preset.copy();
            extra.name = "Preset " + index;
            oversized.add(extra);
        }
        same(HudPreset.LIMIT, HudPreset.normalize(oversized).size(), "Preset storage is bounded");
        same(List.of(), HudPreset.normalize(null), "Legacy settings start with no named presets");
        HudPreferences detached = preset.preferences.copy();
        yes(
                detached.sameDisplay(preset.preferences),
                "Preference copy preserves every display flag");
        detached.showCountdown = false;
        yes(!detached.sameDisplay(preset.preferences), "Countdown invalidates display cache");
        detached.pinnedAchievement = "missing";
        detached.validate();
        same("", detached.pinnedAchievement, "Unknown pinned IDs are repaired");
        System.out.println("Hud preset checks passed: " + checks);
    }
}
