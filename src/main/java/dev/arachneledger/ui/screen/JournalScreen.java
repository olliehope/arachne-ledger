package dev.arachneledger.ui.screen;

import net.minecraft.client.gui.screens.Screen;

/** Compatibility route for existing journal links; progression now has one common entry point. */
public final class JournalScreen extends ProgressScreen {
    public JournalScreen(Screen parent) {
        super(parent);
    }
}
