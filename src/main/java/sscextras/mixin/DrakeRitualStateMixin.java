package sscextras.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeSoulbinding;

@Mixin(LivingEntity.class)
public abstract class DrakeRitualStateMixin implements DrakeSoulbinding.State {
    @Unique private static final TrackedData<Integer> SSC_EXTRAS_RITUAL_ROLE = DataTracker.registerData(LivingEntity.class, TrackedDataHandlerRegistry.INTEGER);
    @Unique private static final TrackedData<Integer> SSC_EXTRAS_SOUL_TICKS = DataTracker.registerData(LivingEntity.class, TrackedDataHandlerRegistry.INTEGER);
    @Unique private static final TrackedData<Boolean> SSC_EXTRAS_DRAKE_SOUL = DataTracker.registerData(LivingEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void sscExtras$ritualData(CallbackInfo ci) {
        var tracker = ((LivingEntity)(Object)this).getDataTracker();
        tracker.startTracking(SSC_EXTRAS_RITUAL_ROLE, 0);
        tracker.startTracking(SSC_EXTRAS_SOUL_TICKS, 0);
        tracker.startTracking(SSC_EXTRAS_DRAKE_SOUL, false);
    }
    public int sscExtras$soulTicks() { return ((LivingEntity)(Object)this).getDataTracker().get(SSC_EXTRAS_SOUL_TICKS); }
    public void sscExtras$soulTicks(int ticks) { ((LivingEntity)(Object)this).getDataTracker().set(SSC_EXTRAS_SOUL_TICKS, ticks); }
    public boolean sscExtras$drakeSoul() { return ((LivingEntity)(Object)this).getDataTracker().get(SSC_EXTRAS_DRAKE_SOUL); }
    public void sscExtras$drakeSoul(boolean bound) { ((LivingEntity)(Object)this).getDataTracker().set(SSC_EXTRAS_DRAKE_SOUL, bound); }
    public int sscExtras$ritualRole() { return ((LivingEntity)(Object)this).getDataTracker().get(SSC_EXTRAS_RITUAL_ROLE) & 255; }
    public void sscExtras$ritualRole(int role) {
        var tracker = ((LivingEntity)(Object)this).getDataTracker();
        int paw = role == DrakeSoulbinding.SHOE_RESTRAINED || role == DrakeSoulbinding.SHOEING
                ? tracker.get(SSC_EXTRAS_RITUAL_ROLE) & ~255 : 0;
        tracker.set(SSC_EXTRAS_RITUAL_ROLE, paw | role);
    }
    public int sscExtras$shoeingPaw() { return (((LivingEntity)(Object)this).getDataTracker().get(SSC_EXTRAS_RITUAL_ROLE) >>> 8) - 1; }
    public void sscExtras$shoeingPaw(int paw) {
        ((LivingEntity)(Object)this).getDataTracker().set(SSC_EXTRAS_RITUAL_ROLE, ((paw + 1) << 8) | sscExtras$ritualRole());
    }

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void sscExtras$holdOnHay(CallbackInfo ci) {
        if (sscExtras$ritualRole() == DrakeSoulbinding.RESTRAINED || sscExtras$ritualRole() == DrakeSoulbinding.SHOE_RESTRAINED) ci.cancel();
    }

    @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
    private void sscExtras$keepShoeingGrip(Entity other, CallbackInfo ci) {
        if (other instanceof LivingEntity living && DrakeSoulbinding.shoeingTogether((LivingEntity)(Object)this, living)) ci.cancel();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void sscExtras$heldRitual(CallbackInfo ci) {
        if ((Object)this instanceof ServerPlayerEntity player && DrakeSoulbinding.restrained(player))
            DrakeSoulbinding.hold(player);
    }
}
