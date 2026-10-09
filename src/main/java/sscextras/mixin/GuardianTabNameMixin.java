package sscextras.mixin;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.guardian.RuneboundGuardian;
@Mixin(ServerPlayerEntity.class)
public abstract class GuardianTabNameMixin {
    @Inject(method="getPlayerListName",at=@At("RETURN"),cancellable=true)
    private void guardianName(CallbackInfoReturnable<Text> cir) {
        var player=(ServerPlayerEntity)(Object)this;
        if(RuneboundGuardian.stage(player)==3&&!RuneboundGuardian.state(player).guardianName.isEmpty()) cir.setReturnValue(player.getDisplayName());
    }
}
