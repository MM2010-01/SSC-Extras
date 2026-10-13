package sscextras.mixin;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.guardian.*;
@Mixin(ClientPlayerEntity.class)
public abstract class GuardianInputMixin {
    @Unique private boolean sscExtras$statueSneaking;
    @Inject(method="tickMovement",at=@At(value="INVOKE",target="Lnet/minecraft/client/input/Input;tick(ZF)V",shift=At.Shift.AFTER))
    private void guardianInput(CallbackInfo ci) {
        var player=(ClientPlayerEntity)(Object)this;
        boolean sneaking = player.input.sneaking;
        if (sneaking != sscExtras$statueSneaking
                && (sneaking || MinecraftClient.getInstance().options.getSneakToggled().getValue())
                && RuneboundGuardian.stage(player) == 3)
            ClientPlayNetworking.send(RuneboundGuardian.STATUE_TOGGLE, PacketByteBufs.create());
        sscExtras$statueSneaking = sneaking;
        if(!GuardianCombat.locked(player)&&!RuneboundGuardian.state(player).protecting) return;
        var input=player.input; input.movementForward=input.movementSideways=0;
        input.jumping=input.sneaking=input.pressingForward=input.pressingBack=input.pressingLeft=input.pressingRight=false;
    }
}
