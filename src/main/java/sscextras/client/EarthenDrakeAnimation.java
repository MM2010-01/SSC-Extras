package sscextras.client;

import com.google.gson.JsonObject;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.onixary.shapeShifterCurseFabric.render.form_render.DefaultModelAnimationSystem;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderer;
import sscextras.drake.EarthenDrake;

public final class EarthenDrakeAnimation extends DefaultModelAnimationSystem {
    private int stage;

    @Override public void loadConfig(JsonObject config) {
        super.loadConfig(config);
        stage = config == null ? 0 : config.get("stage").getAsInt();
    }

    @Override public void processAnimation(FormRenderer formRenderer, FormModel model, PlayerEntityRenderer renderer,
            PlayerEntity player, float limbAngle, float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
        super.processAnimation(formRenderer, model, renderer, player, limbAngle, limbDistance, tickDelta, age, headYaw, headPitch);
        var tail = model.getCachedGeoBone("tail_0");
        if (tail != null && !EarthenDrake.onAllFours(player)) {
            tail.setRotX(tail.getRotX() + (stage == 2 ? 50 : 65) * MathHelper.RADIANS_PER_DEGREE);
        }
        var jaw = model.getCachedGeoBone("jaw");
        if (jaw != null) {
            float opening = player.isUsingItem() && player.getActiveItem().isFood()
                    ? .10f + Math.abs(MathHelper.sin(age * 1.4f)) * .12f : .015f;
            jaw.setRotX(opening);
        }
    }
}
