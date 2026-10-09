package sscextras.mixin;
import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(VillagerEntity.class)
public abstract class GuardianWorkerMixin {
    @Inject(method="mobTick",at=@At("HEAD"),cancellable=true)
    private void guardianRitualWork(CallbackInfo ci) {
        if(((VillagerEntity)(Object)this).getCommandTags().contains("ssc_extras_guardian_worker"))ci.cancel();
    }
}
