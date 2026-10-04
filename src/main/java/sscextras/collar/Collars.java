package sscextras.collar;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.text.Text;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormDynamic;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormPhase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctTicker;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.RegPlayerInstinctComponent;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import net.onixary.shapeShifterCurseFabric.status_effects.BaseTransformativeStatusEffect;
import net.onixary.shapeShifterCurseFabric.status_effects.RegTStatusEffect;
import net.onixary.shapeShifterCurseFabric.status_effects.CTPUtils;
import net.onixary.shapeShifterCurseFabric.status_effects.transformative_effects.CustomTransformativeStatue;
import net.onixary.shapeShifterCurseFabric.status_effects.attachment.EffectManager;
import sscextras.CreatureInstinct;
import java.util.List;
import sscextras.InstinctTarget;

public final class Collars {
    public static final String INFUSION = "SscExtrasInfusion";
    public static final String INFUSION_FORM = "SscExtrasInfusionForm";
    public static final String AWAKENING = "SscExtrasAwakening";
    public static final CollarItem FERALIZING = new CollarItem(false);
    public static final CollarItem CURSED = new CollarItem(true);
    public static final RecipeSerializer<CollarInfusionRecipe> INFUSION_RECIPE = new SpecialRecipeSerializer<>(CollarInfusionRecipe::new);

    private Collars() { }

