package sscextras.drake;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.world.World;
import java.util.List;

public final class SentientCatalyst extends Item {
    public static final SentientCatalyst ITEM = new SentientCatalyst();
    public static final float LOOT_CHANCE = .05f, TRADE_CHANCE = .05f;

    private SentientCatalyst() {
        super(new Settings().maxCount(16).rarity(net.minecraft.util.Rarity.RARE)
                .food(new FoodComponent.Builder().hunger(0).saturationModifier(0).alwaysEdible().build()));
    }

    public static void register() {
        Registry.register(Registries.ITEM, EarthenDrake.id("sentient_catalyst"), ITEM);
        LootTableEvents.MODIFY.register((resources, manager, id, builder, source) -> {
            if (id.equals(new Identifier("minecraft", "chests/pillager_outpost"))
                    || id.equals(new Identifier("minecraft", "chests/woodland_mansion"))) {
                builder.pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(LOOT_CHANCE)).with(ItemEntry.builder(ITEM)));
            }
        });
    }

    public static TradeOffer trade(Random random) {
        return new TradeOffer(new ItemStack(Items.EMERALD, 32 + random.nextInt(33)), new ItemStack(ITEM), 1, 1, 0);
    }

    @Override public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        var result = super.finishUsing(stack, world, user);
        if (!world.isClient && user instanceof ServerPlayerEntity player) DrakeFeralization.recover(player);
        return result;
    }

    @Override public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.ssc-extras.sentient_catalyst.tooltip").formatted(Formatting.AQUA));
    }
}
