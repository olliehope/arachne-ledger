package dev.arachneledger;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Separate, immediately saved choices keep the main price editor uncluttered. */
public final class ValuationScreen extends Screen {
    private final Screen parent;
    private final Tracker t = ArachneLedger.tracker;
    private int x, y, w;
    public ValuationScreen(Screen parent) { super(Component.literal("Arachne gear values")); this.parent = parent; }

    @Override protected void init() {
        w = Math.min(460, width-20); x = (width-w)/2; y = Math.max(6, (height-218)/2);
        modeButton(y+49, t.config.salvageArmor, () -> {
            t.config.salvageArmor = !t.config.salvageArmor; changed();
        });
        modeButton(y+97, t.config.salvageWeapons, () -> {
            t.config.salvageWeapons = !t.config.salvageWeapons; changed();
        });
        var coins = new FlatButton(x+16, y+171, w-32, 18,
            t.config.scavengerCoins ? "Scavenger coins: on" : "Scavenger coins: off", t.config.scavengerCoins, () -> {
                t.config.scavengerCoins = !t.config.scavengerCoins; changed();
            });
        coins.setTooltip(Tooltip.create(Component.literal("Count marked purse gains while Arachne tracking is active. Existing coin entries are kept.")));
        addRenderableWidget(coins);
        addRenderableWidget(new FlatButton(x+16, y+198, w-32, 20, "Done", false, this::onClose));
    }
    private void modeButton(int yy, boolean salvage, Runnable action) {
        var button = new FlatButton(x+w-152, yy, 136, 20,
            salvage ? "Salvage: 5 essence" : "Sell to NPC", salvage, action);
        button.setTooltip(Tooltip.create(Component.literal("Choose the value used for future drops. Salvage uses 5 Spider Essence per item. Saved manual sale prices return when you switch back to NPC. This setting does not sell or salvage items.")));
        addRenderableWidget(button);
    }
    private void changed() { t.saveConfig(); rebuildWidgets(); }
    private void line(GuiGraphicsExtractor g, String text, int yy, int mx, int my) {
        g.text(font, font.plainSubstrByWidth(text, w-32), x+16, yy, Graph.MUTED, false);
        if (mx>=x+16 && mx<x+w-16 && my>=yy-2 && my<yy+11)
            g.setTooltipForNextFrame(Component.literal(text), mx, my);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xDF101010);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.text(font, Component.literal("Gear values").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
            x+16, y+12, Hud.WHITE, true);
        line(g, "Choose how you value unupgraded drops.", y+31, mx, my);
        g.text(font, "Armor", x+16, y+55, Hud.WHITE, true);
        line(g, t.config.salvageArmor ? "Each piece: " + Format.coins(t.config.lootPrice("ARACHNE_HELMET")) + " coins in essence"
            : "NPC: 2,000 each. Saved price overrides apply.", y+78, mx, my);
        g.text(font, "Tools / weapons", x+16, y+103, Hud.WHITE, true);
        line(g, t.config.salvageWeapons ? "Arack: " + Format.coins(t.config.lootPrice("ARACK")) + " coins in essence"
            : "Arack NPC: 5,000. Saved price overrides apply.", y+126, mx, my);
        line(g, "Spider Essence: " + Format.coins(t.config.price("ESSENCE_SPIDER")) + " each ("
            + t.config.priceSource("ESSENCE_SPIDER") + ")", y+147, mx, my);
        line(g, "Future drops only; use Reprice for this session.", y+160, mx, my);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { t.saveConfig(); minecraft.setScreen(parent); }
}
