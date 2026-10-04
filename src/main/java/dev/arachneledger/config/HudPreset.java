package dev.arachneledger.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** A named display snapshot; applying it cannot change scope, prices, or journal entries. */
public final class HudPreset {
    public static final int LIMIT = 12;
    public String name = "My HUD";
    public HudPreferences preferences = new HudPreferences();
    public Config.View view = Config.View.DETAILED;
    public boolean visible = true, alwaysShow, background;
    public double x = -1, y = -1, scale = 1;
    public int corner;

    public static HudPreset capture(String name, Config config) {
        HudPreset preset = new HudPreset();
        preset.name = name;
        preset.preferences = config.hudPreferences.copy();
        preset.view = config.hudView;
        preset.visible = config.hud;
        preset.alwaysShow = config.hudAlwaysShow;
        preset.background = config.hudBackground;
        preset.x = config.hudX;
        preset.y = config.hudY;
        preset.scale = config.hudScale;
        preset.corner = config.corner;
        preset.validate();
        return preset;
    }

    public HudPreset copy() {
        HudPreset copy = new HudPreset();
        copy.name = name;
        copy.preferences = preferences == null ? new HudPreferences() : preferences.copy();
        copy.view = view;
        copy.visible = visible;
        copy.alwaysShow = alwaysShow;
        copy.background = background;
        copy.x = x;
        copy.y = y;
        copy.scale = scale;
        copy.corner = corner;
        return copy;
    }

    public void apply(Config config) {
        HudPreset saved = copy();
        saved.validate();
        config.hudPreferences = saved.preferences;
        config.hudView = saved.view;
        config.hud = saved.visible;
        config.hudAlwaysShow = saved.alwaysShow;
        config.hudBackground = saved.background;
        config.hudX = saved.x;
        config.hudY = saved.y;
        config.hudScale = saved.scale;
        config.corner = saved.corner;
    }

    public void validate() {
        name = cleanName(name);
        if (preferences == null) preferences = new HudPreferences();
        preferences.validate();
        if (view == null) view = Config.View.DETAILED;
        x = coordinate(x);
        y = coordinate(y);
        scale = Double.isFinite(scale) ? Math.max(.65, Math.min(1.6, scale)) : 1;
        corner = Math.floorMod(corner, 4);
    }

    public static String cleanName(String name) {
        if (name == null) return "";
        String cleaned = name.replaceAll("[\\p{Cntrl}§]", "").trim();
        return cleaned.substring(0, Math.min(24, cleaned.length()));
    }

    public static List<HudPreset> normalize(List<HudPreset> saved) {
        List<HudPreset> result = new ArrayList<>();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (saved == null) return result;
        for (HudPreset original : saved) {
            if (original == null || result.size() == LIMIT) continue;
            // Repair detached records without letting a damaged collection mutate live settings.
            HudPreset copy = original.copy();
            copy.validate();
            if (!copy.name.isBlank() && names.add(copy.name.toLowerCase(Locale.ROOT)))
                result.add(copy);
        }
        return result;
    }

    private static double coordinate(double value) {
        return !Double.isFinite(value) || value < 0 ? -1 : Math.min(1, value);
    }
}
