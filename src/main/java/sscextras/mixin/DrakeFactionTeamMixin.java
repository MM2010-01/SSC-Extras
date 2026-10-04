package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.drake.DrakeFaction;

@Mixin(Entity.class)
public abstract class DrakeFactionTeamMixin {
    @Inject(method = "isTeammate", at = @At("HEAD"), cancellable = true)
    private void sscExtras$drakeAllegiance(Entity other, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity)(Object)this;
        if (self instanceof sscextras.drake.DrakeVisitorEntity && DrakeFaction.member(other)
                || other instanceof sscextras.drake.DrakeVisitorEntity && DrakeFaction.member(self)) cir.setReturnValue(true);
        if (self instanceof sscextras.drake.StableDrakeEntity && DrakeFaction.member(other)
                || other instanceof sscextras.drake.StableDrakeEntity && DrakeFaction.member(self)) cir.setReturnValue(true);
        if (other instanceof PlayerEntity player && DrakeFaction.member(self) && DrakeFaction.friendly(player)
                || self instanceof PlayerEntity wearer && DrakeFaction.member(other) && DrakeFaction.friendly(wearer))
            cir.setReturnValue(true);
    }
}
