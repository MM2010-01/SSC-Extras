package sscextras.drake;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctManager;
import java.util.List;

public final class BeastizationCatalyst extends Item {
    public static final int DURATION = 1200;
    public static final float INSTINCT = 200;
    public static final BeastizationCatalyst ITEM = new BeastizationCatalyst();
    public static final StatusEffect TOTAL_FERALIZED = new StatusEffect(StatusEffectCategory.HARMFUL, 0x6b159e) { };

    private BeastizationCatalyst() {
        super(new Settings().maxCount(16).food(new FoodComponent.Builder().hunger(2).saturationModifier(.3f).alwaysEdible().build()));
    }

    public static void register() {
        Registry.register(Registries.ITEM, EarthenDrake.id("full_beastization_catalyst"), ITEM);
        Registry.register(Registries.STATUS_EFFECT, EarthenDrake.id("total_feralized"), TOTAL_FERALIZED);
    }

    @Override public int getMaxUseTime(ItemStack stack) { return DrakeSoulbinding.FEED_TICKS; }

    @Override public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        var result = super.finishUsing(stack, world, user);
        if (!world.isClient && user instanceof PlayerEntity player) apply(player);
        return result;
    }

    static void apply(PlayerEntity player) {
        InstinctManager.applyImmediateEffect(player, "ssc-extras:full_beastization_catalyst", INSTINCT);
        player.addStatusEffect(new StatusEffectInstance(TOTAL_FERALIZED, DURATION));
        DrakeFeralization.sync(player, DrakeOutpostOwnership.claim(player));
    }

    @Override public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.ssc-extras.full_beastization_catalyst.tooltip").formatted(Formatting.DARK_PURPLE));
    }
}
