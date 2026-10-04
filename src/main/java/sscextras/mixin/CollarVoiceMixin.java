package sscextras.mixin;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import sscextras.collar.Collars;

@Mixin(World.class)
public abstract class CollarVoiceMixin {
    @ModifyVariable(method = "playSound(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float sscExtras$collarVolume(float volume) { return Collars.voiceVolume(volume); }
}
