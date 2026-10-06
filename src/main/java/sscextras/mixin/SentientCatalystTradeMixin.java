package sscextras.mixin;

import net.minecraft.entity.passive.WanderingTraderEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.SentientCatalyst;

@Mixin(WanderingTraderEntity.class)
public abstract class SentientCatalystTradeMixin {
    @Inject(method = "fillRecipes", at = @At("TAIL"))
    private void sscExtras$rareCatalyst(CallbackInfo ci) {
        var trader = (WanderingTraderEntity)(Object)this;
        if (trader.getRandom().nextFloat() < SentientCatalyst.TRADE_CHANCE)
            trader.getOffers().add(SentientCatalyst.trade(trader.getRandom()));
    }
}
