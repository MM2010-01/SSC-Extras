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
    public static final int MAX_PROGRESS = 24000, HANDS = 1, FEET = 2, NECK = 4;
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
        var form = FormAbilityManager.getForm(player);
        return form.getBodyType() == PlayerFormBodyType.FERAL && EarthenDrake.stage(player) != 2
                && form.getGroup() != net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.BAT_FORM
                && form.getGroup() != net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.SPIDER_FORM;
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
    public enum Fit { SUITABLE, FRAME, EXOTIC, FERAL, DIGITIGRADE }
    public static Fit fit(PlayerEntity player) {
        var form = FormAbilityManager.getForm(player);
        if (form.getGroup() == net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.SPIDER_FORM) return Fit.EXOTIC;
        if (form == net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.BAT_2
                || form == net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.BAT_3
                || form == net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.ALLAY_SP) return Fit.FRAME;
        if (nativeQuadruped(player) || sscextras.drake.DrakeFeralization.permanent(player)) return Fit.FERAL;
        if (nativeDigitigrade(player)) return Fit.DIGITIGRADE;
        if (!PlayerStance.supports(player, PlayerStance.Stance.QUADRUPEDAL)) return Fit.EXOTIC;
        return Fit.SUITABLE;
    }
    public static float quadProgress(PlayerEntity player) {
        if (nativeQuadruped(player) || sscextras.drake.DrakeFeralization.permanent(player)
                || sscextras.drake.DrakeSoulbinding.bound(player)) return 1;
        return (player.getWorld().isClient ? ((TrainerState.Owner)player).sscExtras$trainerQuad() : TrainerState.get(player).quad) / (float)MAX_PROGRESS;
    }
    public static void reject(PlayerEntity player, Fit fit) {
        if (player.getWorld().isClient) return;
        var state = TrainerState.get(player);
        if (state.lastHint != Integer.MIN_VALUE && player.age - state.lastHint < 100) return;
        state.lastHint = player.age;
        player.sendMessage(Text.translatable("message.ssc-extras.trainer.reject_" + fit.name().toLowerCase(java.util.Locale.ROOT)), false);
    }
    public static float progress(PlayerEntity player, int location) {
        if (location == HANDS && nativeQuadruped(player) || location == FEET && nativeDigitigrade(player)) return 1;
        if (player.getWorld().isClient) return (location == HANDS ? ((TrainerState.Owner)player).sscExtras$trainerHands()
                : ((TrainerState.Owner)player).sscExtras$trainerFeet()) / 1200f;
        var state = TrainerState.get(player);
        return (location == HANDS ? state.handsTicks : state.feetTicks) / (float)MAX_PROGRESS;
    }
    public static boolean complete(PlayerEntity player) { return quadProgress(player) >= 1; }
    public static boolean mouth(PlayerEntity player) {
        return (equipment(player) & HANDS) != 0 || (player.getWorld().isClient
                ? ((TrainerState.Owner)player).sscExtras$trainerHands() > 0 : TrainerState.get(player).handsTicks > 0);
    }
    public static boolean reshapes(PlayerEntity player, int location) {
        // Already digitigrade species retain all of their authored limbs, including
        // forepaws. Training still owns posture and mouth handling, never a replacement rig.
        Fit fit = fit(player);
        if (nativeDigitigrade(player) || fit == Fit.FRAME || fit == Fit.EXOTIC) return false;
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
        Fit fit = fit(player);
        if (worn != 0 && player.isAlive() && !player.isSpectator()) {
            if (fit != Fit.SUITABLE) {
                if (release(player) > 0) reject(player, fit);
                worn = equipment(player);
            } else if (complete(player)) {
                // Accessories may invoke refresh separately while the set changes.
                // Reserve the course's release feedback before touching any slots.
                boolean announce = (state.milestones & 8) == 0;
                state.milestones |= 8;
                int released = release(player);
                if (released > 0 && announce) {
                    player.sendMessage(Text.translatable("message.ssc-extras.trainer.released"), false);
                    player.getWorld().playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN,
                            player.getSoundCategory(), .8f, .7f);
                }
                if (released == 0 && announce) state.milestones &= ~8;
                worn = equipment(player);
            }
        }
        boolean require = player.isAlive() && !player.isSpectator() && ((worn & (HANDS | FEET)) != 0
                || state.quad == MAX_PROGRESS || quadProgress(player) > .5f && (player.isSprinting() || player.isSneaking()));
        if (require && !nativeQuadruped(player)) {
            if (state.stance == null || !state.stance.active()) state.stance = PlayerStance.request(player, STANCE, PlayerStance.Stance.QUADRUPEDAL);
        } else if (state.stance != null) { state.stance.close(); state.stance = null; }
        state.sync(player, worn);
        if (mouth(player) && !player.isSpectator() && !player.getOffHandStack().isEmpty()) {
            var previous = player.getOffHandStack(); player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            player.getInventory().offerOrDrop(previous); player.currentScreenHandler.sendContentUpdates();
        }
        for (int location : new int[]{HANDS, FEET, NECK}) for (var stack : stacks(player, location)) {
            if (stack.isOf(location == NECK ? COLLAR : MOLD)) binding(stack, !complete(player));
        }
        if (neckLocked(player)) player.setPitch(pitch(player));
    }
    /** Verified removal uses the shared active accessory backend, preserving every item. */
    public static int release(ServerPlayerEntity player) {
        var io = AccessoryEquipment.slots(); if (io == null) return 0;
        int removed = 0;
        for (int location : new int[]{HANDS, FEET, NECK}) {
            var stacks = stacks(player, location);
            for (int i = 0; i < stacks.size(); i++) {
                if (!stacks.get(i).isOf(location == NECK ? COLLAR : MOLD)) continue;
                if (AccessoryEquipment.clear(player, io, group(location), slot(location), i, stack -> {
                    binding(stack, false); player.getInventory().offerOrDrop(stack);
                })) removed++;
            }
        }
        return removed;
    }
    public static void tick(ServerPlayerEntity player) {
        var state = TrainerState.get(player);
        if (state.lastTick == player.age) return;
        state.lastTick = player.age;
        double distance = state.previousPosition == null ? 0 : player.getPos().subtract(state.previousPosition).horizontalLengthSquared();
        state.previousPosition = player.getPos();
        refresh(player);
        if (!player.isAlive() || player.isSpectator()) return;
        int worn = equipment(player);
        if ((worn & (HANDS | FEET)) != 0 && fit(player) == Fit.SUITABLE) {
            // Mounted transport, swimming, flying and teleports do not train a walking gait.
            boolean moving = distance > .000001 && distance < 1 && player.isOnGround()
                    && !player.hasVehicle() && !player.isSwimming() && !player.isFallFlying() && !player.getAbilities().flying;
            int mode = !moving ? 0 : player.isSprinting() || player.isSneaking() ? 2 : 1;
            if (++state.activityTicks[mode] >= (mode == 0 ? 100 : 20)) {
                state.activityTicks[mode] = 0;
                int amount = mode == 2 ? 240 : 48; // 1% or 0.2% in the existing 24,000-unit scale.
                if ((worn & HANDS) != 0) state.handsTicks = Math.min(MAX_PROGRESS, state.handsTicks + amount);
                if ((worn & FEET) != 0) state.feetTicks = Math.min(MAX_PROGRESS, state.feetTicks + amount);
                advance(player, amount, true);
            }
            state.recoveryTicks = 0;
        } else {
            state.resetActivity();
            boolean upright = worn == 0 && PlayerStance.current(player) == PlayerStance.Stance.UPRIGHT
                    && !player.isSneaking() && !player.isSwimming() && !player.isFallFlying()
                    && !player.isSleeping() && !player.hasVehicle() && player.isOnGround();
            if (upright && state.quad > 0 && state.quad < MAX_PROGRESS) {
                if (++state.recoveryTicks >= 100) { state.recoveryTicks = 0; state.quad = Math.max(0, state.quad - 48); }
            } else state.recoveryTicks = 0;
        }
        refresh(player);
        if (sscextras.player.PlayerMouth.preventsUse(player, player.getActiveItem())) player.clearActiveItem();
    }
    private static void advance(ServerPlayerEntity player, int amount, boolean molds) {
        var state = TrainerState.get(player); int previous = state.quad;
        state.quad = Math.min(MAX_PROGRESS, state.quad + Math.max(0, amount));
        if (molds) {
            int[] thresholds = {1200, 12000, MAX_PROGRESS};
            String[] keys = {"first_change", "halfway", "complete"};
            for (int i = 0; i < thresholds.length; i++) if (previous < thresholds[i] && state.quad >= thresholds[i]
                    && (state.milestones & (1 << i)) == 0) {
                state.milestones |= 1 << i;
                player.sendMessage(Text.translatable("message.ssc-extras.trainer." + keys[i]), false);
            }
        }
    }
    /** Called once after a ritual dose is swallowed. It never changes limb anatomy. */
    public static void ritualCatalyst(ServerPlayerEntity player) { advance(player, (MAX_PROGRESS + 8) / 9, false); refresh(player); }
    public static void ritualComplete(ServerPlayerEntity player) { advance(player, MAX_PROGRESS, false); refresh(player); }
    public static void binding(ItemStack stack, boolean bound) {
        if (EnchantmentHelper.hasBindingCurse(stack) == bound) return;
        var enchants = EnchantmentHelper.get(stack);
        if (bound) enchants.put(Enchantments.BINDING_CURSE, 1); else enchants.remove(Enchantments.BINDING_CURSE);
        EnchantmentHelper.set(enchants, stack);
    }
    public static void cure(ServerPlayerEntity player) {
        var state = TrainerState.get(player);
        if (state.handsTicks == 0 && state.feetTicks == 0 && state.quad == 0) return;
        state.handsTicks = state.feetTicks = state.quad = state.milestones = 0;
        state.resetActivity(); state.recoveryTicks = 0;
        refresh(player);
        player.sendMessage(Text.translatable("message.ssc-extras.trainer.cured"), false);
    }
}
