package sscextras.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayDeque;
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
    private static final Identifier BRAINS = new Identifier("ssc-extras", "textures/gui/int_brains.png");
    private static final Text LOSS = feedback("loss"), GAIN = feedback("gain");
    private static final int LIFETIME = 32, MAX_VISIBLE = 48;
    private record Popup(boolean gain, float born, float speed, int lane) { }
    private final ArrayDeque<Popup> popups = new ArrayDeque<>();
    private PlayerEntity previousPlayer;
    private double previousPoints = -1;
    private int previousUnits, previousTick, fallingUntil, sequence;
    private long pendingLoss, pendingGain;
    private float nextLoss, nextGain;

    private static Identifier texture(String name) { return new Identifier("shape-shifter-curse", "textures/gui/" + name + ".png"); }
    private static Text feedback(String sign) {
        return Text.translatable("hud.ssc-extras.int." + sign)
                .styled(style -> style.withInsertion("ssc-extras:mental_hint"));
    }

    /** Also called when the native HUD callback is skipped (F1 or no world). */
    public void reset() {
        previousPlayer = null;
        popups.clear(); pendingLoss = pendingGain = 0;
        fallingUntil = 0; sequence = 0;
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
        float now = player.age + tickDelta;
        if (previousPlayer != player || player.age < previousTick) {
            reset();
        } else {
            if (points < previousPoints) fallingUntil = player.age + 20;
            else if (points > previousPoints) fallingUntil = 0;
            // Compare the saved reserve: temporary caps must not invent point gains/losses.
            if (units != previousUnits) {
                boolean gain = units > previousUnits;
                long count = (Math.abs((long)units - previousUnits) + 99) / 100;
                spawn(gain, now, count - 1);
                if (gain) { if (pendingGain == 0) nextGain = now + spacing(count - 1); pendingGain += count - 1; }
                else { if (pendingLoss == 0) nextLoss = now + spacing(count - 1); pendingLoss += count - 1; }
            }
        }
        previousPlayer = player; previousTick = player.age; previousPoints = points;
        previousUnits = units;
        popups.removeIf(popup -> (now - popup.born) * popup.speed >= LIFETIME);
        // Queue counts rather than hundreds of objects; large changes play faster.
        nextLoss = Math.max(nextLoss, now - 3 * spacing(pendingLoss));
        nextGain = Math.max(nextGain, now - 3 * spacing(pendingGain));
        for (int emitted = 0; emitted < 4 && pendingLoss > 0 && now >= nextLoss; emitted++) {
            spawn(false, nextLoss, pendingLoss); pendingLoss--; nextLoss += spacing(pendingLoss);
        }
        for (int emitted = 0; emitted < 4 && pendingGain > 0 && now >= nextGain; emitted++) {
            spawn(true, nextGain, pendingGain); pendingGain--; nextGain += spacing(pendingGain);
        }
        var config = ShapeShifterCurseFabric.clientConfig;
        var position = UIPositionUtils.getCorrectPosition(config.instinctBarPosType,
                config.instinctBarPosOffsetX, config.instinctBarPosOffsetY);
        int x = Math.max(0, Math.min(context.getScaledWindowWidth() - 80, position.getLeft()));
        int y = Math.max(52, Math.min(context.getScaledWindowHeight() - 5, position.getRight() - 24));
        var level = PlayerMind.effectiveInt(player);
        int tier = level.ordinal();
        double remaining = tier == 0 ? 0 : points - (tier - 1) * PlayerMind.POINTS_PER_LEVEL;
        int width = Math.max(0, Math.min(80, (int)Math.ceil(80 * remaining / PlayerMind.POINTS_PER_LEVEL)));
        boolean falling = player.age < fallingUntil;
        context.drawTexture(falling ? FALLING_EMPTY : EMPTY, x, y, 0, 0, 80, 5, 80, 5);
        // Fill from the left edge; losing points retreats the right edge.
        if (width > 0) context.drawTexture(falling ? FALLING : FULL, x, y, 0, 0, width, 5, 80, 5);
        if (PlayerMind.effectiveInt(player) != PlayerMind.baseInt(player)) context.drawTexture(LOCK, x, y, 0, 0, 80, 5, 80, 5);
        int sprite = tier == 4 ? 0 : 5 - tier;
        RenderSystem.enableBlend();
        context.drawTexture(BRAINS, x, y - 27, 24, 24,
                (sprite % 3) * 512f, (sprite / 3) * 512f, 512, 512, 1536, 1024);
        RenderSystem.disableBlend();
        Text label = level.text().copy().styled(style -> style.withInsertion("ssc-extras:mental_hint"));
        float labelScale = Math.min(.75f, 55f / Math.max(1, client.textRenderer.getWidth(label)));
        context.getMatrices().push();
        context.getMatrices().translate(x + 25, y - 19, 0);
        context.getMatrices().scale(labelScale, labelScale, 1);
        context.drawTextWithShadow(client.textRenderer, label, 0, 0, 0xfff0dce8);
        context.getMatrices().pop();
        for (var popup : popups) {
            float age = (now - popup.born) * popup.speed;
            int alpha = Math.round(255 * Math.min(1, (LIFETIME - age) / 10));
            if (alpha < 5) continue;
            Text text = popup.gain ? GAIN : LOSS;
            float scale = Math.min(.75f, 25f / Math.max(1, client.textRenderer.getWidth(text)));
            context.getMatrices().push();
            context.getMatrices().translate(x + 25 + popup.lane, y - 34 - age, 0);
            context.getMatrices().scale(scale, scale, 1);
            context.drawTextWithShadow(client.textRenderer, text, 0, 0,
                    alpha << 24 | (popup.gain ? 0x91edb5 : 0xffa698));
            context.getMatrices().pop();
        }
    }

    private static float spacing(long pending) { return pending <= 8 ? 8 : Math.max(.5f, 8f / (1 + pending / 8f)); }
    private void spawn(boolean gain, float now, long pending) {
        if (popups.size() == MAX_VISIBLE) popups.removeFirst();
        popups.addLast(new Popup(gain, now, 8 / spacing(pending), sequence++ % 3));
    }
}
