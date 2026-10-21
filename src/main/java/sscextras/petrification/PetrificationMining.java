package sscextras.petrification;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.*;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.*;
import net.minecraft.util.*;
import net.minecraft.util.math.MathHelper;

public final class PetrificationMining {
    public static final Identifier INTENT = Petrification.id("mine_statue");
    public static final RegistryKey<DamageType> SHATTER = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Petrification.id("shatter"));
    private PetrificationMining() { }

    public static boolean mineable(PlayerEntity player, Entity target) {
        return target instanceof LivingEntity living && sscextras.player.StoneBodies.mineable(living) && player.getMainHandStack().isIn(ItemTags.PICKAXES);
    }
    /** A native death hook may destroy a body while preserving its soul (for example a Guardian core). */
    public static void bodyBroken(LivingEntity target) {
        var state = Petrification.state(target);
        if (state.shattering) state.shatteredBody = true;
    }
    public static void register() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stop(handler.player));
        ServerPlayNetworking.registerGlobalReceiver(INTENT, (server, player, handler, buffer, sender) -> {
            int target = buffer.readVarInt();
            server.execute(() -> intent(player, target));
        });
    }
    public static void intent(ServerPlayerEntity player, int id) {
        var state = Petrification.state(player);
        if (!(player.getWorld().getEntityById(id) instanceof LivingEntity target) || !canMine(player, target)) {
            stop(player); return;
        }
        if (state.miningTarget != target || !ItemStack.areEqual(state.miningTool, player.getMainHandStack())) {
            stop(player);
            state.miningTarget = target; state.miningTool = player.getMainHandStack().copy(); state.miningProgress = 0;
        }
        state.miningSignal = player.getWorld().getTime();
    }
    public static void stop(ServerPlayerEntity player) {
        var state = Petrification.state(player);
        if (state.miningTarget != null) Petrification.state(state.miningTarget).clearCracks(player.getUuid());
        state.miningTarget = null; state.miningTool = ItemStack.EMPTY; state.miningProgress = 0;
    }
    public static float progress(ServerPlayerEntity player) { return Petrification.state(player).miningProgress; }
    public static float speed(PlayerEntity player, LivingEntity target) {
        return Blocks.STONE.getDefaultState().calcBlockBreakingDelta(player, player.getWorld(), target.getBlockPos());
    }
    public static int drops(LivingEntity target) { return Math.max(1, MathHelper.ceil(target.getMaxHealth() / 10f)); }
    private static boolean canMine(ServerPlayerEntity player, LivingEntity target) {
        if (!player.isAlive() || player.isSpectator() || player.isUsingItem() || !player.getAbilities().allowModifyWorld
                || Petrification.frozen(player) || !mineable(player, target) || target.getWorld() != player.getWorld()
                || target instanceof PlayerEntity other && (other.isCreative() || other.isSpectator()
                || !player.getServer().isPvpEnabled() || !player.shouldDamagePlayer(other))) return false;
        double reach = player.isCreative() ? 6 : 3;
        var eye = player.getEyePos();
        var end = eye.add(player.getRotationVec(1).multiply(reach));
        double distance = Math.min(reach * reach, player.raycast(reach, 1, false).getPos().squaredDistanceTo(eye));
        var hit = ProjectileUtil.raycast(player, eye, end, player.getBoundingBox().stretch(end.subtract(eye)).expand(1),
                entity -> entity.canHit() && !entity.isSpectator(), distance);
        if (hit == null || hit.getEntity() != target) return false;
        var state = Petrification.state(player);
        state.miningPermissionCheck = true;
        try {
            return AttackEntityCallback.EVENT.invoker().interact(player, player.getWorld(), Hand.MAIN_HAND, target, hit) == ActionResult.PASS;
        } finally { state.miningPermissionCheck = false; }
    }
    public static void tick(ServerPlayerEntity player) {
        var state = Petrification.state(player);
        var target = state.miningTarget;
        if (target == null) return;
        if (player.getWorld().getTime() - state.miningSignal > 7 || !ItemStack.areEqual(state.miningTool, player.getMainHandStack()) || !canMine(player, target)) {
            stop(player); return;
        }
        state.miningProgress += speed(player, target);
        Petrification.state(target).miningCracks(player.getUuid(), state.miningProgress);
        if (player.getWorld().getTime() % 4 == 0) {
            player.getServerWorld().playSound(null, target.getBlockPos(), SoundEvents.BLOCK_STONE_HIT, SoundCategory.BLOCKS, .6f, 1);
            player.getServerWorld().spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                    target.getX(), target.getBodyY(.5), target.getZ(), 3, target.getWidth() * .3, target.getHeight() * .3, target.getWidth() * .3, .02);
        }
        if (state.miningProgress >= 1) {
            shatter(player, target);
            stop(player);
        }
    }
    private static void shatter(ServerPlayerEntity player, LivingEntity target) {
        var state = Petrification.state(target);
        int count = drops(target);
        boolean silk = EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, player.getMainHandStack()) > 0;
        var damage = new DamageSource(player.getWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SHATTER), player);
        state.shattering = true; state.shatteredBody = false;
        boolean broken;
        try { target.damage(damage, Math.max(1024, target.getMaxHealth() * 1024)); }
        finally { state.shattering = false; broken = state.shatteredBody; state.shatteredBody = false; }
        if (!target.isDead() && !broken) return;
        if (player.getWorld().getGameRules().getBoolean(net.minecraft.world.GameRules.DO_MOB_LOOT)) {
            while (count > 0) {
                int amount = Math.min(64, count); count -= amount;
                target.dropStack(new ItemStack(silk ? Items.STONE : Items.COBBLESTONE, amount));
            }
        }
        player.getMainHandStack().damage(1, player, p -> p.sendToolBreakStatus(Hand.MAIN_HAND));
        player.getServerWorld().spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                target.getX(), target.getBodyY(.5), target.getZ(), 40, target.getWidth() * .5, target.getHeight() * .5, target.getWidth() * .5, .1);
        if (!(target instanceof PlayerEntity)) target.remove(Entity.RemovalReason.KILLED);
    }
}
