package sscextras.drake;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

public final class BondOfTheBeastCompat {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("bondofthebeast");
    private BondOfTheBeastCompat() { }

    public static boolean hasOwner(PlayerEntity player) { return LOADED && Loaded.hasOwner(player); }

    private static final class Loaded {
        static boolean hasOwner(PlayerEntity player) {
            return com.bondofthebeast.component.ModComponents.PLAYER_BOND.get(player).hasOwner();
        }
    }
}
