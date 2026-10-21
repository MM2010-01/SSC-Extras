package sscextras.petrification;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;

public final class PetrificationTargeting {
    private PetrificationTargeting() { }
    public static boolean allows(LivingEntity attacker, LivingEntity target) {
        if (!Petrification.stone(target)) return true;
        if (attacker == null) return false;
        return attacker.getAttacker() == target
                || attacker instanceof MobEntity mob && mob.getTarget() == target
                || attacker.getBrain().hasMemoryModuleWithValue(MemoryModuleType.ATTACK_TARGET, target)
                || attacker instanceof Angerable angry && target.getUuid().equals(angry.getAngryAt())
                || attacker.getBrain().hasMemoryModuleWithValue(MemoryModuleType.ANGRY_AT, target.getUuid());
    }
}
