package sscextras.client;

import java.util.ArrayDeque;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/** Bounded, read-only delta feedback shared by native-style HUD gauges. */
public final class GaugeFeedback {
    private static final int LIFETIME = 24, MAX_VISIBLE = 48;
    private static final int IDLE_TICKS = 60 * 20;
    private record Popup(boolean gain, float born, float speed, int lane) { }
    private final ArrayDeque<Popup> popups = new ArrayDeque<>();
    private final Text loss, gain;
    private PlayerEntity previous;
    private int previousUnits, previousTick, sequence;
    private int lastChangeTick;
    private Object previousContext;
    private double previousDisplay;
    private long pendingLoss, pendingGain;
    private float nextLoss, nextGain, now;
    public GaugeFeedback(String key, String insertion) {
        loss = Text.translatable(key + "loss").styled(style -> style.withInsertion(insertion));
        gain = Text.translatable(key + "gain").styled(style -> style.withInsertion(insertion));
    }
    public void reset() { previous = null; previousContext = null; popups.clear(); pendingLoss = pendingGain = 0; sequence = 0; }
    public void update(PlayerEntity player, int units, int unitsPerPoint, float delta) {
        now = player.age + delta;
        if (previous != player || player.age < previousTick) {
            reset(); lastChangeTick = player.age;
        }
        else if (units != previousUnits) {
            lastChangeTick = player.age;
            boolean up = units > previousUnits;
            long count = (Math.abs((long)units - previousUnits) + unitsPerPoint - 1) / unitsPerPoint;
            spawn(up, now, count - 1);
            if (up) { if (pendingGain == 0) nextGain = now + spacing(count - 1); pendingGain += count - 1; }
            else { if (pendingLoss == 0) nextLoss = now + spacing(count - 1); pendingLoss += count - 1; }
        }
        previous = player; previousTick = player.age; previousUnits = units;
        popups.removeIf(popup -> (now - popup.born) * popup.speed >= LIFETIME);
        nextLoss = Math.max(nextLoss, now - 3 * spacing(pendingLoss));
        nextGain = Math.max(nextGain, now - 3 * spacing(pendingGain));
        for (int emitted = 0; emitted < 4 && pendingLoss > 0 && now >= nextLoss; emitted++) {
            spawn(false, nextLoss, pendingLoss); pendingLoss--; nextLoss += spacing(pendingLoss);
        }
        for (int emitted = 0; emitted < 4 && pendingGain > 0 && now >= nextGain; emitted++) {
            spawn(true, nextGain, pendingGain); pendingGain--; nextGain += spacing(pendingGain);
        }
    }
    /** Call after update, even while idle-hidden, so real changes wake this gauge immediately. */
    public boolean visible(boolean autoHide, Object context, double displayedValue) {
        if (!java.util.Objects.equals(previousContext, context) || Double.compare(previousDisplay, displayedValue) != 0) {
            previousContext = context; previousDisplay = displayedValue; lastChangeTick = previousTick;
        }
        return !autoHide || previousTick - lastChangeTick < IDLE_TICKS;
    }
    /** Draw from the supplied popup origin, rising at most 18 GUI pixels. */
    public void render(DrawContext context, int x, int y) {
        var renderer = MinecraftClient.getInstance().textRenderer;
        for (var popup : popups) {
            float age = (now - popup.born) * popup.speed;
            int alpha = Math.round(255 * Math.min(1, (LIFETIME - age) / 10));
            if (alpha < 5) continue;
            Text text = popup.gain ? gain : loss;
            float scale = Math.min(.75f, 25f / Math.max(1, renderer.getWidth(text)));
            context.getMatrices().push();
            context.getMatrices().translate(x + popup.lane, y - age * .75f, 0);
            context.getMatrices().scale(scale, scale, 1);
            context.drawTextWithShadow(renderer, text, 0, 0, alpha << 24 | (popup.gain ? 0x91edb5 : 0xffa698));
            context.getMatrices().pop();
        }
    }
    private static float spacing(long pending) { return pending <= 8 ? 8 : Math.max(.5f, 8f / (1 + pending / 8f)); }
    private void spawn(boolean gain, float time, long pending) {
        if (popups.size() == MAX_VISIBLE) popups.removeFirst();
        popups.addLast(new Popup(gain, time, 8 / spacing(pending), sequence++ % 3));
    }
}
