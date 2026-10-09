package sscextras.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.guardian.RuneboundGuardian;

@Mixin(PlayerEntity.class)
public abstract class GuardianNameMixin {
    @Inject(method = {"getName", "getDisplayName"}, at = @At("RETURN"), cancellable = true)
    private void sscExtras$guardianDesignation(CallbackInfoReturnable<Text> cir) {
        var player = (PlayerEntity)(Object)this;
        if (RuneboundGuardian.stage(player) != 3) return;
        String name = RuneboundGuardian.state(player).guardianName;
        if (!name.isEmpty()) cir.setReturnValue(Text.literal(name).setStyle(cir.getReturnValue().getStyle()));
    }
}
