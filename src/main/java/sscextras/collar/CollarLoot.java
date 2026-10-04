package sscextras.collar;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetEnchantmentsLootFunction;
import net.minecraft.loot.function.SetNbtLootFunction;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import java.util.Set;

public final class CollarLoot {
    private static final Set<String> VANILLA = Set.of("chests/simple_dungeon", "chests/abandoned_mineshaft",
            "chests/stronghold_corridor", "chests/stronghold_crossing", "chests/stronghold_library",
            "chests/desert_pyramid", "chests/jungle_temple", "chests/nether_bridge",
            "chests/ancient_city", "chests/woodland_mansion", "chests/end_city_treasure",
            "chests/bastion_bridge", "chests/bastion_hoglin_stable", "chests/bastion_other", "chests/bastion_treasure");
    private static final Set<String> DUNGEONS = Set.of("dungeoncrawl", "dungeons_arise", "betterdungeons",
            "betterstrongholds", "betterdeserttemples", "betterjungletemples", "betterfortresses", "betteroceanmonuments");
    public static final float CHANCE = 1.0f / 15.0f;

    private CollarLoot() { }

    public static boolean eligible(Identifier id) {
        return id.getNamespace().equals("minecraft") ? VANILLA.contains(id.getPath())
                : DUNGEONS.contains(id.getNamespace()) && (id.getPath().startsWith("chests/") || id.getPath().contains("/chests/"));
    }

    public static void register() {
        Registry.register(Registries.LOOT_CONDITION_TYPE, new Identifier("ssc-extras", "root_chest"), RootChestCondition.TYPE);
        LootTableEvents.MODIFY.register((resources, manager, id, builder, source) -> {
            if (!eligible(id)) return;
            var pool = LootPool.builder().rolls(ConstantLootNumberProvider.create(1))
                    .conditionally(() -> RootChestCondition.INSTANCE)
                    .conditionally(RandomChanceLootCondition.builder(CHANCE));
            for (var curse : Collars.naturalCurses()) {
                var data = new NbtCompound();
                data.putString(Collars.INFUSION, Registries.STATUS_EFFECT.getId(curse).toString());
                pool.with(ItemEntry.builder(Collars.CURSED)
                        .apply(SetNbtLootFunction.builder(data))
                        .apply(new SetEnchantmentsLootFunction.Builder()
                                .enchantment(Enchantments.BINDING_CURSE, ConstantLootNumberProvider.create(1))));
            }
            builder.pool(pool);
        });
    }
}
