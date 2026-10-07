package sscextras.drake;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.world.gen.chunk.placement.StructurePlacementType;
import net.minecraft.world.gen.structure.StructureType;

public final class MountMerchants {
    public static final int PRICE = 128;
    public static final EntityType<MountMerchantEntity> MERCHANT = Registry.register(Registries.ENTITY_TYPE, EarthenDrake.id("mount_merchant"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, MountMerchantEntity::new)
                    .dimensions(EntityDimensions.fixed(.6f, 1.95f)).trackRangeBlocks(64).build());
    public static final EntityType<MountBuyerEntity> BUYER = Registry.register(Registries.ENTITY_TYPE, EarthenDrake.id("mount_buyer"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, MountBuyerEntity::new)
                    .dimensions(EntityDimensions.fixed(.6f, 1.95f)).trackRangeBlocks(64).build());
    public static final ScreenHandlerType<MountMerchantScreenHandler> SCREEN = Registry.register(Registries.SCREEN_HANDLER,
            EarthenDrake.id("mount_merchant"), new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(MountMerchantScreenHandler::new));
    public static final StructurePieceType PIECE = Registry.register(Registries.STRUCTURE_PIECE,
            EarthenDrake.id("mount_merchant"), (context, nbt) -> new MountMerchantPiece(nbt));
    public static final StructureType<MountMerchantStructure> STRUCTURE = Registry.register(Registries.STRUCTURE_TYPE,
            EarthenDrake.id("mount_merchant"), () -> MountMerchantStructure.CODEC);
    public static final StructurePlacementType<MountMerchantPlacement> PLACEMENT = Registry.register(Registries.STRUCTURE_PLACEMENT,
            EarthenDrake.id("near_illagers"), () -> MountMerchantPlacement.CODEC);

    private MountMerchants() { }
    public static boolean trader(net.minecraft.entity.Entity entity) {
        return entity instanceof MountMerchantEntity || entity instanceof MountBuyerEntity;
    }
    public static void register() {
        FabricDefaultAttributeRegistry.register(MERCHANT, net.minecraft.entity.mob.PillagerEntity.createPillagerAttributes());
        FabricDefaultAttributeRegistry.register(BUYER, net.minecraft.entity.mob.PillagerEntity.createPillagerAttributes());
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof MountMerchantEntity merchant) merchant.interrupt();
            if (entity instanceof MountBuyerEntity buyer) buyer.interrupt();
        });
    }
}
