package sscextras.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.client.FeralPerception;
import sscextras.drake.FeralText;
import java.util.List;
import java.util.Optional;

@Mixin(value = ItemStack.class, priority = 500)
public abstract class DrakeFeralItemTextMixin {
    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void sscExtras$unknownItem(CallbackInfoReturnable<Text> cir) {
        if (FeralPerception.active()) cir.setReturnValue(FeralText.itemName((ItemStack)(Object)this, cir.getReturnValue()));
    }
    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void sscExtras$noItemDetails(CallbackInfoReturnable<List<Text>> cir) {
        if (FeralPerception.active()) cir.setReturnValue(new java.util.ArrayList<>(List.of(((ItemStack)(Object)this).getName())));
    }
    @Inject(method = "getTooltipData", at = @At("RETURN"), cancellable = true)
    private void sscExtras$noBundlePreview(CallbackInfoReturnable<Optional<net.minecraft.client.item.TooltipData>> cir) {
        if (FeralPerception.active()) cir.setReturnValue(Optional.empty());
    }
}
