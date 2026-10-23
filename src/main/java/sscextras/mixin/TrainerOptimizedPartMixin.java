package sscextras.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import sscextras.client.TrainerLimbModel;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

/** Oculus 1.8 / Embeddium 0.3 cached traversal bypasses ModelPart.renderCuboids. */
@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.render.immediate.model.EntityRenderer", remap=false)
public abstract class TrainerOptimizedPartMixin {
    @ModifyExpressionValue(method="render", at=@At(value="INVOKE",
            target="Lme/jellysquid/mods/sodium/client/render/immediate/model/ModelPartData;isHidden()Z"), require=0)
    private static boolean trainerOriginalCuboids(boolean hidden, MatrixStack matrices, @Coerce Object writer,
            ModelPart part, int light, int overlay, int color) {
        // SSC resets skip-draw between its skin passes; FirstPerson also owns an
        // isHidden implementation. Anatomical ownership survives both boundaries.
        return hidden || ((TrainerLimbModel.Part)(Object)part).sscExtras$reshaped();
    }
    @ModifyVariable(method="render", at=@At("HEAD"), argsOnly=true, ordinal=2, require=0)
    private static int trainerPadColor(int color, MatrixStack matrices, @Coerce Object writer,
            ModelPart part, int light, int overlay, int originalColor) {
        if (!((TrainerLimbModel.Part)(Object)part).sscExtras$pad()) return color;
        return color & 0xff000000 | (int)((color & 255)*.72f)
                | (int)(((color >>> 8)&255)*.72f)<<8 | (int)(((color >>> 16)&255)*.72f)<<16;
    }
}
