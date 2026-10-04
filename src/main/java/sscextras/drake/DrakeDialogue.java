package sscextras.drake;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class DrakeDialogue {
    private DrakeDialogue() { }

    public static void say(LivingEntity mount, String line) {
        if (!(mount instanceof PlayerEntity player) || player.getWorld().isClient) return;
        var claim = DrakeOutpostOwnership.claim(player);
        String name = claim == null ? player.getName().getString() : claim.name;
        player.sendMessage(Text.translatable("message.ssc-extras.drake.pillager." + line, name).formatted(Formatting.GRAY), false);
    }

    public static boolean escaping(PlayerEntity player) {
        var claim = DrakeOutpostOwnership.claim(player);
        return claim != null && claim.tryingToEscape;
    }
}
