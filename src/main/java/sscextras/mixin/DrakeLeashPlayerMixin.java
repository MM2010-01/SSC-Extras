package sscextras.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeLeashing;

@Mixin(PlayerEntity.class)
public abstract class DrakeLeashPlayerMixin extends LivingEntity implements DrakeLeashing.State {
    protected DrakeLeashPlayerMixin(EntityType<? extends LivingEntity> type, World world) { super(type, world); }

    @Override public float getStepHeight() {
        float height = super.getStepHeight();
        if (sscextras.drake.EarthenDrake.onAllFours((PlayerEntity)(Object)this)) return 1;
        var holder = DrakeLeashing.holder((PlayerEntity)(Object)this);
        double distance = holder instanceof PillagerEntity ? squaredDistanceTo(holder) : 0;
        return distance > 9 && distance <= 100 ? Math.max(1, height) : height;
    }

    @Unique private static final TrackedData<Integer> SSC_EXTRAS_LEASH = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    @Unique private final DrakeLeashing.Leash sscExtras$leash = new DrakeLeashing.Leash();
    public DrakeLeashing.Leash sscExtras$leash() { return sscExtras$leash; }
    public int sscExtras$leashHolderId() { return ((PlayerEntity)(Object)this).getDataTracker().get(SSC_EXTRAS_LEASH); }
    public void sscExtras$leashHolderId(int id) { ((PlayerEntity)(Object)this).getDataTracker().set(SSC_EXTRAS_LEASH, id); }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void sscExtras$initLeash(CallbackInfo ci) { ((PlayerEntity)(Object)this).getDataTracker().startTracking(SSC_EXTRAS_LEASH, 0); }
    @Inject(method = "tick", at = @At("TAIL"))
    private void sscExtras$tickLeash(CallbackInfo ci) { sscExtras$leash.tick((PlayerEntity)(Object)this); }
    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void sscExtras$saveLeash(NbtCompound nbt, CallbackInfo ci) { sscExtras$leash.write(nbt); }
    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void sscExtras$loadLeash(NbtCompound nbt, CallbackInfo ci) { sscExtras$leash.read(nbt); }
}
