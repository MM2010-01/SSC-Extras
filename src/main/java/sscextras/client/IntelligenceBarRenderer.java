package sscextras.client;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.util.UIPositionUtils;
import sscextras.player.PlayerMind;

/** Read-only companion to SSC's native gauge; uses its textures, anchor and HUD lifecycle. */
public final class IntelligenceBarRenderer {
    private static final Identifier FULL = texture("instinct_bar_full"), EMPTY = texture("instinct_bar_empty");
    private static final Identifier FALLING = texture("instinct_bar_decrease"), FALLING_EMPTY = texture("instinct_bar_decrease_empty");
    private static final Identifier LOCK = texture("instinct_bar_lock");
    private static final DecimalFormat POINTS = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private PlayerEntity previousPlayer;
    private double previousPoints = -1;
    private int previousTick, fallingUntil;
    private Text label = Text.empty();
    private double labelledPoints = -1;

    private static Identifier texture(String name) { return new Identifier("shape-shifter-curse", "textures/gui/" + name + ".png"); }

    public void render(DrawContext context) {
        var client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null || client.options.hudHidden || client.interactionManager == null
                || !client.interactionManager.hasStatusBars() || !PlayerMind.enabled(player)
                || FormAbilityManager.getForm(player).equals(RegPlayerForms.ORIGINAL_BEFORE_ENABLE)) {
            previousPlayer = null;
            return;
        }
        double points = PlayerMind.effectivePoints(player);
        if (previousPlayer != player || player.age < previousTick) {
            fallingUntil = 0;
            labelledPoints = -1;
        } else if (points < previousPoints) fallingUntil = player.age + 20;
        else if (points > previousPoints) fallingUntil = 0;
        previousPlayer = player; previousTick = player.age; previousPoints = points;
        if (labelledPoints != points) {
            label = Text.translatable("hud.ssc-extras.int", POINTS.format(points), PlayerMind.MAX_POINTS)
                    .styled(style -> style.withInsertion("ssc-extras:mental_hint"));
            labelledPoints = points;
        }
        var config = ShapeShifterCurseFabric.clientConfig;
        var position = UIPositionUtils.getCorrectPosition(config.instinctBarPosType,
                config.instinctBarPosOffsetX, config.instinctBarPosOffsetY);
        int x = Math.max(0, Math.min(context.getScaledWindowWidth() - 80, position.getLeft()));
        int y = Math.max(10, Math.min(context.getScaledWindowHeight() - 5, position.getRight() - 24));
        int width = (int)Math.ceil(80 * points / PlayerMind.MAX_POINTS);
        boolean falling = player.age < fallingUntil;
        context.drawTexture(falling ? FALLING_EMPTY : EMPTY, x, y, 0, 0, 80, 5, 80, 5);
        // A full reserve empties left-to-right, opposite the native instinct buildup.
        if (width > 0) context.drawTexture(falling ? FALLING : FULL, x + 80 - width, y, 80 - width, 0, width, 5, 80, 5);
        for (int boundary = 1; boundary < 5; boundary++) context.fill(x + boundary * 16, y + 1, x + boundary * 16 + 1, y + 4, 0xff303947);
        if (PlayerMind.effectiveInt(player) != PlayerMind.baseInt(player)) context.drawTexture(LOCK, x, y, 0, 0, 80, 5, 80, 5);
        context.getMatrices().push();
        context.getMatrices().translate(x, y - 9, 0);
        float scale = Math.min(.75f, 80f / Math.max(1, client.textRenderer.getWidth(label)));
        context.getMatrices().scale(scale, scale, 1);
        context.drawTextWithShadow(client.textRenderer, label, 0, 0, falling ? 0xffffb393 : 0xffb9e2ed);
        context.getMatrices().pop();
    }
}
