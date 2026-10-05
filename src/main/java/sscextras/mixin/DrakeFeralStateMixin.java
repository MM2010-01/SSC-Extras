package sscextras.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeFeralization;

@Mixin(PlayerEntity.class)
public abstract class DrakeFeralStateMixin implements DrakeFeralization.State {
    @Unique private final DrakeFeralization.Control sscExtras$feralRuntime = new DrakeFeralization.Control();
    public DrakeFeralization.Control sscExtras$feralRuntime() { return sscExtras$feralRuntime; }
    @Unique private static final TrackedData<Boolean> SSC_EXTRAS_FERAL = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    @Unique private static final TrackedData<Boolean> SSC_EXTRAS_FERAL_CONTROL = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void sscExtras$feralData(CallbackInfo ci) {
        var tracker = ((PlayerEntity)(Object)this).getDataTracker();
        tracker.startTracking(SSC_EXTRAS_FERAL, false); tracker.startTracking(SSC_EXTRAS_FERAL_CONTROL, false);
    }
    public boolean sscExtras$feral() { return ((PlayerEntity)(Object)this).getDataTracker().get(SSC_EXTRAS_FERAL); }
    public void sscExtras$feral(boolean value) { ((PlayerEntity)(Object)this).getDataTracker().set(SSC_EXTRAS_FERAL, value); }
    public boolean sscExtras$feralControl() { return ((PlayerEntity)(Object)this).getDataTracker().get(SSC_EXTRAS_FERAL_CONTROL); }
    public void sscExtras$feralControl(boolean value) { ((PlayerEntity)(Object)this).getDataTracker().set(SSC_EXTRAS_FERAL_CONTROL, value); }
}
