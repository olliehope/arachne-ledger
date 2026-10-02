package dev.arachneledger;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** A client-local title queue fed only after the tracker's reward duplicate checks. */
public final class RngAlerts {
    public static final long DURATION_MILLIS = 4_000;
    private static final long FADE_IN_MILLIS = 150, FADE_OUT_MILLIS = 700;
    private static final int MAX_QUEUED = 8;
    private final Deque<Notice> notices = new ArrayDeque<>();
    private record Notice(String item, int count, double value, long start) {}
    public record Display(String item, String title, String valueText, int color, double alpha) {}

    /** The value is the recorded drop value, not the whole fight's net profit. */
    public boolean notice(String item, int count, double unitValue, long now) {
        expire(now);
        if (rarityColor(item) == 0 || count <= 0 || notices.size() >= MAX_QUEUED) return false;
        double value = Double.isFinite(unitValue) && unitValue > 0 ? unitValue * count : 0;
        if (!Double.isFinite(value)) value = 0;
        long start = notices.isEmpty() ? now : Math.max(now, notices.peekLast().start() + DURATION_MILLIS);
        notices.addLast(new Notice(item, count, value, start));
        return true;
    }
    public Display current(long now) {
        expire(now);
        Notice notice = notices.peekFirst();
        if (notice == null || now < notice.start()) return null;
        long age = now - notice.start();
        double alpha = Math.min(1, Math.min(age / (double)FADE_IN_MILLIS,
            (DURATION_MILLIS - age) / (double)FADE_OUT_MILLIS));
        String title = (notice.count() > 1 ? notice.count() + "x " : "") + Catalog.name(notice.item());
        String value = notice.value() > 0 ? "+" + Format.coins(notice.value()) + " coins" : "Unpriced";
        return new Display(notice.item(), title, value, rarityColor(notice.item()), Math.max(0, alpha));
    }
    public void clear() { notices.clear(); }
    public int queued() { return notices.size(); }
    private void expire(long now) {
        while (!notices.isEmpty() && now - notices.peekFirst().start() >= DURATION_MILLIS) notices.removeFirst();
    }
    public static int rarityColor(String item) {
        if (item == null) return 0;
        return switch (item) {
            case "ARACHNE_FANG" -> 0xFF55FF55;
            case "TARANTULA_EPIC" -> 0xFFAA00AA;
            case "TARANTULA_LEGENDARY" -> 0xFFFFAA00;
            default -> 0;
        };
    }
    /** Own overlay so it does not overwrite server titles or other mods' notifications. */
    public void draw(GuiGraphicsExtractor g, long now) {
        draw(g,now,true);
    }
    public void draw(GuiGraphicsExtractor g, long now,boolean showValue) {
        Display display = current(now);
        if (display == null || display.alpha() < .02) return;
        var font = Minecraft.getInstance().font;
        var title = Component.literal(display.title()).withStyle(ChatFormatting.BOLD);
        int titleWidth = font.width(title), gap = showValue?10:0, valueWidth = showValue?font.width(display.valueText()):0;
        int totalWidth = titleWidth + gap + valueWidth;
        float scale = (float)Math.min(2, Math.max(.5, (g.guiWidth() - 24.0) / totalWidth));
        int valueColor = display.valueText().equals("Unpriced") ? 0xFFAAAAAA : 0xFF55FF55;
        g.pose().pushMatrix();
        g.pose().translate(g.guiWidth() / 2f, g.guiHeight() * .35f);
        g.pose().scale(scale);
        int left = -totalWidth / 2;
        g.text(font, title, left, 0, faded(display.color(), display.alpha()), true);
        if(showValue)g.text(font, display.valueText(), left + titleWidth + gap, 0, faded(valueColor, display.alpha()), true);
        String heading = "RARE DROP!";
        g.text(font, heading, -font.width(heading) / 2, -13, faded(0xFFAAAAAA, display.alpha()), true);
        g.pose().popMatrix();
    }
    private static int faded(int color, double alpha) {
        return ((int)Math.round(255 * alpha) << 24) | (color & 0xFFFFFF);
    }
}
