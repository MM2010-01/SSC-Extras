package sscextras.drake;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryUtils;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import sscextras.collar.Collars;
import sscextras.collar.CuriosCompat;
import java.util.List;

public final class DrakeEquipment {
    public static final DrakeAccessoryItem REINS = new DrakeAccessoryItem("head", "face", "cursed_reins", false, false);
    public static final DrakeAccessoryItem SADDLE = new DrakeAccessoryItem("chest", "back", "cursed_saddle", false, false);
    public static final DrakeAccessoryItem RIDERS_CHEST = new DrakeAccessoryItem("chest", "cape", "riders_chest", true, false);
    public static final DrakeAccessoryItem CLAW_TIPS = new DrakeAccessoryItem("hand", "glove", "netherite_claw_tips", true, true);

    private DrakeEquipment() { }

    public static void register() {
        Registry.register(Registries.ITEM, EarthenDrake.id("cursed_reins"), REINS);
        Registry.register(Registries.ITEM, EarthenDrake.id("cursed_saddle"), SADDLE);
        Registry.register(Registries.ITEM, EarthenDrake.id("riders_chest"), RIDERS_CHEST);
        Registry.register(Registries.ITEM, EarthenDrake.id("netherite_claw_tips"), CLAW_TIPS);
        CuriosCompat.register(REINS, SADDLE, RIDERS_CHEST, CLAW_TIPS);
        CuriosCompat.register(Items.SADDLE, SADDLE);
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("trinkets")) VanillaSaddleTrinket.register();
        DrakeRiding.register();
        DrakeFaction.register();
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (player.isSpectator()) return ActionResult.PASS;
            if (hand == Hand.MAIN_HAND && player.isSneaking() && entity instanceof net.minecraft.entity.mob.MobEntity passenger
                    && DrakeRiding.accepts(player, passenger) && !player.hasPassengers() && !player.hasVehicle()
                    && passenger.isAlive() && !passenger.hasVehicle() && !passenger.hasPassengers()) {
                if (!world.isClient) {
                    passenger.getNavigation().stop();
                    if (!passenger.startRiding(player)) return ActionResult.FAIL;
                }
                return ActionResult.SUCCESS;
            }
            if (!(entity instanceof PlayerEntity drake) || EarthenDrake.stage(drake) < 2) return ActionResult.PASS;
            ItemStack held = player.getStackInHand(hand);
            if (held.isOf(REINS) || isSaddle(held) || held.isOf(RIDERS_CHEST)) {
                return tryEquip(drake, held, !player.isCreative()) ? ActionResult.SUCCESS : ActionResult.FAIL;
            }
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (player.isSneaking() && !equipped(drake, RIDERS_CHEST).isEmpty()) {
                if (player instanceof ServerPlayerEntity serverPlayer) RiderChestInventory.open(serverPlayer, drake);
                return ActionResult.SUCCESS;
            }
            if (!player.isSneaking() && canRide(drake) && player.getVehicle() == null && !drake.hasPassengers()
                    && !drake.hasVehicle() && player != drake) {
                if (!world.isClient) player.startRiding(drake);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }

    public static AccessoryUtils.AccessoryIO slots() {
        return CuriosCompat.instance != null ? CuriosCompat.instance : AccessoryUtils.nowAccessoryMod;
    }

