package sscextras.mixin;

import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.drake.DrakeOutpostOwnership;

@Mixin(PlayerEntity.class)
public abstract class DrakeMountNameMixin implements DrakeOutpostOwnership.Display {
    @Unique private static final TrackedData<String> SSC_EXTRAS_MOUNT_NAME = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.STRING);
    public String sscExtras$mountName() { return ((PlayerEntity)(Object)this).getDataTracker().get(SSC_EXTRAS_MOUNT_NAME); }
    public void sscExtras$mountName(String name) { ((PlayerEntity)(Object)this).getDataTracker().set(SSC_EXTRAS_MOUNT_NAME, name); }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void sscExtras$initName(CallbackInfo ci) { ((PlayerEntity)(Object)this).getDataTracker().startTracking(SSC_EXTRAS_MOUNT_NAME, ""); }

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void sscExtras$mountName(CallbackInfoReturnable<Text> cir) {
        var name = sscExtras$mountName();
        if (!name.isEmpty()) cir.setReturnValue(Text.literal(name));
    }
}
