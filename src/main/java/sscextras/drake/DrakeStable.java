package sscextras.drake;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.StructurePieceType;

public final class DrakeStable {
    public static final EntityType<StableDrakeEntity> DRAKE = Registry.register(Registries.ENTITY_TYPE, EarthenDrake.id("stable_drake"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, StableDrakeEntity::new)
                    .dimensions(EntityDimensions.fixed(1.6f, 1.65f)).trackRangeBlocks(96).trackedUpdateRate(3).build());
    public static final StructurePieceType PIECE = Registry.register(Registries.STRUCTURE_PIECE, EarthenDrake.id("drake_stable"),
            (context, nbt) -> new DrakeStablePiece(nbt));
    public static final EntityType<DrakeVisitorEntity> VISITOR = Registry.register(Registries.ENTITY_TYPE, EarthenDrake.id("drake_visitor"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, DrakeVisitorEntity::new)
                    .dimensions(EntityDimensions.fixed(.6f, 1.95f)).trackRangeBlocks(64).build());
    public static final Item SPAWN_EGG = Registry.register(Registries.ITEM, EarthenDrake.id("stable_drake_spawn_egg"),
            new SpawnEggItem(DRAKE, 0x655537, 0xaca174, new Item.Settings()));

    public static void register() {
        FabricDefaultAttributeRegistry.register(DRAKE, StableDrakeEntity.attributes());
        FabricDefaultAttributeRegistry.register(VISITOR, net.minecraft.entity.mob.PillagerEntity.createPillagerAttributes());
        DrakeStableGuards.register();
    }
    private DrakeStable() { }
}
