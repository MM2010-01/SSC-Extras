package sscextras.mixin;

import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.client.MentalPerception;

@Mixin(FilledMapItem.class)
public abstract class MentalMapMixin {
    @Inject(method = "getMapState(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;)Lnet/minecraft/item/map/MapState;", at = @At("HEAD"), cancellable = true)
    private static void sscExtras$unreadableMap(ItemStack stack, World world, CallbackInfoReturnable<MapState> cir) {
        if (world.isClient && MentalPerception.unreadable(stack)) cir.setReturnValue(null);
    }
}
