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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import sscextras.player.*;

public final class DrakeFeralization {
    public static final int ESCAPES = 6, MIN_CONTROL_TICKS = 100, MAX_CONTROL_TICKS = 600;
    public static final TagKey<net.minecraft.item.Item> RAW_FOOD = TagKey.of(RegistryKeys.ITEM, EarthenDrake.id("drake_raw_food"));
    public static final String TAKEOVER_TEXT_MARKER = "ssc-extras:feral_takeover";
    private static final Identifier FERAL_CONTROL = EarthenDrake.id("feral"), RETURN_CONTROL = EarthenDrake.id("bound_return");

    public static final class Control {
        int next;
        Identifier form;
    }

    public interface State {
        Control sscExtras$feralRuntime();
        boolean sscExtras$feral();
        void sscExtras$feral(boolean value);
        void sscExtras$refreshFeralDimensions(net.minecraft.entity.data.TrackedData<?> data);
        int sscExtras$sentience();
        void sscExtras$sentience(int value);
        boolean sscExtras$feralControl();
        void sscExtras$feralControl(boolean value);
    }

    private DrakeFeralization() { }

    public static boolean due(DrakeOutpostOwnership.Claim claim) { return claim.escapes >= ESCAPES && !claim.feral; }
    public static boolean rawFood(ItemStack stack) { return stack.isFood() && stack.isIn(RAW_FOOD); }
    public static boolean permanent(PlayerEntity player) {
        if (((State)player).sscExtras$feral()) return true;
        if (player.getWorld().isClient) return false;
        var claim = DrakeOutpostOwnership.claim(player);
        return claim != null && claim.feral;
    }
    public static boolean feral(PlayerEntity player) { return permanent(player) || PlayerMind.feral(player); }
    public static boolean temporary(PlayerEntity player) { return player.hasStatusEffect(BeastizationCatalyst.TOTAL_FERALIZED); }
    public static boolean edible(ItemStack stack) { return stack.isFood(); }
    public static boolean restricted(PlayerEntity player) { return PlayerMind.feral(player) && !player.isCreative() && !player.isSpectator(); }
    public static boolean wantsHeldFood(PlayerEntity player) { return restricted(player) && player.canConsume(false); }
    public static boolean controlled(PlayerEntity player) { return PlayerControl.active(player); }

    public static boolean forcedQuadruped(PlayerEntity player) {
        return sscextras.player.PlayerStance.genericQuadruped(player);
    }

    public static boolean carriesInMouth(PlayerEntity player) {
        return sscextras.player.PlayerMouth.active(player);
    }

    public static int recovery(PlayerEntity player) { return permanent(player) ? ((State)player).sscExtras$sentience() : 0; }
    public static boolean blocksRestricted(PlayerEntity player) {
        return restricted(player) && (PlayerMind.enabled(player) || controlled(player) || recovery(player) < 1);
    }
    public static boolean itemsRestricted(PlayerEntity player) { return restricted(player) && (PlayerMind.enabled(player) || recovery(player) < 2); }
    public static boolean mindRestricted(PlayerEntity player) { return restricted(player) && (PlayerMind.enabled(player) || recovery(player) < 3); }
    public static boolean canUse(PlayerEntity player, ItemStack stack) {
        return !controlled(player) && PlayerMind.allow(player, MentalContext.item(MentalContext.Action.USE, stack))
                && (!itemsRestricted(player) || stack.isEmpty() || edible(stack) || MentalRequirements.morphscaleArmor(stack)
                    || !PlayerMind.enabled(player) && recovery(player) >= 1 && stack.getItem() instanceof net.minecraft.item.BlockItem);
    }
    public static boolean blocksAttack(PlayerEntity player) {
        return controlled(player) || !PlayerMind.allowHeldTool(player)
                || !PlayerMind.enabled(player) && itemsRestricted(player) && !player.getMainHandStack().isEmpty();
    }

