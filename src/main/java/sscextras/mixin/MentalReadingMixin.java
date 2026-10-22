package sscextras.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.player.MentalProgression;

@Mixin(ItemStack.class)
public abstract class MentalReadingMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void sscExtras$readingLevel(World world, PlayerEntity player, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        var book = (ItemStack)(Object)this;
        if (!MentalProgression.isBook(book)) return;
        if (!MentalProgression.allowBook(player, book)) { cir.setReturnValue(TypedActionResult.fail(book)); return; }
        // These native book items have no reader screen; studying them still uses the same daily ledger.
        if (book.isOf(Items.BOOK) || book.isOf(Items.ENCHANTED_BOOK)) {
            if (player instanceof ServerPlayerEntity serverPlayer) MentalProgression.readBook(serverPlayer, book);
            cir.setReturnValue(TypedActionResult.success(book, world.isClient));
        }
    }
    @Inject(method = "use", at = @At("RETURN"))
    private void sscExtras$readBook(World world, PlayerEntity player, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        if (player instanceof ServerPlayerEntity serverPlayer && cir.getReturnValue().getResult().isAccepted())
            MentalProgression.readBook(serverPlayer, (ItemStack)(Object)this);
    }
}
