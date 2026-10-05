package sscextras.drake;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;

public final class DrakeFeralization {
    public static final int ESCAPES = 6, MIN_CONTROL_TICKS = 100, MAX_CONTROL_TICKS = 600;
    public static final TagKey<net.minecraft.item.Item> RAW_FOOD = TagKey.of(RegistryKeys.ITEM, EarthenDrake.id("drake_raw_food"));

    public static final class Control {
        int ticks, next;
        FeralDrakeBrain brain;
    }

    public interface State {
        Control sscExtras$feralRuntime();
        boolean sscExtras$feral();
        void sscExtras$feral(boolean value);
        boolean sscExtras$feralControl();
        void sscExtras$feralControl(boolean value);
    }

    private DrakeFeralization() { }

    public static boolean due(DrakeOutpostOwnership.Claim claim) { return claim.escapes >= ESCAPES && !claim.feral; }
    public static boolean rawFood(ItemStack stack) { return stack.isFood() && stack.isIn(RAW_FOOD); }
    public static boolean permanent(PlayerEntity player) {
        if (player.getWorld().isClient) return ((State)player).sscExtras$feral();
        var claim = DrakeOutpostOwnership.claim(player);
        return claim != null && claim.feral;
    }
    public static boolean feral(PlayerEntity player) { return permanent(player) || temporary(player); }
    public static boolean temporary(PlayerEntity player) { return player.hasStatusEffect(BeastizationCatalyst.TOTAL_FERALIZED); }
    public static boolean edible(ItemStack stack) { return stack.isFood(); }
    public static boolean restricted(PlayerEntity player) { return feral(player) && !player.isCreative() && !player.isSpectator(); }
    public static boolean controlled(PlayerEntity player) { return ((State)player).sscExtras$feralControl(); }

    static void sync(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        ((State)player).sscExtras$feral(claim != null && claim.feral);
        if (!feral(player) && (claim == null || !claim.returning) && controlled(player)) stop(player, claim);
    }

    public static ActionResult useBlock(PlayerEntity player, net.minecraft.util.math.BlockPos pos) {
        if (!restricted(player)) return ActionResult.PASS;
        if (!controlled(player) && player.getWorld().getBlockState(pos).isOf(Blocks.HAY_BLOCK)) {
            if (player instanceof ServerPlayerEntity serverPlayer) DrakeHaySleep.sleep(serverPlayer, pos);
            return ActionResult.SUCCESS;
        }
        return ActionResult.FAIL;
    }

    public static boolean allowInventoryClick(PlayerEntity player, int slot, int button, SlotActionType action) {
        if (!restricted(player)) return true;
        if (controlled(player) || player.currentScreenHandler != player.playerScreenHandler || slot < 9 || slot > 45) return false;
        var handler = player.playerScreenHandler;
        var stack = handler.getSlot(slot).getStack();
        if (action == SlotActionType.SWAP) {
            return button == 40 || button >= 0 && button <= 8;
        }
        return action == SlotActionType.PICKUP || action == SlotActionType.QUICK_MOVE && edible(stack);
    }

    private static boolean canTakeOver(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        return feral(player) && player.isAlive() && restricted(player) && (claim == null || !claim.awaitingRespawn)
                && !player.isSleeping() && !player.hasVehicle() && !player.hasPassengers()
                && !DrakeSoulbinding.ritualActive(player) && !(DrakeLeashing.holder(player) instanceof net.minecraft.entity.LivingEntity)
                && (temporary(player) || EarthenDrake.stage(player) == 3);
    }

    public static void begin(ServerPlayerEntity player, int ticks) {
        var claim = DrakeOutpostOwnership.claim(player);
        if (!canTakeOver(player, claim)) return;
        var control = ((State)player).sscExtras$feralRuntime();
        control.ticks = net.minecraft.util.math.MathHelper.clamp(ticks, MIN_CONTROL_TICKS, MAX_CONTROL_TICKS);
        control.brain = new FeralDrakeBrain(player);
        player.clearActiveItem();
        player.closeHandledScreen();
        player.setSprinting(false); player.setSneaking(false);
        ((State)player).sscExtras$feralControl(true);
        player.sendMessage(Text.translatable("message.ssc-extras.drake.feral_takeover"), true);
    }

    static void stop(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        if (claim != null) claim.returning = false;
        var control = ((State)player).sscExtras$feralRuntime();
        control.ticks = 0;
        control.brain = null;
        control.next = nextTakeover(player);
        if (controlled(player)) {
            player.setJumping(false); player.setSprinting(false);
            player.setVelocity(Vec3d.ZERO);
        }
        ((State)player).sscExtras$feralControl(false);
    }

    public static Vec3d movement(PlayerEntity player) {
        if (!controlled(player) || player.getWorld().isClient) return Vec3d.ZERO;
        var control = ((State)player).sscExtras$feralRuntime();
        return control.brain == null ? Vec3d.ZERO : control.brain.movement();
    }

    public static void tick(ServerPlayerEntity player) {
        var claim = DrakeOutpostOwnership.claim(player);
        sync(player, claim);
        var control = ((State)player).sscExtras$feralRuntime();
        if (claim != null && DrakeRoaming.mustReturn(player, claim)) {
            if (!claim.returning || control.brain == null || control.brain.getWorld() != player.getWorld()) {
                claim.returning = true;
                control.brain = new FeralDrakeBrain(player, true);
                player.setSprinting(false); player.setSneaking(false);
                ((State)player).sscExtras$feralControl(true);
                player.sendMessage(Text.translatable("message.ssc-extras.drake.bound_return"), true);
            }
            control.brain.think();
            return;
        }
        if (claim != null && claim.returning) { stop(player, claim); return; }
        if (!canTakeOver(player, claim)) {
            if (controlled(player)) stop(player, claim);
            return;
        }
        if (!controlled(player)) {
            if (control.next == 0 || temporary(player) && control.next > 400) control.next = nextTakeover(player);
            if (--control.next > 0) return;
            begin(player, MIN_CONTROL_TICKS + player.getRandom().nextInt(MAX_CONTROL_TICKS - MIN_CONTROL_TICKS + 1));
        }
        if (control.ticks-- <= 0 || control.brain == null || control.brain.getWorld() != player.getWorld()) {
            stop(player, claim); return;
        }
        control.brain.think();
    }

    private static int nextTakeover(PlayerEntity player) {
        return temporary(player) ? 200 + player.getRandom().nextInt(201) : 1200 + player.getRandom().nextInt(4801);
    }

    public static void register() {
        BeastizationCatalyst.register();
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> restricted(player) && controlled(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> useBlock(player, hit.getBlockPos()));
        UseItemCallback.EVENT.register((player, world, hand) -> restricted(player) && (controlled(player) || !edible(player.getStackInHand(hand)))
                ? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand)));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> restricted(player) ? ActionResult.FAIL : ActionResult.PASS);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> restricted(player)
                && (controlled(player) || !player.getMainHandStack().isEmpty()) ? ActionResult.FAIL : ActionResult.PASS);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player, DrakeOutpostOwnership.claim(handler.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var claim = DrakeOutpostOwnership.claim(handler.player);
            stop(handler.player, claim);
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> server.getPlayerManager().getPlayerList().forEach(DrakeFeralization::tick));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerManager().getPlayerList()) if (controlled(player)) {
                player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
                player.getServerWorld().getChunkManager().updatePosition(player);
            }
        });
    }
}
