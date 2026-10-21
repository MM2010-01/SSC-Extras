package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.player.*;

@Mixin(ItemStack.class)
public abstract class MentalItemTickMixin {
    @Inject(method = "inventoryTick", at = @At("HEAD"), cancellable = true)
    private void sscExtras$activeEquipmentInt(World world, Entity entity, int slot, boolean selected, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity player) || PlayerMind.permits(player, IntLevel.REGULAR)) return;
        var stack = (ItemStack)(Object)this;
        boolean active = selected;
        if (!active) for (var equipment : EquipmentSlot.values()) if (player.getEquippedStack(equipment) == stack) { active = true; break; }
        if (active && !PlayerMind.check(player, MentalContext.item(MentalContext.Action.USE, stack)).allowed()) ci.cancel();
    }
}
