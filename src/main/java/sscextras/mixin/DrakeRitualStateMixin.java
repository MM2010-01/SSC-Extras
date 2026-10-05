package sscextras.mixin;

import net.minecraft.entity.LivingEntity;
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

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void sscExtras$ritualData(CallbackInfo ci) { ((LivingEntity)(Object)this).getDataTracker().startTracking(SSC_EXTRAS_RITUAL_ROLE, 0); }
    public int sscExtras$ritualRole() { return ((LivingEntity)(Object)this).getDataTracker().get(SSC_EXTRAS_RITUAL_ROLE); }
    public void sscExtras$ritualRole(int role) { ((LivingEntity)(Object)this).getDataTracker().set(SSC_EXTRAS_RITUAL_ROLE, role); }

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void sscExtras$holdOnHay(CallbackInfo ci) { if (sscExtras$ritualRole() == DrakeSoulbinding.RESTRAINED) ci.cancel(); }

    @Inject(method = "tick", at = @At("TAIL"))
    private void sscExtras$heldRitual(CallbackInfo ci) {
        if (sscExtras$ritualRole() == DrakeSoulbinding.RESTRAINED && (Object)this instanceof ServerPlayerEntity player)
            DrakeSoulbinding.hold(player);
    }
}
