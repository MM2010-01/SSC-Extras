package sscextras.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.util.UIPositionUtils;
import sscextras.player.PlayerMind;
import sscextras.SscExtrasConfig;

/** Read-only companion to SSC's native gauge; uses its textures, anchor and HUD lifecycle. */
public final class IntelligenceBarRenderer {
    private static final Identifier FULL = texture("instinct_bar_full"), EMPTY = texture("instinct_bar_empty");
    private static final Identifier LOCK = texture("instinct_bar_lock");
    private static final Identifier BRAINS = new Identifier("ssc-extras", "textures/gui/int_brains.png");
    private final GaugeFeedback feedback = new GaugeFeedback("hud.ssc-extras.int.", "ssc-extras:mental_hint", 5);

    private static Identifier texture(String name) { return new Identifier("shape-shifter-curse", "textures/gui/" + name + ".png"); }
    /** Also called when the native HUD callback is skipped (F1 or no world). */
    public void reset() {
        feedback.reset();
    }

    public void render(DrawContext context, float tickDelta) {
        var client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null || client.options.hudHidden || client.interactionManager == null
                || !client.interactionManager.hasStatusBars() || !PlayerMind.enabled(player)
                || FormAbilityManager.getForm(player).equals(RegPlayerForms.ORIGINAL_BEFORE_ENABLE)) {
            reset();
            return;
        }
        double points = PlayerMind.effectivePoints(player);
        int units = (int)Math.round(PlayerMind.points(player) * 100);
        // Compare saved reserve, so temporary caps cannot invent gains or losses.
        feedback.update(player, units, 100, tickDelta);
        if (!feedback.visible(SscExtrasConfig.intHudAutoHide(), FormAbilityManager.getForm(player).FormID, points)) return;
        var config = ShapeShifterCurseFabric.clientConfig;
        var position = UIPositionUtils.getCorrectPosition(config.instinctBarPosType,
                config.instinctBarPosOffsetX, config.instinctBarPosOffsetY);
        int x = Math.max(0, Math.min(context.getScaledWindowWidth() - 80, position.getLeft()));
        int y = Math.max(52, Math.min(context.getScaledWindowHeight() - 5, position.getRight() - 24));
        var level = PlayerMind.effectiveInt(player);
        int tier = level.ordinal();
        double remaining = tier == 0 ? 0 : points - (tier - 1) * PlayerMind.POINTS_PER_LEVEL;
        int width = Math.max(0, Math.min(80, (int)Math.ceil(80 * remaining / PlayerMind.POINTS_PER_LEVEL)));
        context.drawTexture(EMPTY, x, y, 0, 0, 80, 5, 80, 5);
        // Fill from the left edge; losing points retreats the right edge.
        if (width > 0) context.drawTexture(FULL, x, y, 0, 0, width, 5, 80, 5);
        if (PlayerMind.effectiveInt(player) != PlayerMind.baseInt(player)) context.drawTexture(LOCK, x, y, 0, 0, 80, 5, 80, 5);
        int sprite = tier == 4 ? 0 : 5 - tier;
        RenderSystem.enableBlend();
        context.drawTexture(BRAINS, x, y - 27, 24, 24,
                (sprite % 3) * 512f, (sprite / 3) * 512f, 512, 512, 1536, 1024);
        RenderSystem.disableBlend();
        Text label = level.text().copy().styled(style -> style.withInsertion("ssc-extras:mental_hint"));
        float labelScale = Math.min(.75f, 26f / Math.max(1, client.textRenderer.getWidth(label)));
        context.getMatrices().push();
        context.getMatrices().translate(x + 25, y - 19, 0);
        context.getMatrices().scale(labelScale, labelScale, 1);
        context.drawTextWithShadow(client.textRenderer, label, 0, 0, 0xfff0dce8);
        context.getMatrices().pop();
        feedback.render(context, x + 53, y - 9);
    }
}
