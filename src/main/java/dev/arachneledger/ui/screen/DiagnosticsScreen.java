package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.client.GameContext;
import dev.arachneledger.diagnostics.DiagnosticReport;
import dev.arachneledger.diagnostics.TrackingDiagnostics;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Live eligibility and bounded event history; viewing or exporting never mutates tracking. */
public final class DiagnosticsScreen extends Screen {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private final Screen parent;
    private final Tracker tracker = ArachneLedger.tracker;
    private int panelX, panelY, panelWidth, panelHeight, offset;
    private TrackingDiagnostics.Result filter;
    private String note = "";

    public DiagnosticsScreen(Screen parent) {
        super(Component.literal("Tracking diagnostics"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(510, width - 20);
        panelHeight = Math.min(328, height - 16);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int buttonWidth = (panelWidth - 32) / 4;
        addButton(
                0,
                buttonWidth,
                filter == null
                        ? "All"
                        : filter == TrackingDiagnostics.Result.ACCEPTED ? "Accepted" : "Ignored",
                "Filter observations. Informational events appear in All.",
                () -> {
                    filter =
                            filter == null
                                    ? TrackingDiagnostics.Result.ACCEPTED
                                    : filter == TrackingDiagnostics.Result.ACCEPTED
                                            ? TrackingDiagnostics.Result.IGNORED
                                            : null;
                    offset = 0;
                    rebuildWidgets();
                });
        addButton(
                1,
                buttonWidth,
                "Copy report",
                "Copy live state, relevant observations and sidebar/tab lines.",
                () -> {
                    minecraft.keyboardHandler.setClipboard(report());
                    note = "Report copied.";
                });
        addButton(
                2,
                buttonWidth,
                "Save report",
                "Save the report to config/arachneledger/detection-debug.txt.",
                this::saveReport);
        addButton(
                3,
                buttonWidth,
                "Clear log",
                "Clear this local observation history; recorded loot stays unchanged.",
                () -> {
                    tracker.diagnostics.clear();
                    offset = 0;
                    note = "Observation history cleared.";
                });
        addRenderableWidget(
                new FlatButton(
                        panelX + 12,
                        panelY + panelHeight - 24,
                        panelWidth - 24,
                        20,
                        "Back",
                        false,
                        this::onClose));
    }

    private void addButton(int index, int buttonWidth, String label, String tip, Runnable action) {
        var button =
                new FlatButton(
                        panelX + 12 + index * (buttonWidth + 2),
                        panelY + 78,
                        buttonWidth,
                        20,
                        label,
                        false,
                        action);
        button.setTooltip(Tooltip.create(Component.literal(tip)));
        addRenderableWidget(button);
    }

    private String report() {
        var context = GameContext.snapshot(minecraft);
        long now = System.currentTimeMillis();
        String version =
                FabricLoader.getInstance()
                        .getModContainer("arachneledger")
                        .orElseThrow()
                        .getMetadata()
                        .getVersion()
                        .getFriendlyString();
        return DiagnosticReport.create(
                version,
                tracker.detectionSnapshot(now),
                tracker.diagnostics.newestFirst(),
                context.sidebarLines(),
                context.tabLines(),
                now);
    }

    private void saveReport() {
        var path =
                FabricLoader.getInstance()
                        .getConfigDir()
                        .resolve("arachneledger/detection-debug.txt");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, report());
            note = "Saved detection-debug.txt.";
        } catch (IOException exception) {
            note = "Could not save report: " + exception.getMessage();
        }
    }

    private List<TrackingDiagnostics.Event> events() {
        return tracker.diagnostics.newestFirst().stream()
                .filter(event -> filter == null || event.result() == filter)
                .toList();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= panelX
                && x < panelX + panelWidth
                && y >= panelY + 104
                && y < panelY + panelHeight - 38
                && vertical != 0) {
            offset =
                    Math.max(
                            0,
                            Math.min(
                                    Math.max(0, events().size() - visibleRows()),
                                    offset - (int) Math.signum(vertical)));
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    private int visibleRows() {
        return Math.max(1, (panelHeight - 148) / 26);
    }

    private void text(GuiGraphicsExtractor graphics, String value, int y, int color) {
        graphics.text(
                font,
                font.plainSubstrByWidth(value, panelWidth - 24),
                panelX + 12,
                panelY + y,
                color,
                false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        graphics.fill(0, 0, width, height, 0xDF101010);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        super.extractRenderState(graphics, x, y, delta);
        var state = tracker.detectionSnapshot(System.currentTimeMillis());
        text(graphics, "Tracking diagnostics", 12, Hud.TITLE);
        text(graphics, state.timer() + " | " + state.status(), 30, Hud.WHITE);
        text(graphics, "Location: " + state.location(), 43, Graph.MUTED);
        text(
                graphics,
                "Fight "
                        + state.fightId()
                        + " / "
                        + state.outcome()
                        + " | Damage "
                        + state.damage()
                        + " / "
                        + state.minimumDamage(),
                56,
                Graph.MUTED);
        text(
                graphics,
                "Pickups: "
                        + (state.pickups() ? "open" : "closed")
                        + " | Drop labels: "
                        + (state.labels() ? "open" : "closed"),
                68,
                Graph.MUTED);
        List<TrackingDiagnostics.Event> rows = events();
        offset = Math.min(offset, Math.max(0, rows.size() - visibleRows()));
        if (rows.isEmpty()) text(graphics, "No observations yet.", 110, Graph.MUTED);
        for (int index = offset; index < Math.min(rows.size(), offset + visibleRows()); index++) {
            var event = rows.get(index);
            int yAt = 108 + (index - offset) * 26;
            int color =
                    event.result() == TrackingDiagnostics.Result.ACCEPTED
                            ? Graph.GREEN
                            : event.result() == TrackingDiagnostics.Result.IGNORED
                                    ? Hud.TITLE
                                    : Graph.MUTED;
            text(
                    graphics,
                    TIME.format(Instant.ofEpochMilli(event.at()))
                            + " "
                            + event.kind()
                            + " · "
                            + event.detail()
                            + (event.repeats() > 1 ? " (x" + event.repeats() + ")" : ""),
                    yAt,
                    color);
            text(graphics, event.reason(), yAt + 11, Graph.MUTED);
            if (x >= panelX + 12
                    && x < panelX + panelWidth - 12
                    && y >= panelY + yAt
                    && y < panelY + yAt + 23) {
                graphics.setTooltipForNextFrame(
                        Component.literal(
                                event.result() + ": " + event.detail() + "\n" + event.reason()),
                        x,
                        y);
            }
        }
        text(
                graphics,
                note.isEmpty() ? "Scroll for older events · local history only" : note,
                panelHeight - 37,
                Graph.MUTED);
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
