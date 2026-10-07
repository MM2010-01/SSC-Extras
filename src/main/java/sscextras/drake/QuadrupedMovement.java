package sscextras.drake;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public final class QuadrupedMovement {
    public static final float STEP_HEIGHT = 1.125f;
    private QuadrupedMovement() { }
    public static boolean quadrupedal(Entity entity) {
        return entity instanceof StableDrakeEntity || entity instanceof PlayerEntity player && EarthenDrake.onAllFours(player);
    }
}
