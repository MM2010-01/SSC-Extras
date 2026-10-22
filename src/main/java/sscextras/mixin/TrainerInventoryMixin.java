package sscextras.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.trainer.QuadrupedalTrainer;

@Mixin(ScreenHandler.class)
public abstract class TrainerInventoryMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void trainerOffhand(int slotId, int button, SlotActionType action, PlayerEntity player, CallbackInfo ci) {
        if (!QuadrupedalTrainer.mouth(player)) return;
        var handler = (ScreenHandler)(Object)this;
        boolean offhand = slotId >= 0 && slotId < handler.slots.size()
                && handler.getSlot(slotId).inventory instanceof PlayerInventory && handler.getSlot(slotId).getIndex() == 40;
        if (offhand || action == SlotActionType.SWAP && button == 40) { handler.syncState(); ci.cancel(); }
    }
}
