package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import sscextras.guardian.RuneboundGuardian;

@Mixin(Entity.class)
public abstract class GuardianCoreLoadMixin {
    @WrapMethod(method = "readNbt")
    private void sscExtras$restoreCore(NbtCompound nbt, Operation<Void> original) {
        if (!((Object)this instanceof ServerPlayerEntity player)) {
            original.call(nbt);
            return;
        }
        var state = RuneboundGuardian.state(player);
        var previous = state.loadingCoreId;
        var saved = nbt.getCompound("cardinal_components").getCompound("ssc-extras:guardian");
        // Curios validates saved equipment before CCA restores the form and Guardian state.
        state.loadingCoreId = saved.containsUuid("Core") ? saved.getUuid("Core") : null;
        try {
            original.call(nbt);
        } finally {
            state.loadingCoreId = previous;
        }
    }
}