    public static boolean tryEquip(PlayerEntity player, ItemStack source, boolean consume) {
        DrakeAccessoryItem item = source.isOf(Items.SADDLE) ? SADDLE
                : source.getItem() instanceof DrakeAccessoryItem accessory ? accessory : null;
        if (!player.isAlive() || player.isSpectator() || source.isEmpty() || item == null) return false;
        var io = slots();
        if (io == null) return false;
        String group = CuriosCompat.instance == null ? item.group : "";
        String name = CuriosCompat.instance == null ? item.slot : item.curiosSlot();
        var stacks = stacks(player, item);
        for (int index = 0; index < stacks.size(); index++) {
            if (!stacks.get(index).isEmpty()) continue;
            var data = new AccessoryItem.SlotData(new net.minecraft.util.Identifier(
                    CuriosCompat.instance == null ? "trinkets" : "curios",
                    CuriosCompat.instance == null ? group + "/" + name : name), index);
            if (!item.canEquip(source, player, data)) continue;
            if (player.getWorld().isClient) return true;
            ItemStack copy = source.copyWithCount(1);
            io.setEntitySlot(player, group, name, index, copy);
            ItemStack equipped = io.getEntitySlot(player, group, name, index);
            if (equipped == null || !ItemStack.areEqual(equipped, copy)) return false;
            item.onEquip(equipped, player, data);
            if (consume) source.decrement(1);
            player.currentScreenHandler.sendContentUpdates();
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_HORSE_SADDLE, SoundCategory.PLAYERS, 1, 1);
            return true;
        }
        return false;
    }

    public static List<ItemStack> stacks(PlayerEntity player, DrakeAccessoryItem item) {
        var io = slots();
        if (io == null) return List.of();
        var stacks = io.getEntitySlot(player, CuriosCompat.instance != null ? "" : item.group,
                CuriosCompat.instance != null ? item.curiosSlot() : item.slot);
        return stacks == null ? List.of() : stacks;
    }

    public static ItemStack equipped(PlayerEntity player, DrakeAccessoryItem item) {
        if (player.isSpectator() || item.permanentOnly && EarthenDrake.stage(player) != 3) return ItemStack.EMPTY;
        for (ItemStack stack : stacks(player, item)) if (stack.isOf(item)) return stack;
        return ItemStack.EMPTY;
    }

    public static boolean visible(PlayerEntity player, DrakeAccessoryItem item) {
        var stacks = stacks(player, item);
        for (int i = 0; i < stacks.size(); i++) if ((stacks.get(i).isOf(item) || item == SADDLE && stacks.get(i).isOf(Items.SADDLE))
                && (CuriosCompat.instance == null || CuriosCompat.instance.visible(player, item.curiosSlot(), i))) return true;
        return false;
    }

    public static void tickEquipped(PlayerEntity player, ItemStack stack) {
        if (stack.isOf(REINS) || isSaddle(stack)) {
            if (!stack.isOf(Items.SADDLE)) {
                stack.getOrCreateNbt().putString(Collars.INFUSION, Registries.STATUS_EFFECT.getId(EarthenDrake.CURSE).toString());
                Collars.applyCurse(player, stack);
            }
            ItemStack reins = equipped(player, REINS), saddle = saddle(player);
            if (!reins.isEmpty() && !saddle.isEmpty()) {
                bind(reins);
                bind(saddle);
            }
        }
    }

    private static void bind(ItemStack stack) {
        if (!EnchantmentHelper.hasBindingCurse(stack)) stack.addEnchantment(Enchantments.BINDING_CURSE, 1);
    }

    public static boolean canRide(Entity entity) {
        return entity instanceof PlayerEntity player && player.isAlive() && EarthenDrake.stage(player) == 3
                && !saddle(player).isEmpty() || entity instanceof StableDrakeEntity drake && drake.isSaddled();
    }

    public static boolean isSaddle(ItemStack stack) { return stack.isOf(SADDLE) || stack.isOf(Items.SADDLE); }

    public static ItemStack saddle(PlayerEntity player) {
        if (!player.isSpectator()) for (ItemStack stack : stacks(player, SADDLE)) if (isSaddle(stack)) return stack;
        return ItemStack.EMPTY;
    }

    public static boolean hasReins(Entity entity) {
        return entity instanceof PlayerEntity player && !equipped(player, REINS).isEmpty()
                || entity instanceof StableDrakeEntity drake && drake.hasReins();
    }

    public static ItemStack claws(PlayerEntity player, Item tool) {
        if (!player.getInventory().getMainHandStack().isEmpty()) return ItemStack.EMPTY;
        ItemStack tips = equipped(player, CLAW_TIPS);
        if (tips.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(tool);
        EnchantmentHelper.set(EnchantmentHelper.get(tips), result);
        result.getOrCreateNbt().putBoolean("Unbreakable", true);
        return result;
    }
}
