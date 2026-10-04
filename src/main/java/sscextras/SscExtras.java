package sscextras;

import net.fabricmc.api.ModInitializer;

public final class SscExtras implements ModInitializer {
    @Override
    public void onInitialize() {
        SscExtrasConfig.load();
        SscExtrasGameRules.register();
        ConfiguredLootChance.register();
        sscextras.drake.EarthenDrake.register();
        sscextras.effigy.FeralEffigy.register();
        sscextras.collar.Collars.register();
        sscextras.cuffs.MetalCuffs.register();
        sscextras.drake.DrakeEquipment.register();
        sscextras.drake.DrakeStable.register();
    }

    public static float instinctPerHit() {
        return SscExtrasConfig.instinctPerHit();
    }

    public static float instinctPerPotion() {
        return SscExtrasConfig.instinctPerPotion();
    }
}
