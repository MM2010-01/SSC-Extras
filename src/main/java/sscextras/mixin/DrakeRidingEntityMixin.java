package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.*;

@Mixin(Entity.class)
public abstract class DrakeRidingEntityMixin implements DrakeRiding.State {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void sscExtras$feralLook(double x, double y, CallbackInfo ci) {
        if ((Object)this instanceof PlayerEntity player && DrakeFeralization.controlled(player)) ci.cancel();
    }
    @Inject(method = "isLogicalSideForUpdatingMovement", at = @At("HEAD"), cancellable = true)
    private void sscExtras$feralMovementOwner(CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof PlayerEntity player && DrakeFeralization.controlled(player))
            cir.setReturnValue(!player.getWorld().isClient);
    }
    @Unique private DrakeRiding.Input sscExtras$riderInput;
    @Unique private RiderChestInventory sscExtras$chest;
    @Unique private boolean sscExtras$syncPassengers;
    @Unique private net.minecraft.entity.mob.PillagerEntity sscExtras$battleRider;
    @Unique private long sscExtras$nextPatrol;
    public long sscExtras$nextPatrol() { return sscExtras$nextPatrol; }
    public void sscExtras$nextPatrol(long tick) { sscExtras$nextPatrol = tick; }
    public DrakeRiding.Input sscExtras$getRiderInput() { return sscExtras$riderInput; }
    public void sscExtras$setRiderInput(DrakeRiding.Input input) { sscExtras$riderInput = input; }
    public RiderChestInventory sscExtras$getChest() { return sscExtras$chest; }
    public void sscExtras$setChest(RiderChestInventory inventory) { sscExtras$chest = inventory; }
    public boolean sscExtras$tracksDrakePassenger() { return sscExtras$syncPassengers; }
    public net.minecraft.entity.mob.PillagerEntity sscExtras$battleRider() { return sscExtras$battleRider; }
    public void sscExtras$battleRider(net.minecraft.entity.mob.PillagerEntity id) { sscExtras$battleRider = id; }

    @Inject(method = {"addPassenger", "removePassenger"}, at = @At("TAIL"))
    private void sscExtras$syncOwnerPassenger(Entity passenger, CallbackInfo ci) {
        if (passenger instanceof net.minecraft.entity.mob.PillagerEntity && (Object)this instanceof net.minecraft.entity.LivingEntity living
                && !living.hasPassenger(passenger)) living.setSprinting(false);
        if ((Object)this instanceof ServerPlayerEntity owner && (sscExtras$syncPassengers || DrakeEquipment.canRide(owner)
                || DrakeRiding.canCarryMob(owner))) {
            if (passenger instanceof net.minecraft.entity.mob.PillagerEntity && owner.hasPassenger(passenger))
                DrakeLeashing.detach(owner, false);
            // Vanilla sends passenger changes to observers, excluding the mounted player's own client.
            owner.networkHandler.sendPacket(new EntityPassengersSetS2CPacket(owner));
            sscExtras$syncPassengers = owner.hasPassengers();
            sscExtras$riderInput = null;
            if (EarthenDrake.stage(owner) == 2)
                io.github.apace100.apoli.component.PowerHolderComponent.getPowers(owner, DrakeBodyPower.class)
                        .forEach(DrakeBodyPower::refreshSize);
        }
    }

    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
    private void sscExtras$drakePassenger(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity)(Object)this;
        if (self instanceof PlayerEntity player && (DrakeEquipment.canRide(self) || DrakeRiding.canCarryMob(player)))
            cir.setReturnValue(DrakeRiding.accepts(player, passenger) && !self.hasPassengers() && !self.hasVehicle());
    }

    @Inject(method = "getMountedHeightOffset", at = @At("HEAD"), cancellable = true)
    private void sscExtras$saddleHeight(CallbackInfoReturnable<Double> cir) {
        Entity self = (Entity)(Object)this;
        if (self instanceof PlayerEntity player && EarthenDrake.stage(player) == 3) cir.setReturnValue(1.15);
        else if (self instanceof PlayerEntity player && EarthenDrake.stage(player) == 2 && self.hasPassengers()) cir.setReturnValue(.95);
    }
}
