package sscextras.mixin;

import sscextras.InstinctTarget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.PlayerInstinctComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerInstinctComponent.class, remap = false)
public abstract class InstinctComponentMixin implements InstinctTarget {
    @Unique private Identifier sscExtras$target;

    @Override
    public Identifier sscExtras$getTarget() {
        return sscExtras$target;
    }

    @Override
    public void sscExtras$setTarget(Identifier target) {
        sscExtras$target = target;
    }

    @Inject(method = "readFromNbt", at = @At("TAIL"))
    private void sscExtras$read(NbtCompound nbt, CallbackInfo ci) {
        sscExtras$target = nbt.contains("ssc_extras:target")
                ? Identifier.tryParse(nbt.getString("ssc_extras:target")) : null;
    }

    @Inject(method = "writeToNbt", at = @At("TAIL"))
    private void sscExtras$write(NbtCompound nbt, CallbackInfo ci) {
        if (sscExtras$target != null) {
            nbt.putString("ssc_extras:target", sscExtras$target.toString());
        } else {
            nbt.remove("ssc_extras:target");
        }
    }

    @Inject(method = "clear", at = @At("TAIL"))
    private void sscExtras$clear(CallbackInfo ci) {
        sscExtras$target = null;
    }
}
