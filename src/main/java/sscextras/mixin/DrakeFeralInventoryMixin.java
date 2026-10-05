package sscextras.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeFeralization;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class DrakeFeralInventoryMixin {
    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    private void sscExtras$foodInventory(int syncId, int slot, int button, SlotActionType action, PlayerEntity player, CallbackInfo ci) {
        if (!DrakeFeralization.allowInventoryClick(player, slot, button, action)) ci.cancel();
    }
}
