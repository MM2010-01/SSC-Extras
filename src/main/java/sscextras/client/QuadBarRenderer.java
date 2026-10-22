package sscextras.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.util.UIPositionUtils;
import sscextras.trainer.QuadrupedalTrainer;

public final class QuadBarRenderer {
    private static final Identifier EMPTY = nativeTexture("instinct_bar_empty"), FULL = nativeTexture("instinct_bar_full"), LOCK = nativeTexture("instinct_bar_lock");
    private static final Identifier ICON = QuadrupedalTrainer.id("textures/gui/quad_legs.png");
    private static final Identifier BIPED = QuadrupedalTrainer.id("textures/gui/biped_legs.png");
    private final GaugeFeedback feedback = new GaugeFeedback("hud.ssc-extras.quad.", "ssc-extras:physical_hint");
    private static Identifier nativeTexture(String name) { return new Identifier("shape-shifter-curse", "textures/gui/" + name + ".png"); }
    public void reset() { feedback.reset(); }
    public void render(DrawContext context, float delta) {
        var client = MinecraftClient.getInstance(); var player = client.player;
        if (player == null || client.options.hudHidden || client.interactionManager == null || !client.interactionManager.hasStatusBars()) { reset(); return; }
        float progress = QuadrupedalTrainer.quadProgress(player);
        int tenths = Math.min(1000, (int)(progress * 1000));
        feedback.update(player, tenths, 10, delta);
        var config = ShapeShifterCurseFabric.clientConfig;
        var anchor = UIPositionUtils.getCorrectPosition(config.instinctBarPosType, config.instinctBarPosOffsetX, config.instinctBarPosOffsetY);
        int x = Math.max(0, Math.min(context.getScaledWindowWidth() - 80, anchor.getLeft()));
        int y = Math.max(43, Math.min(context.getScaledWindowHeight() - 5, anchor.getRight() - 68));
        context.drawTexture(EMPTY, x, y, 0, 0, 80, 5, 80, 5);
        int width = (int)(80 * progress);
        if (width > 0) context.drawTexture(FULL, x, y, 0, 0, width, 5, 80, 5);
        if (QuadrupedalTrainer.complete(player)) context.drawTexture(LOCK, x, y, 0, 0, 80, 5, 80, 5);
        context.drawTexture(BIPED, x, y - 36, 0, 0, 24, 24, 24, 24);
        context.drawTexture(ICON, x + 56, y - 36, 0, 0, 24, 24, 24, 24);
        label(context, Text.translatable("hud.ssc-extras.quad.biped"), x, y - 9, false);
        label(context, Text.translatable("hud.ssc-extras.quad.quad"), x + 80, y - 9, true);
        Text percent = Text.literal(tenths % 10 == 0 ? (tenths / 10) + "%" : (tenths / 10) + "." + (tenths % 10) + "%");
        var matrices = context.getMatrices(); matrices.push(); matrices.translate(x + 40, y - 24, 0); matrices.scale(.65f, .65f, 1);
        context.drawTextWithShadow(client.textRenderer, percent, -client.textRenderer.getWidth(percent) / 2, 0, 0xffdfb5eb); matrices.pop();
        feedback.render(context, x, y);
    }
    private static void label(DrawContext context, Text text, int x, int y, boolean right) {
        var renderer = MinecraftClient.getInstance().textRenderer;
        float scale = Math.min(.65f, 36f / Math.max(1, renderer.getWidth(text)));
        var matrices = context.getMatrices(); matrices.push(); matrices.translate(x, y, 0); matrices.scale(scale, scale, 1);
        context.drawTextWithShadow(renderer, text, right ? -renderer.getWidth(text) : 0, 0, 0xffdfb5eb); matrices.pop();
    }
}
