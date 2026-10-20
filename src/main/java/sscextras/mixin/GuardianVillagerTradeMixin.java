package sscextras.mixin;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.guardian.GuardianVillagerProtection;

@Mixin(VillagerEntity.class)
public abstract class GuardianVillagerTradeMixin implements GuardianVillagerProtection.Access {
    @Unique private final GuardianVillagerProtection sscExtras$protection = new GuardianVillagerProtection();

    @Override public GuardianVillagerProtection sscExtras$guardianProtection() { return sscExtras$protection; }

    @Inject(method = "prepareOffersFor", at = @At("TAIL"))
    private void sscExtras$protectionDiscount(PlayerEntity player, CallbackInfo ci) {
        sscExtras$protection.prepareOffers((VillagerEntity)(Object)this, player);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void sscExtras$readProtection(NbtCompound nbt, CallbackInfo ci) { sscExtras$protection.read(nbt); }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void sscExtras$writeProtection(NbtCompound nbt, CallbackInfo ci) { sscExtras$protection.write(nbt); }
}
