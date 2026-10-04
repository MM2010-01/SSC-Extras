package sscextras.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeAttention;
import sscextras.drake.DrakeVisitorEntity;

@Mixin(VillagerResemblingModel.class)
public abstract class DrakePettingVillagerModelMixin {
    @Shadow @Final private ModelPart root;

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void sscExtras$stroke(Entity entity, float limbAngle, float limbDistance, float time, float headYaw, float headPitch, CallbackInfo ci) {
        root.getChild("arms").pitch = entity instanceof DrakeVisitorEntity && ((DrakeAttention.State)entity).sscExtras$petting()
                ? -1.05f + MathHelper.sin(time * .3f) * .12f : -.75f;
    }
}
