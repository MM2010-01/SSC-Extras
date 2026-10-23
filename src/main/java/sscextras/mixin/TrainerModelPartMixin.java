package sscextras.mixin;

import net.minecraft.client.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class TrainerModelPartMixin implements sscextras.client.TrainerLimbModel.Part {
    @Unique private boolean sscExtras$reshaped;
    @Unique private boolean sscExtras$pad;
    @Shadow public boolean hidden;
    @Unique private boolean sscExtras$previousHidden;
    public boolean sscExtras$reshaped() { return sscExtras$reshaped; }
    public void sscExtras$reshaped(boolean active) {
        // Native skip-draw hides only this root's cuboids, retaining its children.
        // Oculus/Embeddium's cached traversal also respects this flag.
        if (active && !sscExtras$reshaped) sscExtras$previousHidden = hidden;
        if (active) hidden = true;
        else if (sscExtras$reshaped) hidden = sscExtras$previousHidden;
        sscExtras$reshaped = active;
    }
    public void sscExtras$pad(boolean active) { sscExtras$pad = active; }
    public boolean sscExtras$pad() { return sscExtras$pad; }
    @ModifyArgs(method="renderCuboids",at=@At(value="INVOKE",target="Lnet/minecraft/client/model/ModelPart$Cuboid;renderCuboid(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"))
    private void trainerPadTint(org.spongepowered.asm.mixin.injection.invoke.arg.Args args) {
        if(sscExtras$pad)for(int i=4;i<=6;i++)args.set(i,(float)args.get(i)*.72f);
    }
    @Inject(method = "renderCuboids", at = @At("HEAD"), cancellable = true)
    private void trainerArticulatedGeometry(CallbackInfo ci) { if (sscExtras$reshaped) ci.cancel(); }
}
