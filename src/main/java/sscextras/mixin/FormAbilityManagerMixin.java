package sscextras.mixin;

import sscextras.CreatureInstinct;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.RegPlayerInstinctComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FormAbilityManager.class, remap = false)
public abstract class FormAbilityManagerMixin {
    @Inject(method = "applyForm", at = @At("HEAD"))
    private static void sscExtras$onFormChange(PlayerEntity player, PlayerFormBase newForm, CallbackInfo ci) {
        sscextras.drake.DrakeSoulbinding.formChanged(player, newForm);
        if (!player.getWorld().isClient && !newForm.equals(FormAbilityManager.getForm(player))) {
            CreatureInstinct.clearTarget(player);
            RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.sync(player);
        }
    }

    @Inject(method = "applyForm", at = @At("TAIL"))
    private static void sscExtras$drakeSize(PlayerEntity player, PlayerFormBase newForm, CallbackInfo ci) {
        if (player.getWorld().isClient) return;
        if (newForm.getGroup() == sscextras.drake.EarthenDrake.GROUP)
            io.github.apace100.apoli.component.PowerHolderComponent.getPowers(player, sscextras.drake.DrakeBodyPower.class)
                    .forEach(sscextras.drake.DrakeBodyPower::refreshSize);
        sscextras.drake.DrakeFeralization.formChanged(player);
    }
}
