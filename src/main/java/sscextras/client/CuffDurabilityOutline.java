package sscextras.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import sscextras.cuffs.MetalCuffs;

public final class CuffDurabilityOutline {
    private CuffDurabilityOutline() { }

    public static void render(DrawContext context, int x, int y, PlayerEntity player) {
        float fraction = MetalCuffs.durabilityFraction(player);
        if (fraction < 0) return;
        int left = x - 2, top = y - 2, width = 84, height = 9;
        outline(context, left, top, width, height, 0xff424c56);
        int filled = Math.round(width * fraction);
        if (filled == 0) return;
        int color = fraction < .1f ? 0xffef7770 : fraction < .25f ? 0xffe8bd68 : 0xffa4d8d4;
        int start = left + width - filled;
        context.fill(start, top, left + width, top + 1, color);
        context.fill(start, top + height - 1, left + width, top + height, color);
        context.fill(left + width - 1, top + 1, left + width, top + height - 1, color);
        if (filled == width) context.fill(left, top + 1, left + 1, top + height - 1, color);
    }

    private static void outline(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
