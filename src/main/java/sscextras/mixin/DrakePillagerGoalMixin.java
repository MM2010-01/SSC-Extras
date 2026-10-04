package sscextras.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.IllagerEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeFaction;

@Mixin(PillagerEntity.class)
public abstract class DrakePillagerGoalMixin extends IllagerEntity {
    protected DrakePillagerGoalMixin(EntityType<? extends IllagerEntity> type, World world) { super(type, world); }

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void sscExtras$completeDrakeHarness(CallbackInfo ci) {
        goalSelector.add(1, new DrakeFaction.EquipGoal((PillagerEntity)(Object)this));
    }
}
