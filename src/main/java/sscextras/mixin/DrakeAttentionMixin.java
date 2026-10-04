package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeAttention;

@Mixin(LivingEntity.class)
public abstract class DrakeAttentionMixin extends Entity implements DrakeAttention.State {
    @Unique private static final TrackedData<Long> SSC_EXTRAS_PET_UNTIL = DataTracker.registerData(LivingEntity.class, TrackedDataHandlerRegistry.LONG);
    @Unique private static final TrackedData<Boolean> SSC_EXTRAS_PETTING = DataTracker.registerData(LivingEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    @Unique private MobEntity sscExtras$petter;
    @Unique private long sscExtras$nextPet, sscExtras$calledUntil;

    protected DrakeAttentionMixin(EntityType<?> type, World world) { super(type, world); }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void sscExtras$petData(CallbackInfo ci) {
        dataTracker.startTracking(SSC_EXTRAS_PET_UNTIL, 0L);
        dataTracker.startTracking(SSC_EXTRAS_PETTING, false);
    }

    public long sscExtras$petUntil() { return dataTracker.get(SSC_EXTRAS_PET_UNTIL); }
    public void sscExtras$petUntil(long tick) { dataTracker.set(SSC_EXTRAS_PET_UNTIL, tick); }
    public boolean sscExtras$petting() { return dataTracker.get(SSC_EXTRAS_PETTING); }
    public void sscExtras$petting(boolean petting) { dataTracker.set(SSC_EXTRAS_PETTING, petting); }
    public MobEntity sscExtras$petter() { return sscExtras$petter; }
    public void sscExtras$petter(MobEntity petter) { sscExtras$petter = petter; }
    public long sscExtras$nextPet() { return sscExtras$nextPet; }
    public void sscExtras$nextPet(long tick) { sscExtras$nextPet = tick; }
    public long sscExtras$calledUntil() { return sscExtras$calledUntil; }
    public void sscExtras$calledUntil(long tick) { sscExtras$calledUntil = tick; }
}
