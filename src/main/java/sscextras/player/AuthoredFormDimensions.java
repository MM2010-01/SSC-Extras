package sscextras.player;

import net.minecraft.entity.LivingEntity;
import virtuoel.pehkui.api.ScaleType;
import virtuoel.pehkui.api.ScaleTypes;

/** Native Pehkui physical size for meshes already authored at their final visual scale. */
public final class AuthoredFormDimensions {
    private AuthoredFormDimensions() { }
    public static void apply(LivingEntity entity, float width, float height, float eyeHeight) {
        if (entity.getWorld().isClient) return;
        set(entity, ScaleTypes.WIDTH, 1); set(entity, ScaleTypes.HEIGHT, 1);
        set(entity, ScaleTypes.HITBOX_WIDTH, width / .6f);
        set(entity, ScaleTypes.HITBOX_HEIGHT, height / 1.8f);
        set(entity, ScaleTypes.EYE_HEIGHT, eyeHeight / 1.62f);
    }
    public static void reset(LivingEntity entity) {
        if (entity.getWorld().isClient) return;
        set(entity, ScaleTypes.HITBOX_WIDTH, 1); set(entity, ScaleTypes.HITBOX_HEIGHT, 1);
        set(entity, ScaleTypes.EYE_HEIGHT, 1);
    }
    private static void set(LivingEntity entity, ScaleType type, float amount) {
        var data = type.getScaleData(entity); data.setScale(amount); data.setPersistence(true);
    }
}
