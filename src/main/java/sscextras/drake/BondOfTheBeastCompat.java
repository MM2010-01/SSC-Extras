package sscextras.drake;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class BondOfTheBeastCompat {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("bondofthebeast");
    private BondOfTheBeastCompat() { }

    public static boolean hasOwner(PlayerEntity player) { return LOADED && Loaded.hasOwner(player); }

    public static void registerPurchasedPlayer(ServerPlayerEntity pet, ServerPlayerEntity owner, String name) {
        if (LOADED) Loaded.registerPurchasedPlayer(pet, owner, name);
    }

    private static final class Loaded {
        static boolean hasOwner(PlayerEntity player) {
            return com.bondofthebeast.component.ModComponents.PLAYER_BOND.get(player).hasOwner();
        }

        static void registerPurchasedPlayer(ServerPlayerEntity pet, ServerPlayerEntity owner, String name) {
            var petBond = com.bondofthebeast.component.ModComponents.PLAYER_BOND.get(pet);
            petBond.setOwner(owner.getUuidAsString(), owner.getGameProfile().getName());
            petBond.setPetNickname(name);
            com.bondofthebeast.component.ModComponents.PLAYER_BOND.get(owner).addPetToRegistry(pet.getUuidAsString(), name);
            com.bondofthebeast.BondOfTheBeast.grantAdvancement(owner, "owner_story/tame_pet");
            com.bondofthebeast.BondOfTheBeast.grantAdvancement(pet, "pet_story/root");
            owner.sendMessage(Text.translatable("message.ssc-extras.merchant.pet_registered", name), false);
            pet.sendMessage(Text.translatable("message.ssc-extras.merchant.pet_owner", owner.getGameProfile().getName()), false);
        }
    }
}
