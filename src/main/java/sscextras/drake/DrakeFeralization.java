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

public final class DrakeFeralization {
    public static final int ESCAPES = 6, MIN_CONTROL_TICKS = 100, MAX_CONTROL_TICKS = 600;
    public static final TagKey<net.minecraft.item.Item> RAW_FOOD = TagKey.of(RegistryKeys.ITEM, EarthenDrake.id("drake_raw_food"));
    public static final String TAKEOVER_TEXT_MARKER = "ssc-extras:feral_takeover";

    public static final class Control {
        int ticks, next;
        FeralBrain brain;
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
    public static boolean feral(PlayerEntity player) { return permanent(player) || temporary(player); }
    public static boolean temporary(PlayerEntity player) { return player.hasStatusEffect(BeastizationCatalyst.TOTAL_FERALIZED); }
    public static boolean edible(ItemStack stack) { return stack.isFood(); }
    public static boolean restricted(PlayerEntity player) { return feral(player) && !player.isCreative() && !player.isSpectator(); }
    public static boolean controlled(PlayerEntity player) { return ((State)player).sscExtras$feralControl(); }

    public static boolean forcedQuadruped(PlayerEntity player) {
        return ((State)player).sscExtras$feral() && EarthenDrake.stage(player) < 0
                && FormAbilityManager.getForm(player).getBodyType()
                    != net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType.FERAL;
    }

    public static boolean carriesInMouth(PlayerEntity player) {
        return permanent(player) || temporary(player) && EarthenDrake.stage(player) >= 0 || DrakeShoes.hands(player);
    }

    public static int recovery(PlayerEntity player) { return permanent(player) ? ((State)player).sscExtras$sentience() : 0; }
    public static boolean blocksRestricted(PlayerEntity player) { return restricted(player) && (controlled(player) || recovery(player) < 1); }
    public static boolean itemsRestricted(PlayerEntity player) { return restricted(player) && recovery(player) < 2; }
    public static boolean mindRestricted(PlayerEntity player) { return restricted(player) && recovery(player) < 3; }
    public static boolean canUse(PlayerEntity player, ItemStack stack) {
        return !restricted(player) || !controlled(player) && (!itemsRestricted(player) || edible(stack)
                || recovery(player) >= 1 && stack.getItem() instanceof net.minecraft.item.BlockItem);
    }
    public static boolean blocksAttack(PlayerEntity player) {
        return restricted(player) && (controlled(player) || itemsRestricted(player) && !player.getMainHandStack().isEmpty());
    }

    public static void fullyFeralize(PlayerEntity player) {
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
        if (permanent(player)) {
            var state = (State)player;
            state.sscExtras$feral(true);
            int recovered = recovery(player) + 1;
            if (recovered >= 4) {
                DrakeSoulbinding.clearFeralization(player);
                player.sendMessage(SentientCatalyst.hint(4), false);
                return;
            }
            state.sscExtras$sentience(recovered);
            player.sendMessage(SentientCatalyst.hint(recovered), false);
        }
        player.removeStatusEffect(BeastizationCatalyst.TOTAL_FERALIZED);
        var claim = DrakeOutpostOwnership.claim(player);
        if (!mindRestricted(player) && (claim == null || !claim.returning)) stop(player, claim);
    }

    static void relapse(PlayerEntity player) {
        if (permanent(player)) ((State)player).sscExtras$sentience(Math.max(0, recovery(player) - 1));
    }

    static void sync(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        // Import existing saves; releasing a stable or reverting the body cannot cure the mind.
        if (claim != null && claim.feral) ((State)player).sscExtras$feral(true);
        if (claim != null && !claim.feral && permanent(player)) {
            claim.feral = true;
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
        }
        if (!feral(player) && (claim == null || !claim.returning) && controlled(player)) stop(player, claim);
    }

    public static ActionResult useBlock(PlayerEntity player, net.minecraft.util.math.BlockPos pos) {
        if (!blocksRestricted(player)) return ActionResult.PASS;
        if (!controlled(player) && player.getWorld().getBlockState(pos).isOf(Blocks.HAY_BLOCK)) {
            if (player instanceof ServerPlayerEntity serverPlayer) DrakeHaySleep.sleep(serverPlayer, pos);
            return ActionResult.SUCCESS;
        }
        return ActionResult.FAIL;
    }

