package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.ledger.FightRecord;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.skyblock.Catalog;
import dev.arachneledger.skyblock.PurseCoins;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Format;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class FightDetailsScreen extends Screen {
    private final Screen parent;
    private final Tracker tracker = ArachneLedger.tracker;
    private final long fightId;
    private int panelX, panelY, panelWidth, panelHeight, scrollOffset;
    private FlatButton editDropsButton;

    public FightDetailsScreen(Screen parent, long fightId) {
        super(Component.literal("Fight breakdown"));
        this.parent = parent;
        this.fightId = fightId;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(470, width - 24);
        panelHeight = Math.min(328, height - 20);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        editDropsButton =
                new FlatButton(
                        panelX + 12,
                        panelY + panelHeight - 24,
                        96,
                        18,
                        "Edit drops",
                        false,
                        () -> minecraft.setScreen(new FightEditScreen(this, fightId)));
        updateEditButton();
        addRenderableWidget(editDropsButton);
        addRenderableWidget(
                new FlatButton(
                        panelX + panelWidth - 80,
                        panelY + panelHeight - 24,
                        68,
                        18,
                        "Back",
                        false,
                        this::onClose));
    }

    private void updateEditButton() {
        var outcome = tracker.ledger.fight(fightId).outcome;
        editDropsButton.active =
                outcome != FightRecord.Outcome.FIGHTING
                        && outcome != FightRecord.Outcome.WAITING_DAMAGE;
    }

    @Override
    public void tick() {
        updateEditButton();
    }

    static String duration(FightRecord fight) {
        return fight.duration() < 0
                ? "Time unknown"
                : String.format(Locale.ROOT, "%.1fs", fight.duration() / 1000.0);
    }

    static String date(FightRecord fight) {
        long at = fight.died > 0 ? fight.died : fight.spawned;
        return at > 0
                ? DateTimeFormatter.ofPattern("MMM d, HH:mm")
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(at))
                : "Start time unknown";
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0x80000000);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xDF101010);
        var fight = tracker.ledger.fight(fightId);
        var stats = tracker.ledger.fightStats(fightId);
        var values = tracker.ledger.fightLootValues(fightId);
        graphics.text(
                font,
                Component.literal("Fight #" + fightId)
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                panelX + 12,
                panelY + 12,
                Hud.WHITE,
                true);
        String reason = fight.reason(stats.kills() > 0);
        graphics.text(
                font,
                font.plainSubstrByWidth(reason, panelWidth - 24),
                panelX + 12,
                panelY + 26,
                Graph.MUTED,
                true);
        if (mouseY >= panelY + 24 && mouseY < panelY + 38) {
            graphics.setTooltipForNextFrame(
                    Component.literal(reason + "\n" + date(fight)), mouseX, mouseY);
        }
        graphics.horizontalLine(panelX + 12, panelX + panelWidth - 12, panelY + 46, 0xFF333333);
        line(graphics, "Kill time", duration(fight), 56, 0xFF55FFFF);
        line(
                graphics,
                "Your damage",
                fight.damage < 0 ? "Unknown" : String.format(Locale.ROOT, "%,d", fight.damage),
                69,
                Hud.TITLE);
        line(
                graphics,
                "Net profit",
                Format.coins(stats.profit()) + " coins",
                87,
                stats.profit() >= 0 ? Graph.GREEN : Graph.RED);
        line(graphics, "Rewards value", Format.coins(stats.revenue()) + " coins", 100, Hud.GOLD);
        line(
                graphics,
                "Crystal costs (" + stats.crystals() + ")",
                Format.coins(tracker.ledger.fightSpend(fightId, Ledger.Kind.CRYSTAL)) + " coins",
                113,
                Graph.RED);
        line(
                graphics,
                "Calling costs (" + stats.callings() + ")",
                Format.coins(tracker.ledger.fightSpend(fightId, Ledger.Kind.CALLING)) + " coins",
                126,
                Graph.RED);
        graphics.horizontalLine(panelX + 12, panelX + panelWidth - 12, panelY + 140, 0xFF333333);
        List<String> itemIds = new ArrayList<>(stats.loot().keySet());
        itemIds.sort(
                Comparator.<String>comparingDouble(item -> values.getOrDefault(item, 0.0))
                        .reversed()
                        .thenComparing(item -> item));
        double coins = tracker.ledger.fightScavengerCoins(fightId);
        if (coins > 0) {
            // Coins get a reward row, but remain INCOME entries rather than item quantities.
            itemIds.addFirst(PurseCoins.ITEM);
            values.put(PurseCoins.ITEM, coins);
        }
        int top = panelY + 151,
                bottom = panelY + panelHeight - 52,
                visibleRows = Math.max(1, (bottom - top) / 13);
        scrollOffset =
                Math.max(0, Math.min(scrollOffset, Math.max(0, itemIds.size() - visibleRows)));
        if (itemIds.isEmpty()) {
            graphics.text(font, "No drops recorded.", panelX + 12, top, Graph.MUTED, true);
        }
        graphics.enableScissor(panelX + 10, top - 1, panelX + panelWidth - 10, bottom);
        for (int i = scrollOffset; i < Math.min(itemIds.size(), scrollOffset + visibleRows); i++) {
            String item = itemIds.get(i),
                    name =
                            item.equals(PurseCoins.ITEM)
                                    ? "Scavenger coins"
                                    : String.format(Locale.ROOT, "%,d", stats.loot().get(item))
                                            + "x "
                                            + Catalog.name(item);
            double value = values.getOrDefault(item, 0.0);
            boolean missingPrice = value == 0 && stats.unpricedLoot().getOrDefault(item, 0L) > 0;
            String price = missingPrice ? "Unpriced" : Format.coins(value) + " coins";
            int rowY = top + (i - scrollOffset) * 13,
                    right = panelX + panelWidth - 16 - font.width(price);
            graphics.text(
                    font,
                    font.plainSubstrByWidth(name, Math.max(25, right - panelX - 24)),
                    panelX + 12,
                    rowY,
                    Hud.itemColor(item),
                    true);
            graphics.text(font, price, right, rowY, value == 0 ? Graph.MUTED : Hud.GOLD, true);
            if (mouseY >= rowY - 1 && mouseY < rowY + 11) {
                graphics.setTooltipForNextFrame(
                        Component.literal(name + " · " + price), mouseX, mouseY);
            }
        }
        graphics.disableScissor();
        if (itemIds.size() > visibleRows) {
            int scrollTrackHeight = bottom - top,
                    scrollThumbHeight =
                            Math.max(6, scrollTrackHeight * visibleRows / itemIds.size()),
                    scrollThumbOffset =
                            (scrollTrackHeight - scrollThumbHeight)
                                    * scrollOffset
                                    / Math.max(1, itemIds.size() - visibleRows);
            graphics.fill(
                    panelX + panelWidth - 8, top, panelX + panelWidth - 7, bottom, 0xFF333333);
            graphics.fill(
                    panelX + panelWidth - 8,
                    top + scrollThumbOffset,
                    panelX + panelWidth - 7,
                    top + scrollThumbOffset + scrollThumbHeight,
                    0xFF888888);
        }
        String footer =
                stats.unpriced() > 0
                        ? stats.unpriced() + " unpriced · set prices or reprice session"
                        : date(fight) + " · Session " + fight.session;
        graphics.text(
                font,
                font.plainSubstrByWidth(footer, panelWidth - 24),
                panelX + 12,
                panelY + panelHeight - 42,
                Graph.MUTED,
                true);
        graphics.horizontalLine(
                panelX + 12, panelX + panelWidth - 12, panelY + panelHeight - 29, 0xFF333333);
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.components.Renderable renderable) {
                renderable.extractRenderState(graphics, mouseX, mouseY, delta);
            }
        }
    }

    private void line(
            GuiGraphicsExtractor graphics,
            String label,
            String value,
            int scrollThumbOffset,
            int color) {
        int right = panelX + panelWidth - 12 - font.width(value);
        graphics.text(
                font,
                font.plainSubstrByWidth(label + ":", Math.max(20, right - panelX - 24)),
                panelX + 12,
                panelY + scrollThumbOffset,
                Graph.MUTED,
                true);
        graphics.text(font, value, right, panelY + scrollThumbOffset, color, true);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollOffset = Math.max(0, scrollOffset - (int) Math.signum(vertical));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
