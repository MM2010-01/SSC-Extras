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
    @Unique private DrakeRiding.Input sscExtras$riderInput;
    @Unique private RiderChestInventory sscExtras$chest;
    @Unique private boolean sscExtras$syncPassengers;
    public DrakeRiding.Input sscExtras$getRiderInput() { return sscExtras$riderInput; }
    public void sscExtras$setRiderInput(DrakeRiding.Input input) { sscExtras$riderInput = input; }
    public RiderChestInventory sscExtras$getChest() { return sscExtras$chest; }
    public void sscExtras$setChest(RiderChestInventory inventory) { sscExtras$chest = inventory; }
    public boolean sscExtras$tracksDrakePassenger() { return sscExtras$syncPassengers; }

    @Inject(method = {"addPassenger", "removePassenger"}, at = @At("TAIL"))
    private void sscExtras$syncOwnerPassenger(Entity passenger, CallbackInfo ci) {
        if ((Object)this instanceof ServerPlayerEntity owner && (sscExtras$syncPassengers || DrakeEquipment.canRide(owner))) {
            // Vanilla sends passenger changes to observers, excluding the mounted player's own client.
            owner.networkHandler.sendPacket(new EntityPassengersSetS2CPacket(owner));
            sscExtras$syncPassengers = owner.hasPassengers();
        }
    }

    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
    private void sscExtras$drakePassenger(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity)(Object)this;
        if (self instanceof PlayerEntity && DrakeEquipment.canRide(self))
            cir.setReturnValue(passenger instanceof PlayerEntity && !self.hasPassengers() && !self.hasVehicle());
    }

    @Inject(method = "getMountedHeightOffset", at = @At("HEAD"), cancellable = true)
    private void sscExtras$saddleHeight(CallbackInfoReturnable<Double> cir) {
        Entity self = (Entity)(Object)this;
        if (self instanceof PlayerEntity player && EarthenDrake.stage(player) == 3) cir.setReturnValue(1.15);
    }
}