    public static boolean allowInventoryClick(PlayerEntity player, int slot, int button, SlotActionType action) {
        if (!restricted(player)) return true;
        if (controlled(player)) return false;
        if (recovery(player) >= 2 || recovery(player) >= 1 && player.currentScreenHandler != player.playerScreenHandler) return true;
        if (player.currentScreenHandler != player.playerScreenHandler) return false;
        if (slot == -999) return action == SlotActionType.PICKUP;
        if (slot < 9 || slot > 45) return false;
        var handler = player.playerScreenHandler;
        var stack = handler.getSlot(slot).getStack();
        if (action == SlotActionType.SWAP) {
            return button == 40 || button >= 0 && button <= 8;
        }
        return action == SlotActionType.PICKUP || action == SlotActionType.THROW
                || action == SlotActionType.QUICK_MOVE && (edible(stack) || recovery(player) >= 1 && stack.getItem() instanceof net.minecraft.item.BlockItem);
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
        return mindRestricted(player) && player.isAlive() && (claim == null || !claim.awaitingRespawn)
                && !player.isSleeping() && !player.hasVehicle() && !player.hasPassengers()
                && !DrakeSoulbinding.ritualActive(player) && !(DrakeLeashing.holder(player) instanceof net.minecraft.entity.LivingEntity);
    }

    public static void begin(ServerPlayerEntity player, int ticks) {
        var claim = DrakeOutpostOwnership.claim(player);
        if (!canTakeOver(player, claim)) return;
        var control = ((State)player).sscExtras$feralRuntime();
        control.ticks = net.minecraft.util.math.MathHelper.clamp(ticks, MIN_CONTROL_TICKS, MAX_CONTROL_TICKS);
        selectBrain(player, control);
        player.clearActiveItem();
        player.closeHandledScreen();
        player.setSprinting(false); player.setSneaking(false);
        ((State)player).sscExtras$feralControl(true);
        player.sendMessage(takeoverMessage(player), true);
    }

    public static Text takeoverMessage(PlayerEntity player) {
        return Text.translatable("message.ssc-extras.drake.feral_takeover", FeralForm.resolve(FormAbilityManager.getForm(player)).name())
                .styled(style -> style.withInsertion(TAKEOVER_TEXT_MARKER));
    }

    private static void selectBrain(ServerPlayerEntity player, Control control) {
        if (control.brain != null) control.brain.stop();
        var form = FormAbilityManager.getForm(player);
        var profile = FeralForm.resolve(form);
        control.form = form.FormID;
        control.brain = profile.drake() ? new FeralDrakeBrain(player) : new FeralMobBrain(player, profile);
    }

    public static void formChanged(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !controlled(player)) return;
        var claim = DrakeOutpostOwnership.claim(player);
        var control = ((State)player).sscExtras$feralRuntime();
        if (claim != null && claim.returning || FormAbilityManager.getForm(player).FormID.equals(control.form)) return;
        selectBrain(serverPlayer, control);
        player.sendMessage(takeoverMessage(player), true);
    }

    static void stop(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        if (claim != null) claim.returning = false;
        var control = ((State)player).sscExtras$feralRuntime();
        control.ticks = 0;
        if (control.brain != null) control.brain.stop();
        control.brain = null;
        control.form = null;
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
            if (!claim.returning || control.brain == null || control.brain.world() != player.getWorld()) {
                claim.returning = true;
                if (control.brain != null) control.brain.stop();
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
        if (control.ticks-- <= 0 || control.brain == null || control.brain.world() != player.getWorld()) {
            stop(player, claim); return;
        }
        control.brain.think();
    }

    private static int nextTakeover(PlayerEntity player) {
        return temporary(player) ? 200 + player.getRandom().nextInt(201) : 1200 + player.getRandom().nextInt(4801);
    }

    public static void register() {
        BeastizationCatalyst.register();
        SentientCatalyst.register();
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> restricted(player) && controlled(player) ? ActionResult.FAIL : ActionResult.PASS);
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
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> server.getPlayerManager().getPlayerList().forEach(DrakeFeralization::tick));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerManager().getPlayerList()) if (controlled(player)) {
                player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
                player.getServerWorld().getChunkManager().updatePosition(player);
            }
        });
    }

    public static ActionResult useEntity(PlayerEntity player, net.minecraft.entity.Entity entity, net.minecraft.util.Hand hand) {
        if (!restricted(player)) return ActionResult.PASS;
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
