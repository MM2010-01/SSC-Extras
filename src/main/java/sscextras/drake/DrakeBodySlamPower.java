package sscextras.drake;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Active;
import io.github.apace100.apoli.power.CooldownPower;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.util.HudRender;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** The owner moves through vanilla travel; the server owns the impact, cost and cooldown. */
public final class DrakeBodySlamPower extends CooldownPower implements Active {
    public static final double DISTANCE = 3, SPEED = .6;
    public static final int HUNGER_COST = 2, COOLDOWN = 100;
    public static final RegistryKey<DamageType> DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, EarthenDrake.id("body_slam"));
    private Active.Key key = new Active.Key();
    private boolean rushing, stopAfterTravel;
    private Vec3d start = Vec3d.ZERO, previous = Vec3d.ZERO, direction = Vec3d.ZERO;
    private RegistryKey<World> rushWorld;

    public DrakeBodySlamPower(PowerType<?> type, LivingEntity entity) {
        super(type, entity, COOLDOWN, new HudRender(true, 4, new Identifier("origins", "textures/gui/resource_bar.png"), null, false));
        key.key = "key.origins.primary_active";
        lastUseTime = -COOLDOWN;
        setTicking(true);
    }

    @Override public Active.Key getKey() { return key; }
    @Override public void setKey(Active.Key key) { this.key = key; }
    @Override public boolean shouldTick() { return rushing; }
    public boolean isRushing() { return rushing; }

    @Override public boolean canUse() {
        return super.canUse() && !rushing && entity instanceof PlayerEntity player && eligible(player)
                && player.getHungerManager().getFoodLevel() >= HUNGER_COST;
    }

    private boolean eligible(PlayerEntity player) {
        return player.isAlive() && !player.isSpectator() && !player.hasVehicle() && !player.isSleeping()
                && EarthenDrake.stage(player) == 3 && isActive();
    }

    @Override public void onUse() {
        if (!(entity instanceof ServerPlayerEntity player) || !canUse()) return;
        start = previous = player.getPos();
        float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        direction = new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
        rushWorld = player.getWorld().getRegistryKey();
        rushing = true;
        var hunger = player.getHungerManager();
        hunger.setFoodLevel(hunger.getFoodLevel() - HUNGER_COST);
        hunger.setSaturationLevel(Math.min(hunger.getSaturationLevel(), hunger.getFoodLevel()));
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GOAT_LONG_JUMP,
                player.getSoundCategory(), .8f, .7f);
        use();
    }

    public static DrakeBodySlamPower get(PlayerEntity player) {
        return PowerHolderComponent.getPowers(player, DrakeBodySlamPower.class).stream().findFirst().orElse(null);
    }

    public static Vec3d movement(PlayerEntity player, Vec3d input) {
        if (!player.isMainPlayer()) return null;
        var power = get(player);
        if (power == null || !power.rushing) return null;
        if (!power.eligible(player)) { power.stop(); return null; }
        double remaining = Math.max(0, DISTANCE - player.getPos().subtract(power.start).horizontalLength());
        Vec3d step = power.direction.multiply(Math.min(SPEED, remaining));
        Vec3d allowed = power.allowedMovement(player, step);
        Contact contact = power.contact(player, player.getPos(), player.getPos().add(allowed));
        power.stopAfterTravel = remaining <= SPEED + 1.0E-5 || allowed.subtract(step).horizontalLengthSquared() > 1.0E-8;
        if (contact != null) {
            allowed = contact.position().subtract(player.getPos());
            power.stopAfterTravel = true;
        }
        player.setVelocity(allowed.x, player.getVelocity().y, allowed.z);
        return new Vec3d(0, input.y, 0);
    }

    public static void afterTravel(PlayerEntity player) {
        if (!player.isMainPlayer()) return;
        var power = get(player);
        if (power != null && power.rushing && (power.stopAfterTravel || player.horizontalCollision)) power.stop();
    }

    private Vec3d allowedMovement(PlayerEntity player, Vec3d step) {
        return Entity.adjustMovementForCollisions(player, step, player.getBoundingBox(), player.getWorld(),
                player.getWorld().getEntityCollisions(player, player.getBoundingBox().stretch(step)));
    }

    @Override public void tick() {
        if (!rushing || !(entity instanceof ServerPlayerEntity player)) return;
        if (!eligible(player) || player.getWorld().getRegistryKey() != rushWorld
                || player.getWorld().getTime() - lastUseTime > 40) { finish(player, false); return; }
        Vec3d current = player.getPos();
        Contact contact = contact(player, previous, current.add(direction.multiply(.03)));
        boolean atLimit = current.subtract(start).horizontalLength() >= DISTANCE - 1.0E-4;
        boolean blocked = allowedMovement(player, direction.multiply(.03)).horizontalLength() < .029;
        if (contact != null || atLimit || blocked) {
            Vec3d end = current;
            if (contact != null && contact.position().squaredDistanceTo(previous) < current.squaredDistanceTo(previous))
                end = contact.position();
            if (end.subtract(start).horizontalLength() > DISTANCE)
                end = new Vec3d(start.x + direction.x * DISTANCE, end.y, start.z + direction.z * DISTANCE);
            if (end.squaredDistanceTo(current) > .0025)
                player.networkHandler.requestTeleport(end.x, end.y, end.z, player.getYaw(), player.getPitch());
            finish(player, true);
        } else previous = current;
    }

    private record Contact(Vec3d position) { }

    private Contact contact(PlayerEntity player, Vec3d from, Vec3d to) {
        Box body = player.getBoundingBox().offset(from.subtract(player.getPos()));
        Box search = body.stretch(to.subtract(from)).expand(.03);
        Vec3d closest = null;
        for (LivingEntity target : player.getWorld().getEntitiesByClass(LivingEntity.class, search,
                candidate -> canHit(player, candidate))) {
            if (target.getPos().subtract(from).dotProduct(direction) < 0) continue;
            Box box = target.getBoundingBox().expand(player.getWidth() * .5 + .02, 0, player.getWidth() * .5 + .02);
            box = new Box(box.minX, box.minY - player.getHeight(), box.minZ, box.maxX, box.maxY, box.maxZ);
            Vec3d hit = box.contains(from) ? from : box.raycast(from, to).orElse(null);
            if (hit != null && (closest == null || hit.squaredDistanceTo(from) < closest.squaredDistanceTo(from))) closest = hit;
        }
        return closest == null ? null : new Contact(closest);
    }

    private boolean canHit(PlayerEntity player, LivingEntity target) {
        return target != player && target.isAlive() && !target.isSpectator() && target.isAttackable()
                && !player.isConnectedThroughVehicle(target) && !player.isTeammate(target) && !target.isTeammate(player)
                && !(target instanceof ArmorStandEntity stand && stand.isMarker())
                && !(target instanceof PlayerEntity other && (other.getAbilities().invulnerable || !player.shouldDamagePlayer(other)))
                && player.canSee(target);
    }

    private void finish(ServerPlayerEntity player, boolean impact) {
        stop();
        PowerHolderComponent.syncPower(player, getType());
        if (!impact) return;
        DamageSource source = new DamageSource(player.getWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE), player);
        for (LivingEntity target : player.getWorld().getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(3, .25, 3),
                candidate -> canHit(player, candidate) && player.squaredDistanceTo(candidate) < 9
                        && candidate.getPos().subtract(player.getPos()).dotProduct(direction) >= 0)) {
            if (target.damage(source, player.getMaxHealth())) target.takeKnockback(.4, -direction.x, -direction.z);
        }
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                player.getSoundCategory(), 1, .7f);
        player.getServerWorld().spawnParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + direction.x,
                player.getBodyY(.5), player.getZ() + direction.z, 1, 0, 0, 0, 0);
    }

    private void stop() {
        rushing = stopAfterTravel = false;
        entity.setVelocity(0, entity.getVelocity().y, 0);
    }

    @Override public void onRemoved() { if (rushing) stop(); }
    @Override public void onRespawn() { rushing = stopAfterTravel = false; }

    @Override public NbtElement toTag() {
        var tag = new NbtCompound();
        tag.putLong("LastUse", lastUseTime);
        tag.putBoolean("Rushing", rushing);
        tag.putDouble("StartX", start.x); tag.putDouble("StartY", start.y); tag.putDouble("StartZ", start.z);
        tag.putDouble("DirectionX", direction.x); tag.putDouble("DirectionZ", direction.z);
        return tag;
    }

    @Override public void fromTag(NbtElement nbt) {
        if (!(nbt instanceof NbtCompound tag)) return;
        lastUseTime = tag.getLong("LastUse");
        boolean wasRushing = rushing;
        rushing = entity.getWorld().isClient && tag.getBoolean("Rushing");
        if (wasRushing && !rushing) stop();
        stopAfterTravel = false;
        start = previous = new Vec3d(tag.getDouble("StartX"), tag.getDouble("StartY"), tag.getDouble("StartZ"));
        direction = new Vec3d(tag.getDouble("DirectionX"), 0, tag.getDouble("DirectionZ"));
    }

    public static PowerFactory<DrakeBodySlamPower> factory() {
        return new PowerFactory<DrakeBodySlamPower>(EarthenDrake.id("body_slam"), new SerializableData(),
                data -> DrakeBodySlamPower::new).allowCondition();
    }
}
