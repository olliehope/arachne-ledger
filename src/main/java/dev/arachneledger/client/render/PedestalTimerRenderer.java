package dev.arachneledger.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.arachneledger.client.ArachneLedger;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

/** Extracts one immutable timer label, then submits it at the altar during world rendering. */
public final class PedestalTimerRenderer {
    // Altar center verified against Dulkir's ArachneFeatures; raised slightly for readability.
    // https://github.com/inglettronald/DulkirMod-Fabric/blob/master/src/main/kotlin/com/dulkirfabric/features/ArachneFeatures.kt
    private static final Vec3 LABEL_POSITION = new Vec3(-282.5, 51.3, -178.5);
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final RenderStateDataKey<Label> TIMER =
            RenderStateDataKey.create(() -> "arachneledger:pedestal_timer");

    private record Line(FormattedCharSequence text, int width, int color) {}

    private record Label(Line heading, Line detail, float scale, boolean throughWalls) {}

    public static void register() {
        LevelRenderEvents.END_EXTRACTION.register(PedestalTimerRenderer::extract);
        LevelRenderEvents.COLLECT_SUBMITS.register(PedestalTimerRenderer::submit);
    }

    private static void extract(LevelExtractionContext context) {
        FabricRenderState state = (FabricRenderState) context.levelState();
        // Render states can be reused; a hidden frame must clear its previous label.
        state.setData(TIMER, null);
        Minecraft client = Minecraft.getInstance();
        var tracker = ArachneLedger.tracker;
        if (tracker == null
                || !tracker.ready()
                || client.player == null
                || client.level == null
                || context.level() != client.level
                || client.options.hideGui
                || !(tracker.config.farming.pedestalTimer
                        || tracker.config.farming.pedestalFightTime)) {
            return;
        }
        var view = tracker.pedestalTimer(System.currentTimeMillis());
        if (view == null) {
            return;
        }
        var options = tracker.config.farming;
        double range = options.pedestalRange;
        if (context.camera().position().distanceToSqr(LABEL_POSITION) > range * range) {
            return;
        }
        state.setData(
                TIMER,
                new Label(
                        line(client.font, view.label(), view.color()),
                        line(client.font, view.detail(), 0xBBBBBB),
                        (float) (0.025 * options.pedestalScale),
                        options.pedestalThroughWalls));
    }

    private static Line line(Font font, String text, int color) {
        Component component = Component.literal(text);
        return new Line(component.getVisualOrderText(), font.width(component), 0xFF000000 | color);
    }

    private static void submit(LevelRenderContext context) {
        Label label = ((FabricRenderState) context.levelState()).getData(TIMER);
        var camera = context.levelState().cameraRenderState;
        PoseStack poses = context.poseStack();
        if (label == null || camera == null || !camera.initialized || poses == null) {
            return;
        }
        Font.DisplayMode mode =
                label.throughWalls() ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        poses.pushPose();
        try {
            poses.translate(
                    LABEL_POSITION.x - camera.pos.x,
                    LABEL_POSITION.y - camera.pos.y,
                    LABEL_POSITION.z - camera.pos.z);
            poses.mulPose(camera.orientation);
            poses.scale(label.scale(), -label.scale(), label.scale());
            submitLine(context, label.heading(), -11, mode);
            if (label.detail().width() > 0) {
                poses.translate(0, 2, 0);
                poses.scale(0.75f, 0.75f, 0.75f);
                submitLine(context, label.detail(), 0, mode);
            }
        } finally {
            poses.popPose();
        }
    }

    private static void submitLine(
            LevelRenderContext context, Line line, float y, Font.DisplayMode mode) {
        context.submitNodeCollector()
                .submitText(
                        context.poseStack(),
                        -line.width() / 2f,
                        y,
                        line.text(),
                        true,
                        mode,
                        FULL_BRIGHT,
                        line.color(),
                        0x55000000,
                        0);
    }

    private PedestalTimerRenderer() {}
}
