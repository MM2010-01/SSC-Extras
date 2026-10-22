package sscextras.mixin;

import net.minecraft.block.LecternBlock;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.LecternScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.player.MentalProgression;

@Mixin(LecternBlock.class)
public abstract class MentalLecternMixin {
    @Inject(method = "openScreen", at = @At("HEAD"), cancellable = true)
    private void sscExtras$readingLevel(World world, BlockPos pos, PlayerEntity player, CallbackInfo ci) {
        if (world.getBlockEntity(pos) instanceof LecternBlockEntity lectern
                && !MentalProgression.allowBook(player, lectern.getBook())) ci.cancel();
    }
    @Inject(method = "openScreen", at = @At("TAIL"))
    private void sscExtras$readBook(World world, BlockPos pos, PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer && player.currentScreenHandler instanceof LecternScreenHandler
                && world.getBlockEntity(pos) instanceof LecternBlockEntity lectern) {
            if (MentalProgression.readBook(serverPlayer, lectern.getBook()) > 0) lectern.markDirty();
        }
    }
}
