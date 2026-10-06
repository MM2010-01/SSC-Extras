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
    @Unique private boolean sscExtras$feralDataReady;
    @Unique private final DrakeFeralization.Control sscExtras$feralRuntime = new DrakeFeralization.Control();
    public DrakeFeralization.Control sscExtras$feralRuntime() { return sscExtras$feralRuntime; }
    @Unique private static final TrackedData<Boolean> SSC_EXTRAS_FERAL = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    @Unique private static final TrackedData<Boolean> SSC_EXTRAS_FERAL_CONTROL = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    @Unique private static final TrackedData<Integer> SSC_EXTRAS_SENTIENCE = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void sscExtras$feralData(CallbackInfo ci) {
        var tracker = ((PlayerEntity)(Object)this).getDataTracker();
        tracker.startTracking(SSC_EXTRAS_FERAL, false); tracker.startTracking(SSC_EXTRAS_FERAL_CONTROL, false);
        tracker.startTracking(SSC_EXTRAS_SENTIENCE, 0);
        sscExtras$feralDataReady = true;
    }
    public boolean sscExtras$feral() { return sscExtras$feralDataReady && ((PlayerEntity)(Object)this).getDataTracker().get(SSC_EXTRAS_FERAL); }
    public void sscExtras$feral(boolean value) { ((PlayerEntity)(Object)this).getDataTracker().set(SSC_EXTRAS_FERAL, value); }
    public void sscExtras$refreshFeralDimensions(TrackedData<?> data) {
        if (data == SSC_EXTRAS_FERAL) ((PlayerEntity)(Object)this).calculateDimensions();
    }
    public int sscExtras$sentience() { return ((PlayerEntity)(Object)this).getDataTracker().get(SSC_EXTRAS_SENTIENCE); }
    public void sscExtras$sentience(int value) { ((PlayerEntity)(Object)this).getDataTracker().set(SSC_EXTRAS_SENTIENCE, net.minecraft.util.math.MathHelper.clamp(value, 0, 3)); }
    public boolean sscExtras$feralControl() { return ((PlayerEntity)(Object)this).getDataTracker().get(SSC_EXTRAS_FERAL_CONTROL); }
    public void sscExtras$feralControl(boolean value) { ((PlayerEntity)(Object)this).getDataTracker().set(SSC_EXTRAS_FERAL_CONTROL, value); }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void sscExtras$saveMind(net.minecraft.nbt.NbtCompound nbt, CallbackInfo ci) {
        nbt.putBoolean("SscExtrasFeralMind", DrakeFeralization.permanent((PlayerEntity)(Object)this));
        nbt.putInt("SscExtrasSentience", sscExtras$sentience());
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void sscExtras$loadMind(net.minecraft.nbt.NbtCompound nbt, CallbackInfo ci) {
        sscExtras$feral(nbt.getBoolean("SscExtrasFeralMind"));
        sscExtras$sentience(sscExtras$feral() ? nbt.getInt("SscExtrasSentience") : 0);
    }
}
