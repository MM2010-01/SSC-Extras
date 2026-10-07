package sscextras.mixin;

import mod.azure.azurelib.rewrite.model.AzBone;
import mod.azure.azurelib.rewrite.render.armor.AzArmorRendererPipeline;
import mod.azure.azurelib.rewrite.render.armor.AzArmorRendererPipelineContext;
import mod.azure.azurelib.rewrite.render.armor.bone.AzArmorBoneContext;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.items.armors.MorphScaleArmor;
import net.onixary.shapeShifterCurseFabric.items.armors.NetheriteMorphScaleArmor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.EarthenDrake;

@Mixin(value = AzArmorRendererPipeline.class, remap = false)
public abstract class DrakeMorphscaleArmorMixin {
    @Inject(method = "scaleBoneWithModelPart", at = @At("TAIL"))
    private void sscExtras$fitDrakeArmor(AzArmorRendererPipelineContext context, AzArmorBoneContext bones,
            boolean reRender, CallbackInfo ci) {
        if (reRender || !(context.currentStack().getItem() instanceof MorphScaleArmor
                || context.currentStack().getItem() instanceof NetheriteMorphScaleArmor)) return;
        // AzureLib scales leggings but omits the separate ankle bones. Restore these
        // on every wearer too: the renderer is shared with other forms and players.
        var model = context.baseModel();
        scale(bones.leftBoot, model.leftLeg.xScale, model.leftLeg.yScale, model.leftLeg.zScale);
        scale(bones.rightBoot, model.rightLeg.xScale, model.rightLeg.yScale, model.rightLeg.zScale);
        if (!(context.currentEntity() instanceof PlayerEntity player) || EarthenDrake.stage(player) != 3) return;
        switch (context.currentSlot()) {
            case HEAD -> scale(bones.head, .64f, .38f, .68f);
            case CHEST -> {
                scale(bones.leftArm, 1, .85f, 1);
                scale(bones.rightArm, 1, .85f, 1);
            }
            case LEGS -> {
                scale(bones.leftLeg, .78f, .65f, .9f);
                scale(bones.rightLeg, .78f, .65f, .9f);
            }
            case FEET -> {
                scale(bones.leftBoot, .78f, .65f, .9f);
                scale(bones.rightBoot, .78f, .65f, .9f);
            }
            default -> { }
        }
    }

    private static void scale(AzBone bone, float x, float y, float z) {
        if (bone == null) return;
        bone.setScaleX(x); bone.setScaleY(y); bone.setScaleZ(z);
    }
}
