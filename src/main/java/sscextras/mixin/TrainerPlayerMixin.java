package sscextras.mixin;

import net.minecraft.entity.data.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.trainer.*;

@Mixin(PlayerEntity.class)
public abstract class TrainerPlayerMixin implements TrainerState.Owner {
    @Unique private final TrainerState sscExtras$trainer = new TrainerState();
    @Unique private boolean sscExtras$trainerReady;
    @Unique private static final TrackedData<Integer> TRAINER_HANDS = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    @Unique private static final TrackedData<Integer> TRAINER_FEET = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    @Unique private static final TrackedData<Integer> TRAINER_QUAD = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    @Unique private static final TrackedData<Integer> TRAINER_EQUIPMENT = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public TrainerState sscExtras$trainer() { return sscExtras$trainer; }
    public int sscExtras$trainerHands() { return sscExtras$trainerReady ? ((PlayerEntity)(Object)this).getDataTracker().get(TRAINER_HANDS) : 0; }
    public int sscExtras$trainerFeet() { return sscExtras$trainerReady ? ((PlayerEntity)(Object)this).getDataTracker().get(TRAINER_FEET) : 0; }
    public int sscExtras$trainerEquipment() { return sscExtras$trainerReady ? ((PlayerEntity)(Object)this).getDataTracker().get(TRAINER_EQUIPMENT) : 0; }
    public int sscExtras$trainerQuad() { return sscExtras$trainerReady ? ((PlayerEntity)(Object)this).getDataTracker().get(TRAINER_QUAD) : 0; }
    public void sscExtras$trainerSnapshot(int hands, int feet, int quad, int equipment) {
        var tracker = ((PlayerEntity)(Object)this).getDataTracker();
        tracker.set(TRAINER_HANDS, hands); tracker.set(TRAINER_FEET, feet); tracker.set(TRAINER_EQUIPMENT, equipment);
        tracker.set(TRAINER_QUAD, quad);
    }
    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void trainerInit(CallbackInfo ci) {
        var tracker = ((PlayerEntity)(Object)this).getDataTracker();
        tracker.startTracking(TRAINER_HANDS, 0); tracker.startTracking(TRAINER_FEET, 0); tracker.startTracking(TRAINER_EQUIPMENT, 0);
        tracker.startTracking(TRAINER_QUAD, 0);
        sscExtras$trainerReady = true;
    }
    @Inject(method = "tick", at = @At("TAIL"))
    private void trainerTick(CallbackInfo ci) {
        if ((Object)this instanceof ServerPlayerEntity player) QuadrupedalTrainer.tick(player);
    }
    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void trainerSave(NbtCompound nbt, CallbackInfo ci) { sscExtras$trainer.write(nbt); }
    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void trainerLoad(NbtCompound nbt, CallbackInfo ci) {
        sscExtras$trainer.read(nbt); sscExtras$trainer.sync((PlayerEntity)(Object)this, 0);
    }
}
