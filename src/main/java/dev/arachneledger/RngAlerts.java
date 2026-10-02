package dev.arachneledger;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/** A client-local title queue fed only after the tracker's reward duplicate checks. */
public final class RngAlerts {
    public static final long DURATION_MILLIS = 4_000;
    private static final long FADE_IN_MILLIS = 150;
    private static final long FADE_OUT_MILLIS = 700;
    private static final int MAX_QUEUED = 8;
    private final Deque<Notice> notices = new ArrayDeque<>();
    private long hiddenSince = -1;
    private long hiddenMillis;

    private record Notice(String item, int count, double value, long start) {}

    public record Display(String item, String title, String valueText, int color, double alpha) {}

    /** The value is the recorded drop value, not the whole fight's net profit. */
    public boolean notice(String item, int count, double unitValue, long now) {
        long visibleNow = visibleTime(now);
        expire(visibleNow);
        if (rarityColor(item) == 0 || count <= 0 || notices.size() >= MAX_QUEUED) {
            return false;
        }
        double value = Double.isFinite(unitValue) && unitValue > 0 ? unitValue * count : 0;
        if (!Double.isFinite(value)) {
            value = 0;
        }
        long start =
                notices.isEmpty()
                        ? visibleNow
                        : Math.max(visibleNow, notices.peekLast().start() + DURATION_MILLIS);
        notices.addLast(new Notice(item, count, value, start));
        return true;
    }

    public Display current(long now) {
        long visibleNow = visibleTime(now);
        expire(visibleNow);
        Notice notice = notices.peekFirst();
        if (notice == null || visibleNow < notice.start()) {
            return null;
        }
        long age = visibleNow - notice.start();
        double alpha =
                Math.min(
                        1,
                        Math.min(
                                age / (double) FADE_IN_MILLIS,
                                (DURATION_MILLIS - age) / (double) FADE_OUT_MILLIS));
        String title =
                (notice.count() > 1 ? notice.count() + "x " : "") + Catalog.name(notice.item());
        String value = notice.value() > 0 ? "+" + Format.coins(notice.value()) + " coins" : "";
        return new Display(
                notice.item(), title, value, rarityColor(notice.item()), Math.max(0, alpha));
    }

    /** Inventory screens and F1 hide this overlay; its four seconds should be visible time. */
    public void setVisible(boolean visible, long now) {
        if (!visible && hiddenSince < 0) {
            hiddenSince = now;
        } else if (visible && hiddenSince >= 0) {
            hiddenMillis += Math.max(0, now - hiddenSince);
            hiddenSince = -1;
        }
    }

    private long visibleTime(long now) {
        return now - hiddenMillis - (hiddenSince < 0 ? 0 : Math.max(0, now - hiddenSince));
    }

    public void clear() {
        notices.clear();
        hiddenSince = -1;
        hiddenMillis = 0;
    }

    public int queued() {
        return notices.size();
    }

    private void expire(long now) {
        while (!notices.isEmpty() && now - notices.peekFirst().start() >= DURATION_MILLIS) {
            notices.removeFirst();
        }
    }

    public static int rarityColor(String item) {
        if (item == null) {
            return 0;
        }
        return switch (item) {
            case "ARACHNE_FANG" -> 0xFF55FF55;
            case "TARANTULA_EPIC" -> 0xFFAA00AA;
            case "TARANTULA_LEGENDARY" -> 0xFFFFAA00;
            default -> 0;
        };
    }

    /** Own overlay so it does not overwrite server titles or other mods' notifications. */
    public void draw(GuiGraphicsExtractor graphics, long now) {
        draw(graphics, now, true);
    }

    public void draw(GuiGraphicsExtractor graphics, long now, boolean showValue) {
        Display display = current(now);
        if (display == null || display.alpha() < .02) {
            return;
        }
        var font = Minecraft.getInstance().font;
        var title = Component.literal(display.title()).withStyle(ChatFormatting.BOLD);
        // Omitted values reserve no gap, so the item title remains centered by itself.
        boolean drawValue = showValue && !display.valueText().isEmpty();
        int titleWidth = font.width(title),
                gap = drawValue ? 10 : 0,
                valueWidth = drawValue ? font.width(display.valueText()) : 0;
        int totalWidth = titleWidth + gap + valueWidth;
        float scale = (float) Math.min(2, Math.max(.5, (graphics.guiWidth() - 24.0) / totalWidth));
        graphics.pose().pushMatrix();
        graphics.pose().translate(graphics.guiWidth() / 2f, graphics.guiHeight() * .35f);
        graphics.pose().scale(scale);
        int left = -totalWidth / 2;
        graphics.text(font, title, left, 0, faded(display.color(), display.alpha()), true);
        if (drawValue) {
            graphics.text(
                    font,
                    display.valueText(),
                    left + titleWidth + gap,
                    0,
                    faded(0xFF55FF55, display.alpha()),
                    true);
        }
        String heading = "RARE DROP!";
        graphics.text(
                font,
                heading,
                -font.width(heading) / 2,
                -13,
                faded(0xFFAAAAAA, display.alpha()),
                true);
        graphics.pose().popMatrix();
    }

    private static int faded(int color, double alpha) {
        return ((int) Math.round(255 * alpha) << 24) | (color & 0xFFFFFF);
    }
}
