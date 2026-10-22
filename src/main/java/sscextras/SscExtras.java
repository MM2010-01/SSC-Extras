package sscextras;

import net.fabricmc.api.ModInitializer;

public final class SscExtras implements ModInitializer {
    @Override
    public void onInitialize() {
        SscExtrasConfig.load();
        SscExtrasGameRules.register();
        sscextras.player.EmptySlot.register();
        ConfiguredLootChance.register();
        sscextras.drake.EarthenDrake.register();
        // Explicit block interactions precede the empty-hand infused-tool fallback.
        sscextras.drake.DrakeHaySleep.register();
        sscextras.effigy.FeralEffigy.register();
        sscextras.collar.Collars.register();
        sscextras.cuffs.MetalCuffs.register();
        sscextras.drake.DrakeEquipment.register();
        sscextras.drake.DrakeStable.register();
        sscextras.drake.MountMerchants.register();
        sscextras.drake.DrakeOutpostOwnership.register();
        sscextras.drake.DrakeRitualCommands.register();
        sscextras.guardian.RuneboundGuardian.register();
        sscextras.golem.DrakeGolem.register();
        sscextras.player.PlayerAspects.register();
        sscextras.trainer.QuadrupedalTrainer.register();
        sscextras.petrification.Petrification.register();
    }

    public static float instinctPerHit() {
        return SscExtrasConfig.instinctPerHit();
    }

    public static float instinctPerPotion() {
        return SscExtrasConfig.instinctPerPotion();
    }
}