    public static void fullyFeralize(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity serverPlayer) PlayerMind.feralize(serverPlayer);
        var state = (State)player;
        state.sscExtras$feral(true);
        state.sscExtras$sentience(0);
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim != null) {
            claim.feral = true;
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
        }
    }

    public static void copyMind(PlayerEntity oldPlayer, PlayerEntity newPlayer) {
        PlayerMind.copy(oldPlayer, newPlayer);
        ((State)newPlayer).sscExtras$feral(permanent(oldPlayer));
        ((State)newPlayer).sscExtras$sentience(recovery(oldPlayer));
    }

    public static void afterRespawn(ServerPlayerEntity player, boolean alive) {
        if (alive || !feral(player)) return;
        var form = FormAbilityManager.getForm(player);
        if (form == net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.ORIGINAL_SHIFTER
                || form == net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.ORIGINAL_BEFORE_ENABLE)
            DrakeSoulbinding.clearFeralization(player);
    }

    static void recover(ServerPlayerEntity player) {
        player.removeStatusEffect(BeastizationCatalyst.TOTAL_FERALIZED);
        PlayerMind.gainPoints(player, 100);
        if (permanent(player)) {
            var state = (State)player;
            state.sscExtras$feral(true);
            int recovered = recovery(player) + 1;
            if (recovered >= 4) {
                DrakeSoulbinding.clearFeralization(player, false);
                if (!PlayerMind.enabled(player)) player.sendMessage(SentientCatalyst.hint(player, 4), false);
                return;
            }
            state.sscExtras$sentience(recovered);
            if (!PlayerMind.enabled(player)) player.sendMessage(SentientCatalyst.hint(player, recovered), false);
        }
        player.removeStatusEffect(BeastizationCatalyst.TOTAL_FERALIZED);
        PlayerMind.refresh(player);
        var claim = DrakeOutpostOwnership.claim(player);
        if (!mindRestricted(player) && (claim == null || !claim.returning)) stop(player, claim);
    }

    static void relapse(PlayerEntity player) {
        if (permanent(player)) {
            ((State)player).sscExtras$sentience(Math.max(0, recovery(player) - 1));
        }
    }

    static void sync(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        // Import existing saves; releasing a stable or reverting the body cannot cure the mind.
        if (claim != null && claim.feral) {
            ((State)player).sscExtras$feral(true);
            PlayerMind.importLegacy(player, recovery(player));
        }
        if (claim != null && !claim.feral && permanent(player)) {
            claim.feral = true;
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
        }
        if (!restricted(player) && (claim == null || !claim.returning) && player instanceof ServerPlayerEntity serverPlayer
                && PlayerControl.owns(serverPlayer, FERAL_CONTROL)) stop(player, claim);
    }

    public static ActionResult useBlock(PlayerEntity player, net.minecraft.util.math.BlockPos pos) {
        if (!blocksRestricted(player)) return PlayerMind.allow(player, MentalContext.block(MentalContext.Action.INTERACT, player.getWorld(), pos)) ? ActionResult.PASS : ActionResult.FAIL;
        if (!controlled(player) && player.getWorld().getBlockState(pos).isOf(Blocks.HAY_BLOCK)) {
            if (player instanceof ServerPlayerEntity serverPlayer) DrakeHaySleep.sleep(serverPlayer, pos);
            return ActionResult.SUCCESS;
        }
        return ActionResult.FAIL;
    }

    public static boolean allowInventoryClick(PlayerEntity player, int slot, int button, SlotActionType action) {
        if (!PlayerMind.allowEquipmentClick(player, slot, button, action)) return false;
        if (controlled(player)) return false;
        if (!restricted(player)) return true;
        if (!PlayerMind.enabled(player) && (recovery(player) >= 2
                || recovery(player) >= 1 && player.currentScreenHandler != player.playerScreenHandler)) return true;
        if (player.currentScreenHandler != player.playerScreenHandler) return false;
        if (slot == -999) return action == SlotActionType.PICKUP;
        if (slot >= 5 && slot <= 8) return MentalRequirements.morphscaleArmor(player.currentScreenHandler.getCursorStack())
                || MentalRequirements.morphscaleArmor(player.currentScreenHandler.getSlot(slot).getStack());
        if (slot < 9 || slot > 45) return false;
        var handler = player.playerScreenHandler;
        var stack = handler.getSlot(slot).getStack();
        if (action == SlotActionType.SWAP) {
            return button == 40 || button >= 0 && button <= 8;
        }
        return action == SlotActionType.PICKUP || action == SlotActionType.THROW
                || action == SlotActionType.QUICK_MOVE && (edible(stack) || MentalRequirements.morphscaleArmor(stack)
                    || !PlayerMind.enabled(player) && recovery(player) >= 1 && stack.getItem() instanceof net.minecraft.item.BlockItem);
    }

    public static ActionResult eatAtBlock(PlayerEntity player, net.minecraft.util.Hand hand) {
        if ((!blocksRestricted(player) && !DrakeShoes.restricted(player)) || controlled(player)
                || DrakeSoulbinding.restrained(player) || !DrakeShoes.canUse(player, hand)) return ActionResult.PASS;
        var stack = player.getStackInHand(hand);
        if (!edible(stack) || !canUse(player, stack)) return ActionResult.PASS;
        var result = stack.use(player.getWorld(), player, hand).getResult();
        // Fabric sends the block-use packet only for SUCCESS on the client.
        return result.isAccepted() ? ActionResult.success(player.getWorld().isClient) : result;
    }

    private static boolean canTakeOver(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        return mindRestricted(player) && PlayerControl.available(player) && (claim == null || !claim.awaitingRespawn)
                && !player.isSleeping() && !player.hasVehicle() && !player.hasPassengers()
                && !DrakeSoulbinding.ritualActive(player) && !(DrakeLeashing.holder(player) instanceof net.minecraft.entity.LivingEntity);
    }

    public static void begin(ServerPlayerEntity player, int ticks) {
        var claim = DrakeOutpostOwnership.claim(player);
        sync(player, claim);
        if (!canTakeOver(player, claim)) return;
        var control = ((State)player).sscExtras$feralRuntime();
        int duration = net.minecraft.util.math.MathHelper.clamp(ticks, MIN_CONTROL_TICKS, MAX_CONTROL_TICKS);
        // Negative INT adds 20% per 100 points, capped at +200%; cadence is unchanged.
        double multiplier = PlayerMind.enabled(player) ? 1 + Math.min(2, Math.max(0, -PlayerMind.points(player)) / 500) : 1;
        if (!selectBrain(player, control, (int)Math.round(duration * multiplier))) return;
        player.sendMessage(takeoverMessage(player), true);
    }

    public static Text takeoverMessage(PlayerEntity player) {
        var form = FormAbilityManager.getForm(player);
        return Text.translatable("message.ssc-extras.drake.feral_takeover", BrainProfiles.resolve(form).name().apply(form))
                .styled(style -> style.withInsertion(TAKEOVER_TEXT_MARKER));
    }

    private static boolean selectBrain(ServerPlayerEntity player, Control control, int ticks) {
        PlayerControl.stop(player, FERAL_CONTROL);
        var form = FormAbilityManager.getForm(player);
        var profile = BrainProfiles.resolve(form);
        control.form = form.FormID;
        return PlayerControl.tryStart(player, FERAL_CONTROL, () -> profile.factory().apply(player, form), Math.max(1, ticks)) != null;
    }

    public static void formChanged(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        PlayerStance.formChanged(serverPlayer);
        PlayerNames.refresh(serverPlayer);
        if (!controlled(player)) return;
        var claim = DrakeOutpostOwnership.claim(player);
        var control = ((State)player).sscExtras$feralRuntime();
        if (claim != null && claim.returning || FormAbilityManager.getForm(player).FormID.equals(control.form)) return;
        if (!PlayerControl.owns(serverPlayer, FERAL_CONTROL)) { PlayerControl.clear(serverPlayer); return; }
        selectBrain(serverPlayer, control, PlayerControl.current(serverPlayer).remainingTicks());
        player.sendMessage(takeoverMessage(player), true);
    }

    static void stop(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        if (claim != null) claim.returning = false;
        var control = ((State)player).sscExtras$feralRuntime();
        if (player instanceof ServerPlayerEntity serverPlayer) {
            PlayerControl.stop(serverPlayer, FERAL_CONTROL);
            PlayerControl.stop(serverPlayer, RETURN_CONTROL);
        }
        control.form = null;
        control.next = nextTakeover(player);
    }

    public static Vec3d movement(PlayerEntity player) {
        return PlayerControl.movement(player);
    }

    public static void tick(ServerPlayerEntity player) {
        sscextras.player.MentalProgression.tick(player);
        PlayerMind.refresh(player);
        tickPolicy(player);
        if (wantsHeldFood(player)) FeralForageGoal.eatHeldFood(player);
        boolean wasFeral = PlayerControl.owns(player, FERAL_CONTROL);
        PlayerControl.tick(player);
        if (wasFeral && !PlayerControl.owns(player, FERAL_CONTROL)) ((State)player).sscExtras$feralRuntime().next = nextTakeover(player);
    }

    private static void tickPolicy(ServerPlayerEntity player) {
        var claim = DrakeOutpostOwnership.claim(player);
        sync(player, claim);
        var control = ((State)player).sscExtras$feralRuntime();
        boolean outside = claim != null && DrakeRoaming.mustReturn(player, claim);
        if (outside && DrakeBoundReturn.ready(player, claim)) {
            if (!PlayerControl.owns(player, RETURN_CONTROL)) {
                PlayerControl.stop(player, FERAL_CONTROL);
                if (PlayerControl.tryStart(player, RETURN_CONTROL, () -> new FeralDrakeBrain(player, true), 0) == null) return;
                claim.returning = true;
                player.sendMessage(Text.translatable("message.ssc-extras.drake.bound_return"), true);
            }
            if (DrakeBoundReturn.tick(player, claim)) return;
            return;
        }
        if (claim != null && !outside) DrakeBoundReturn.clear(claim);
        if (claim != null && claim.returning) { stop(player, claim); return; }
        if (!canTakeOver(player, claim)) {
            if (PlayerControl.owns(player, FERAL_CONTROL)) stop(player, claim);
            return;
        }
        if (!controlled(player)) {
            if (control.next == 0 || temporary(player) && control.next > 400) control.next = nextTakeover(player);
            if (--control.next > 0) return;
            begin(player, MIN_CONTROL_TICKS + player.getRandom().nextInt(MAX_CONTROL_TICKS - MIN_CONTROL_TICKS + 1));
        }
    }

    private static int nextTakeover(PlayerEntity player) {
        return temporary(player) ? 200 + player.getRandom().nextInt(201) : 1200 + player.getRandom().nextInt(4801);
    }

    public static void register() {
        BrainProfiles.fallback(new BrainProfiles.Profile((player, form) -> {
            var profile = FeralForm.resolve(form);
            return profile.drake() ? new FeralDrakeBrain(player) : new FeralMobBrain(player, profile);
        }, form -> FeralForm.resolve(form).name()));
        BeastizationCatalyst.register();
        SentientCatalyst.register();
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> controlled(player) || !PlayerMind.allowHeldTool(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            var eating = eatAtBlock(player, hand);
            return eating != ActionResult.PASS ? eating : useBlock(player, hit.getBlockPos());
        });
        UseItemCallback.EVENT.register((player, world, hand) -> !canUse(player, player.getStackInHand(hand))
                ? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand)));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> useEntity(player, entity, hand));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> blocksAttack(player) ? ActionResult.FAIL : ActionResult.PASS);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player, DrakeOutpostOwnership.claim(handler.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var claim = DrakeOutpostOwnership.claim(handler.player);
            stop(handler.player, claim);
            PlayerControl.clear(handler.player);
            PlayerStance.clear(handler.player);
            if (claim != null) DrakeBoundReturn.clear(claim);
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> server.getPlayerManager().getPlayerList().forEach(DrakeFeralization::tick));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerManager().getPlayerList()) if (controlled(player)) {
                PlayerControl.syncPosition(player);
                player.getServerWorld().getChunkManager().updatePosition(player);
            }
        });
    }

    public static ActionResult useEntity(PlayerEntity player, net.minecraft.entity.Entity entity, net.minecraft.util.Hand hand) {
        if (!restricted(player)) return PlayerMind.allow(player, MentalContext.entity(entity))
                && PlayerMind.allow(player, MentalContext.item(MentalContext.Action.USE, player.getStackInHand(hand))) ? ActionResult.PASS : ActionResult.FAIL;
        if (entity instanceof net.minecraft.entity.passive.VillagerEntity villager) {
            if (!player.getWorld().isClient && hand == net.minecraft.util.Hand.MAIN_HAND && villager.isAlive()) {
                villager.setHeadRollingTimeLeft(40);
                villager.playSound(net.minecraft.sound.SoundEvents.ENTITY_VILLAGER_NO, 1, 1);
            }
            return ActionResult.success(player.getWorld().isClient);
        }
        return controlled(player) || itemsRestricted(player) ? ActionResult.FAIL : ActionResult.PASS;
    }
}
