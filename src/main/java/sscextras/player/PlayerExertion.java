package sscextras.player;

import net.minecraft.entity.player.PlayerEntity;
import sscextras.drake.DrakeEquipment;
import sscextras.drake.DrakeShoes;

/** Read-only modifiers for native sprint travel and sprint-jump exhaustion. */
public final class PlayerExertion {
    private PlayerExertion() { }

    public static float sprintExhaustion(PlayerEntity player, float exhaustion) {
        if (!player.isSprinting()) return exhaustion;
        if (PlayerStance.quadrupedal(player)) exhaustion *= .7f;
        if (DrakeShoes.feet(player) || !DrakeEquipment.footClaws(player).isEmpty()) exhaustion *= .7f;
        return exhaustion;
    }
}
