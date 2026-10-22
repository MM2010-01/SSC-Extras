package sscextras.trainer;

import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import sscextras.collar.AccessoryEquipment;
import sscextras.collar.CuriosCompat;
import sscextras.drake.EarthenDrake;
import sscextras.player.PlayerStance;

public final class QuadrupedalTrainer {
    public static final int DAY = 24000, HANDS = 1, FEET = 2, NECK = 4;
    public static final TrainerItem MOLD = new TrainerItem(false), COLLAR = new TrainerItem(true);
    private static final Identifier STANCE = id("quadrupedal_trainer");
    private QuadrupedalTrainer() { }
    public static Identifier id(String path) { return new Identifier("ssc-extras", path); }
    public static void register() {
        Registry.register(Registries.ITEM, id("digitigrade_mold"), MOLD);
        Registry.register(Registries.ITEM, id("feral_neck_support"), COLLAR);
        CuriosCompat.register(MOLD, COLLAR);
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, player, alive) -> {
            var nbt = new net.minecraft.nbt.NbtCompound();
            TrainerState.get(oldPlayer).write(nbt); TrainerState.get(player).read(nbt);
            TrainerState.get(player).sync(player, 0);
        });
    }
    public static String group(int location) {
        return CuriosCompat.instance != null ? "" : location == HANDS ? "hand" : location == FEET ? "feet" : "chest";
    }
    public static String slot(int location) {
        return location == NECK ? "necklace" : CuriosCompat.instance != null
                ? location == HANDS ? "hands" : "feet" : location == HANDS ? "glove" : "shoes";
    }
    public static List<ItemStack> stacks(PlayerEntity player, int location) {
        var io = AccessoryEquipment.slots();
        var stacks = io == null ? null : io.getEntitySlot(player, group(location), slot(location));
        return stacks == null ? List.of() : stacks;
    }
    public static ItemStack equipped(PlayerEntity player, int location, boolean visible) {
        var stacks = stacks(player, location);
        for (int i = 0; i < stacks.size(); i++)
            if (stacks.get(i).isOf(location == NECK ? COLLAR : MOLD)
                    && (!visible || CuriosCompat.instance == null || CuriosCompat.instance.visible(player, slot(location), i))) return stacks.get(i);
        return ItemStack.EMPTY;
    }
    public static int equipment(PlayerEntity player) {
        if (player.getWorld().isClient) return ((TrainerState.Owner)player).sscExtras$trainerEquipment();
        return (equipped(player, HANDS, false).isEmpty() ? 0 : HANDS)
                | (equipped(player, FEET, false).isEmpty() ? 0 : FEET)
                | (equipped(player, NECK, false).isEmpty() ? 0 : NECK);
    }
    public static boolean nativeQuadruped(PlayerEntity player) {
        // SSC's FERAL rigs already own their anatomy and animation. The upright
        // stage-two drake is the explicit exception in the installed dependency.
        return FormAbilityManager.getForm(player).getBodyType() == PlayerFormBodyType.FERAL && EarthenDrake.stage(player) != 2;
    }
    public static boolean nativeDigitigrade(PlayerEntity player) {
        var form = FormAbilityManager.getForm(player);
        return nativeQuadruped(player) || EarthenDrake.stage(player) >= 2
                || sscextras.guardian.RuneboundGuardian.stage(player) >= 2
                || form == net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.OCELOT_2
                || form == net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.SNOW_FOX_2
                || form == net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.FAMILIAR_FOX_2
                || form == net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.ANUBIS_WOLF_2;
    }
    public static float quadProgress(PlayerEntity player) { return (progress(player, HANDS) + progress(player, FEET)) * .5f; }
    public static float progress(PlayerEntity player, int location) {
        if (location == HANDS && nativeQuadruped(player) || location == FEET && nativeDigitigrade(player)) return 1;
        if (player.getWorld().isClient) return (location == HANDS ? ((TrainerState.Owner)player).sscExtras$trainerHands()
                : ((TrainerState.Owner)player).sscExtras$trainerFeet()) / 1200f;
        var state = TrainerState.get(player);
        return (location == HANDS ? state.handsTicks : state.feetTicks) / (float)DAY;
    }
    public static boolean complete(PlayerEntity player) { return progress(player, HANDS) >= 1 && progress(player, FEET) >= 1; }
    public static boolean mouth(PlayerEntity player) {
        return (equipment(player) & HANDS) != 0 || (player.getWorld().isClient
                ? ((TrainerState.Owner)player).sscExtras$trainerHands() > 0 : TrainerState.get(player).handsTicks > 0);
    }
    public static boolean reshapes(PlayerEntity player, int location) {
        // Already digitigrade species retain all of their authored limbs, including
        // forepaws. Training still owns posture and mouth handling, never a replacement rig.
        if (nativeDigitigrade(player)) return false;
        return progress(player, location) > 0;
    }
    public static boolean neckLocked(PlayerEntity player) {
        // Pitch is also assigned during player construction, before the server game-mode manager exists.
        return (((TrainerState.Owner)player).sscExtras$trainerEquipment() & NECK) != 0 && player.isAlive() && !player.isSpectator();
    }
    public static float pitch(PlayerEntity player) {
        return PlayerStance.quadrupedal(player) || nativeQuadruped(player) || player.isSwimming() || player.isFallFlying() ? 0 : -90;
    }
    public static void refresh(ServerPlayerEntity player) {
        var state = TrainerState.get(player); int worn = equipment(player);
        boolean require = player.isAlive() && !player.isSpectator() && (worn == 7
                || complete(player) && (state.handsTicks > 0 || state.feetTicks > 0));
        if (require && !nativeQuadruped(player)) {
            if (state.stance == null || !state.stance.active()) state.stance = PlayerStance.request(player, STANCE, PlayerStance.Stance.QUADRUPEDAL);
        } else if (state.stance != null) { state.stance.close(); state.stance = null; }
        state.sync(player, worn);
        if (mouth(player) && !player.isSpectator() && !player.getOffHandStack().isEmpty()) {
            var previous = player.getOffHandStack(); player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            player.getInventory().offerOrDrop(previous); player.currentScreenHandler.sendContentUpdates();
        }
        boolean finished = complete(player);
        for (int location : new int[]{HANDS, FEET, NECK}) for (var stack : stacks(player, location)) {
            if (!stack.isOf(location == NECK ? COLLAR : MOLD)) continue;
            binding(stack, !finished);
        }
        if (finished && worn != 0 && !state.announced) {
            state.announced = true; player.sendMessage(Text.translatable("message.ssc-extras.trainer.complete"), false);
        }
        if (neckLocked(player)) player.setPitch(pitch(player));
    }
    public static void tick(ServerPlayerEntity player) {
        var state = TrainerState.get(player);
        if (state.lastTick == player.age) return;
        state.lastTick = player.age;
        int worn = equipment(player);
        if (player.isAlive() && !player.isSpectator()) {
            if ((worn & HANDS) != 0 && !nativeQuadruped(player)) state.handsTicks = Math.min(DAY, state.handsTicks + 1);
            if ((worn & FEET) != 0 && !nativeDigitigrade(player)) state.feetTicks = Math.min(DAY, state.feetTicks + 1);
        }
        refresh(player);
        if (sscextras.player.PlayerMouth.preventsUse(player, player.getActiveItem())) player.clearActiveItem();
    }
    public static void binding(ItemStack stack, boolean bound) {
        if (EnchantmentHelper.hasBindingCurse(stack) == bound) return;
        var enchants = EnchantmentHelper.get(stack);
        if (bound) enchants.put(Enchantments.BINDING_CURSE, 1); else enchants.remove(Enchantments.BINDING_CURSE);
        EnchantmentHelper.set(enchants, stack);
    }
    public static void cure(ServerPlayerEntity player) {
        var state = TrainerState.get(player);
        if (state.handsTicks == 0 && state.feetTicks == 0) return;
        state.handsTicks = state.feetTicks = 0; state.announced = false;
        refresh(player);
        player.sendMessage(Text.translatable("message.ssc-extras.trainer.cured"), false);
    }
}