    public static void register() {
        Registry.register(Registries.ITEM, new Identifier("ssc-extras", "feralizing_collar"), FERALIZING);
        Registry.register(Registries.ITEM, new Identifier("ssc-extras", "cursed_feralizing_collar"), CURSED);
        Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("ssc-extras", "collar_infusion"), INFUSION_RECIPE);
        CuriosCompat.register(FERALIZING, CURSED);
        if (CuriosCompat.instance != null) {
            ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> CollarSlots.recoverLegacy(handler.player));
        }
        Registry.register(Registries.ITEM_GROUP, new Identifier("ssc-extras", "main"), FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.ssc-extras"))
                .icon(FERALIZING::getDefaultStack).entries((context, entries) -> {
            entries.add(FERALIZING);
            entries.add(CURSED.getDefaultStack());
            entries.add(sscextras.effigy.FeralEffigy.ITEM);
            entries.add(sscextras.drake.EarthenDrake.potion(net.minecraft.item.Items.POTION));
            entries.add(sscextras.drake.EarthenDrake.potion(net.minecraft.item.Items.SPLASH_POTION));
            entries.add(sscextras.drake.EarthenDrake.potion(net.minecraft.item.Items.LINGERING_POTION));
        }).build());
        CollarLoot.register();
    }

    public static int strength(PlayerEntity player) {
        int strength = 0;
        for (var slot : CollarSlots.get(player)) {
            if (slot.stack().getItem() instanceof CollarItem collar) strength = Math.max(strength, collar.strength());
        }
        return strength;
    }

    public static float gain(PlayerEntity player, float amount) {
        return amount > 0 ? amount * Math.max(1, strength(player) * 2) : amount;
    }

    public static boolean isCursed(PlayerEntity player) {
        return FormAbilityManager.getForm(player).getIndex() >= 0 || EffectManager.hasTransformativeEffect(player)
                || CreatureInstinct.getTarget(player) != null;
    }

    public static boolean tryEquip(PlayerEntity player, ItemStack source) {
        if (!(player instanceof ServerPlayerEntity) || source.isEmpty() || !source.isOf(CURSED)
                || !player.isAlive() || player.isSpectator() || !isCursed(player)
                || FormAbilityManager.getForm(player).getIndex() == 3) return false;
        CollarSlots.Slot destination = null;
        for (var slot : CollarSlots.get(player)) {
            if (!slot.stack().isEmpty()) return false;
            if (destination == null) destination = slot;
        }
        if (destination == null) return false;
        ensureNaturalCurse(source, player.getRandom());
        ItemStack equipped = source.copyWithCount(1);
        CURSED.ensureBinding(equipped);
        destination.set(player, equipped);
        ItemStack actual = destination.get(player);
        if (actual == null || !ItemStack.areEqual(actual, equipped)) return false;
        source.decrement(1);
        player.currentScreenHandler.sendContentUpdates();
        return true;
    }

    public static void equipCarried(PlayerEntity player) {
        if (!isCursed(player)) return;
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (tryEquip(player, player.getInventory().getStack(i))) {
                player.getInventory().markDirty();
                return;
            }
        }
    }

    public static void openedContainer(ServerPlayerEntity player) {
        if (!isCursed(player)) return;
        for (Slot slot : player.currentScreenHandler.slots) {
            if (slot.inventory == player.getInventory() || !slot.canTakeItems(player)) continue;
            if (tryEquip(player, slot.getStack())) {
                slot.markDirty();
                player.currentScreenHandler.sendContentUpdates();
                return;
            }
        }
    }

    public static BaseTransformativeStatusEffect infusion(ItemStack stack) {
        if (!stack.hasNbt()) return null;
        Identifier id = Identifier.tryParse(stack.getNbt().getString(INFUSION));
        return id != null && Registries.STATUS_EFFECT.get(id) instanceof BaseTransformativeStatusEffect effect ? effect : null;
    }

    public static PlayerFormBase infusionForm(ItemStack stack) {
        if (!stack.hasNbt()) return null;
        Identifier id = Identifier.tryParse(stack.getNbt().getString(INFUSION_FORM));
        return id == null ? null : RegPlayerForms.getPlayerForm(id);
    }

    public static boolean validForm(PlayerFormBase form) {
        return form != null && form.getIndex() >= 0 && form.getPhase() != PlayerFormPhase.PHASE_CLEAR
                && RegPlayerForms.playerForms.containsKey(form.FormID);
    }

    private static boolean availableForm(PlayerEntity player, PlayerFormBase form) {
        return validForm(form) && (!(form instanceof PlayerFormDynamic dynamic) || dynamic.IsPlayerCanUse(player));
    }

    public static List<BaseTransformativeStatusEffect> naturalCurses() {
        return List.of(RegTStatusEffect.TO_BAT_0_EFFECT, RegTStatusEffect.TO_AXOLOTL_0_EFFECT,
                RegTStatusEffect.TO_OCELOT_0_EFFECT, RegTStatusEffect.TO_ANUBIS_WOLF_0_EFFECT,
                RegTStatusEffect.TO_SPIDER_0_EFFECT, RegTStatusEffect.TO_FAMILIAR_FOX_0_EFFECT,
                sscextras.drake.EarthenDrake.CURSE);
    }

    public static void ensureNaturalCurse(ItemStack stack, Random random) {
        if (!stack.isOf(CURSED) || (stack.hasNbt()
                && (stack.getNbt().contains(INFUSION) || stack.getNbt().contains(INFUSION_FORM)))) return;
        var choices = naturalCurses();
        var effect = choices.get(random.nextInt(choices.size()));
        stack.getOrCreateNbt().putString(INFUSION, Registries.STATUS_EFFECT.getId(effect).toString());
    }

    public static void applyCurse(PlayerEntity player, ItemStack stack) {
        if (!(player instanceof ServerPlayerEntity) || !player.isAlive() || player.isSpectator()) return;
        ensureNaturalCurse(stack, player.getRandom());
        if (TransformManager.getPlayerTransformData(player).isTransforming || isCursed(player)) return;
        BaseTransformativeStatusEffect effect = infusion(stack);
        PlayerFormBase target = effect instanceof CustomTransformativeStatue ? infusionForm(stack)
                : effect == null ? null : effect.getToForm(player);
        if (effect == null || !availableForm(player, target)) return;
        if (FormAbilityManager.getForm(player) == RegPlayerForms.ORIGINAL_BEFORE_ENABLE) {
            stack.getOrCreateNbt().putBoolean(AWAKENING, true);
            TransformManager.handleDirectTransform(player, RegPlayerForms.ORIGINAL_SHIFTER, false);
            return;
        }
        stack.getOrCreateNbt().remove(AWAKENING);
        if (effect instanceof CustomTransformativeStatue) CTPUtils.setTransformativePotionForm(player, target.FormID);
        EffectManager.overrideEffect(player, effect);
        ((InstinctTarget) RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.get(player)).sscExtras$setTarget(target.FormID);
        RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.sync(player);
    }

    public static float instinctRate(PlayerEntity player) {
        if (!player.isAlive() || player.isSpectator()
                || InstinctTicker.isPausing || TransformManager.getPlayerTransformData(player).isTransforming) return 0;
        int strength = strength(player);
        if (strength == 0) return 0;
        var form = FormAbilityManager.getForm(player);
        if (form == RegPlayerForms.ORIGINAL_SHIFTER) {
            var effect = EffectManager.getTransformativeEffect(player);
            if (CreatureInstinct.getTarget(player) == null && effect != null) {
                var target = effect.getTransformativeEffectType().getToForm(player);
                if (target != null) ((InstinctTarget) RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.get(player)).sscExtras$setTarget(target.FormID);
            }
            if (CreatureInstinct.getTarget(player) == null) return 0;
        } else if (form.getIndex() < 0 || (form.getIndex() >= 2 && CreatureInstinct.permanentTarget(form) == null)) return 0;
        return strength * 0.05f / CreatureInstinct.costMultiplier(form);
    }

    public static boolean release(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity) || FormAbilityManager.getForm(player) != RegPlayerForms.ORIGINAL_SHIFTER
                || RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.get(player).instinctValue > 0
                || TransformManager.getPlayerTransformData(player).isTransforming) return false;
        boolean released = false;
        for (var slot : CollarSlots.includingLegacy(player)) {
            ItemStack stack = slot.get(player);
            if (!(stack.getItem() instanceof CollarItem)) continue;
            ItemStack dropped = stack.copy();
            slot.set(player, ItemStack.EMPTY);
            ItemStack remaining = slot.get(player);
            if (remaining != null && remaining.isEmpty()) {
                player.dropItem(dropped, false);
                released = true;
            }
        }
        if (released) {
            EffectManager.clearTransformativeEffect(player);
            InstinctTicker.clearInstinct(player);
            player.currentScreenHandler.sendContentUpdates();
        }
        return released;
    }
}
