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
        String name = claim == null ? MountMerchantForms.name(player) : claim.name;
        String key = "message.ssc-extras.drake.pillager." + line;
        var text = line.equals("stable_rules") ? Text.translatable(key, name,
                claim == null ? DrakeRoaming.RANGE : DrakeStableLayout.roamRange(claim.stable)) : Text.translatable(key, name);
        player.sendMessage(text.formatted(Formatting.GRAY), false);
    }

    public static boolean escaping(PlayerEntity player) {
        var claim = DrakeOutpostOwnership.claim(player);
        return claim != null && claim.tryingToEscape;
    }
}
