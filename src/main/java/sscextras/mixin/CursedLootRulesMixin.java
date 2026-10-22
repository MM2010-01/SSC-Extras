package sscextras.mixin;

import com.mojang.serialization.DynamicLike;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.SscExtrasGameRules;

/** Preserve old world choices; renamed keys take precedence when both exist. */
@Mixin(GameRules.class)
public abstract class CursedLootRulesMixin {
    @Inject(method = "load", at = @At("TAIL"))
    private void migrateCursedLoot(DynamicLike<?> data, CallbackInfo ci) {
        var rules = (GameRules)(Object)this;
        for (boolean cursed : new boolean[]{true, false}) {
            String suffix = cursed ? "ForCursedPlayers" : "ForUncursedPlayers";
            if (data.get("CursedLootAutoEquip" + suffix).asString().result().isPresent()) continue;
            data.get("CursedCollarAutoEquip" + suffix).asString().result().ifPresent(value ->
                    rules.get(cursed ? SscExtrasGameRules.CURSED_LOOT_AUTO_EQUIP_CURSED
                            : SscExtrasGameRules.CURSED_LOOT_AUTO_EQUIP_UNCURSED).set(Boolean.parseBoolean(value), null));
        }
    }
}
