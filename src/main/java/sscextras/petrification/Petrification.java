package sscextras.petrification;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.item.*;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class Petrification {
    public static final int DOSE_INTERVAL = 100, BOTTLE_DURATION = 1200, STONE_DURATION = 3600;
    public static final net.minecraft.registry.tag.TagKey<net.minecraft.entity.EntityType<?>> STONE_BODIES =
            sscextras.player.StoneBodies.TYPES;
    public static final StatusEffect EFFECT = new StatusEffect(StatusEffectCategory.HARMFUL, 0x858581) {
        @Override public void onApplied(LivingEntity entity, net.minecraft.entity.attribute.AttributeContainer attributes, int amplifier) {
            super.onApplied(entity, attributes, amplifier);
            sscextras.player.StoneBodies.refreshArmor(entity, amplifier);
        }
        @Override public void onRemoved(LivingEntity entity, net.minecraft.entity.attribute.AttributeContainer attributes, int amplifier) {
            super.onRemoved(entity, attributes, amplifier);
            sscextras.player.StoneBodies.refreshArmor(entity, -1);
        }
        @Override public double adjustModifierAmount(int amplifier, EntityAttributeModifier modifier) {
            if (modifier.getOperation() == EntityAttributeModifier.Operation.ADDITION) return amplifier >= 2 ? 10 : 0;
            return amplifier == 0 ? -.95 : -1;
        }
    }.addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, "a112a2e2-28c6-4cff-b43f-f4c38ca61411",
            -.95, EntityAttributeModifier.Operation.MULTIPLY_TOTAL)
            .addAttributeModifier(EntityAttributes.GENERIC_ARMOR, "6cb9cd25-e4d8-4930-a380-b4140fad6b30", 10, EntityAttributeModifier.Operation.ADDITION);
    public static final Potion POTION = new Potion("ssc_extras_petrification", new StatusEffectInstance(EFFECT, BOTTLE_DURATION));
    public static final FlowableFluid STILL = new PetrifyingFluid.Still(), FLOWING = new PetrifyingFluid.Flowing();
    public static final Block LIQUID = new PetrifyingFluidBlock(STILL, AbstractBlock.Settings.copy(Blocks.WATER));
    public static final Item BUCKET = new BucketItem(STILL, new Item.Settings().recipeRemainder(Items.BUCKET).maxCount(1));
    public static final RecipeSerializer<PetrificationBucketRecipe> BUCKET_RECIPE = new SpecialRecipeSerializer<>(PetrificationBucketRecipe::new);

    private Petrification() { }
    public static Identifier id(String path) { return new Identifier("ssc-extras", path); }
    public static PetrificationState state(LivingEntity entity) { return PetrificationComponents.KEY.get(entity); }
    public static boolean frozen(LivingEntity entity) {
        var effect = entity.getStatusEffect(EFFECT);
        return entity.isAlive() && !entity.isSpectator() && effect != null && effect.getAmplifier() >= 1;
    }
    public static boolean stone(LivingEntity entity) {
        return frozen(entity) && entity.getStatusEffect(EFFECT).getAmplifier() >= 2;
    }
    /** Visual state survives native death ticking; it does not grant a dead body gameplay abilities. */
    public static boolean stoneAppearance(LivingEntity entity) {
        var effect = entity.getStatusEffect(EFFECT);
        return !entity.isSpectator() && effect != null && effect.getAmplifier() >= 2;
    }
    public static boolean poseLocked(LivingEntity entity) { return frozen(entity) || stoneAppearance(entity); }
    public static boolean stoneBody(LivingEntity entity) {
        return sscextras.player.StoneBodies.material(entity);
    }
    public static boolean replaceTexture(LivingEntity entity) { return stoneAppearance(entity) && !stoneBody(entity); }
    public static ItemStack potion(Item item) { return PotionUtil.setPotion(new ItemStack(item), POTION); }

    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity living) {
                sscextras.player.StoneBodies.refreshArmor(living);
                state(living).updateLock();
            }
        });
        Registry.register(Registries.STATUS_EFFECT, id("petrification"), EFFECT);
        Registry.register(Registries.POTION, id("petrification"), POTION);
        net.minecraft.recipe.BrewingRecipeRegistry.registerPotionRecipe(
                net.onixary.shapeShifterCurseFabric.items.RegCustomPotions.MOONDUST_POTION, Items.STONE, POTION);
        Registry.register(Registries.FLUID, id("petrification"), STILL);
        Registry.register(Registries.FLUID, id("flowing_petrification"), FLOWING);
        Registry.register(Registries.BLOCK, id("petrification"), LIQUID);
        Registry.register(Registries.ITEM, id("petrification_bucket"), BUCKET);
        Registry.register(Registries.RECIPE_SERIALIZER, id("petrification_bucket"), BUCKET_RECIPE);
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getStackInHand(hand);
            return frozen(player) && stack.getUseAction() != net.minecraft.util.UseAction.DRINK
                    ? net.minecraft.util.TypedActionResult.fail(stack) : net.minecraft.util.TypedActionResult.pass(stack);
        });
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                frozen(player) ? net.minecraft.util.ActionResult.FAIL : net.minecraft.util.ActionResult.PASS);
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, target, hit) ->
                frozen(player) || !state(player).miningPermissionCheck && PetrificationMining.mineable(player, target)
                        ? net.minecraft.util.ActionResult.FAIL : net.minecraft.util.ActionResult.PASS);
        PetrificationMining.register();
        PetrificationPose.register();
        PetrificationComponents.registerSync();
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) ->
                frozen(player) ? net.minecraft.util.ActionResult.FAIL : net.minecraft.util.ActionResult.PASS);
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, target, hit) ->
                frozen(player) ? net.minecraft.util.ActionResult.FAIL : net.minecraft.util.ActionResult.PASS);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> {
            entries.add(potion(Items.POTION)); entries.add(potion(Items.SPLASH_POTION)); entries.add(potion(Items.LINGERING_POTION));
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(BUCKET));
    }
}
