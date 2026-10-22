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
        int percent = Math.min(100, (int)(progress * 100));
        feedback.update(player, percent, 1, delta);
        var config = ShapeShifterCurseFabric.clientConfig;
        var anchor = UIPositionUtils.getCorrectPosition(config.instinctBarPosType, config.instinctBarPosOffsetX, config.instinctBarPosOffsetY);
        int x = Math.max(0, Math.min(context.getScaledWindowWidth() - 80, anchor.getLeft()));
        int y = Math.max(32, Math.min(context.getScaledWindowHeight() - 5, anchor.getRight() - 68));
        context.drawTexture(EMPTY, x, y, 0, 0, 80, 5, 80, 5);
        int width = (int)(80 * progress);
        if (width > 0) context.drawTexture(FULL, x, y, 0, 0, width, 5, 80, 5);
        if (QuadrupedalTrainer.complete(player)) context.drawTexture(LOCK, x, y, 0, 0, 80, 5, 80, 5);
        context.drawTexture(percent == 0 ? BIPED : ICON, x, y - 27, 0, 0, 24, 24, 24, 24);
        Text label = percent == 0 ? Text.translatable("hud.ssc-extras.quad.biped") : Text.translatable("hud.ssc-extras.quad.label", percent);
        float scale = Math.min(.75f, 55f / Math.max(1, client.textRenderer.getWidth(label)));
        context.getMatrices().push(); context.getMatrices().translate(x + 25, y - 19, 0); context.getMatrices().scale(scale, scale, 1);
        context.drawTextWithShadow(client.textRenderer, label, 0, 0, 0xffdfb5eb); context.getMatrices().pop();
        feedback.render(context, x, y);
    }
}
