package dev.arachneledger.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import dev.arachneledger.SettingsScreen;

/** Loaded through Mod Menu's optional entrypoint, keeping its API out of normal initialization. */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Construct on click so the shared tracker is initialized, and preserve the return screen.
        return SettingsScreen::new;
    }
}
