package dev.arachneledger;

import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** A snapshot of server-provided HUD data, including a reason when detection is waiting. */
public final class GameContext {
    public record Snapshot(boolean onHypixel, boolean skyBlock, boolean sanctuary,
                           String detectionReason, String location,
                           List<String> sidebarLines, List<String> tabLines) {
        public Snapshot {
            sidebarLines = List.copyOf(sidebarLines);
            tabLines = List.copyOf(tabLines);
        }
    }

    public static Snapshot snapshot(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null)
            return new Snapshot(false, false, false, "Not connected to a world", "", List.of(), List.of());
        var connection = mc.getConnection();
        var server = mc.getCurrentServer();
        String host = server == null ? "" : server.ip;
        Scoreboard board = mc.level.getScoreboard();
        Objective sidebar = activeSidebar(board, mc.player.getScoreboardName());
        String title = sidebar == null ? "" : sidebar.getDisplayName().getString();
        List<String> rows = new ArrayList<>();
        if (sidebar != null) {
            board.listPlayerScores(sidebar).stream().filter(entry -> !entry.isHidden())
                .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed()
                    .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
                .limit(15).forEach(entry -> {
                    // ownerName() respects modern per-score display components; team prefix/suffix are still needed.
                    String row = PlayerTeam.formatNameForTeam(board.getPlayersTeam(entry.owner()), entry.ownerName()).getString();
                    rows.add(LocationDetection.clean(row));
                });
        }
        List<String> tab = connection.getListedOnlinePlayers().stream()
            .map(info -> LocationDetection.clean(mc.gui.getTabList().getNameForDisplay(info).getString())).toList();
        var result = LocationDetection.detect(host, connection.serverBrand(), title, rows, tab);
        List<String> diagnosticSidebar = new ArrayList<>();
        if (!title.isEmpty()) diagnosticSidebar.add("Title: " + LocationDetection.clean(title));
        diagnosticSidebar.addAll(rows);
        return new Snapshot(result.onHypixel(), result.skyBlock(), result.sanctuary(), result.detectionReason(),
            result.location(), diagnosticSidebar, tab);
    }

    /** Mirrors the vanilla visible-sidebar choice, preventing stale hidden objectives from winning. */
    static Objective activeSidebar(Scoreboard board, String playerName) {
        var team = board.getPlayersTeam(playerName);
        if (team != null) {
            DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
            Objective colored = slot == null ? null : board.getDisplayObjective(slot);
            if (colored != null) return colored;
        }
        return board.getDisplayObjective(DisplaySlot.SIDEBAR);
    }

    public static boolean arena(Minecraft mc) { return snapshot(mc).sanctuary(); }
    private GameContext() {}
}
