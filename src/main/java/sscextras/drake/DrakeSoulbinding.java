package sscextras.drake;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import net.onixary.shapeShifterCurseFabric.data.StaticParams;
import java.util.Comparator;

public final class DrakeSoulbinding {
    public static final int DAY_TICKS = 24000;
    public static final int SERVICE_TICKS = 10 * DAY_TICKS;
    public static final int RITUAL_TICKS = 600;
    public static final int SOUL_TRANSFORM_TICKS = 300, SOUL_RETURN_TICKS = 360;
    public static final int ESCORT = 1, RESTRAINED = 2, HOLDING = 3, CHANTING = 4, FEEDING = 5;
    public static final int SHOE_RESTRAINED = 6, SHOEING = 7;

    public interface State {
        int sscExtras$ritualRole();
        void sscExtras$ritualRole(int role);
        int sscExtras$shoeingPaw();
        void sscExtras$shoeingPaw(int paw);
        int sscExtras$soulTicks();
        void sscExtras$soulTicks(int ticks);
        boolean sscExtras$drakeSoul();
        void sscExtras$drakeSoul(boolean bound);
    }

    private DrakeSoulbinding() { }

    public static void register() {
        SoulboundEquipment.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var claim = DrakeOutpostOwnership.claim(handler.player);
            if (claim != null) claim.lastServiceTime = handler.player.getWorld().getTimeOfDay();
            syncSoul(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var claim = DrakeOutpostOwnership.claim(handler.player);
            if (claim != null) cancel(handler.player, claim);
        });
    }

    public static int role(LivingEntity entity) { return ((State)entity).sscExtras$ritualRole(); }
    static void role(LivingEntity entity, int role) { ((State)entity).sscExtras$ritualRole(role); }
    public static boolean restrained(PlayerEntity player) { return role(player) == RESTRAINED || shoeing(player); }
    public static boolean shoeing(PlayerEntity player) { return role(player) == SHOE_RESTRAINED; }
    public static int shoeingPaw(LivingEntity entity) { return ((State)entity).sscExtras$shoeingPaw(); }
    static void shoeingPaw(LivingEntity entity, int paw) { ((State)entity).sscExtras$shoeingPaw(paw); }

    public static boolean shoeingTogether(LivingEntity first, LivingEntity second) {
        if (first.getWorld().isClient) return false;
        int role = role(first), otherRole = role(second);
        if (role != SHOE_RESTRAINED && role != HOLDING && role != SHOEING
                && otherRole != SHOE_RESTRAINED && otherRole != HOLDING && otherRole != SHOEING) return false;
        var patient = first instanceof ServerPlayerEntity player ? player
                : first instanceof PillagerEntity guard ? attendee(guard) : null;
        if (patient == null || !shoeing(patient)) return false;
        var claim = DrakeOutpostOwnership.claim(patient);
        return second == patient || second instanceof PillagerEntity && claim != null && claim.attendants.contains(second.getUuid());
    }

    public static boolean bound(PlayerEntity player) {
        if (player.getWorld().isClient) return ((State)player).sscExtras$drakeSoul();
        var claim = DrakeOutpostOwnership.claim(player);
        return claim != null && claim.soulbound;
    }

    public static int soulTicks(PlayerEntity player) { return ((State)player).sscExtras$soulTicks(); }

    private static void soulTicks(PlayerEntity player, int ticks) { ((State)player).sscExtras$soulTicks(ticks); }

    public static void syncSoul(PlayerEntity player) {
        if (player.getWorld().isClient) return;
        ((State)player).sscExtras$drakeSoul(bound(player));
        io.github.apace100.apoli.component.PowerHolderComponent.getPowers(player, DrakeBodyPower.class)
                .forEach(DrakeBodyPower::refreshSize);
    }

    public static boolean ritualActive(PlayerEntity player) {
        var claim = DrakeOutpostOwnership.claim(player);
        return claim != null && !claim.attendants.isEmpty();
    }

    public static float instinctRate(PlayerEntity player) {
        if (!restrained(player) || shoeing(player)) return 0;
        int stage = EarthenDrake.stage(player);
        if (stage < 0 || stage >= 2) return 0;
        var claim = DrakeOutpostOwnership.claim(player);
        return claim != null && claim.ritualTicks > 0 && claim.ritualTicks <= SOUL_TRANSFORM_TICKS ? 1.5f : 0;
    }

    public static void disobey(PlayerEntity player) {
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim == null || claim.soulbound) return;
        claim.lastServiceTime = player.getWorld().getTimeOfDay();
        if (claim.goodTicks == 0) return;
        claim.goodTicks = 0; claim.ritualHint = 0;
        cancel(player, claim);
        hint(player, "service_reset");
        DrakeOutpostOwnership.get(player.getServer()).markDirty();
    }

    static void tick(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        long time = player.getWorld().getTimeOfDay();
        long elapsed = claim.lastServiceTime == Long.MIN_VALUE ? 20 : Math.max(20, time - claim.lastServiceTime);
        claim.lastServiceTime = time;
        if (!player.isAlive() || player.isSpectator() || claim.awaitingRespawn) { cancel(player, claim); return; }
        if (claim.shoeingDue && !claim.shoeingRitual && DrakeShoes.fullyEquipped(player)) {
            claim.shoeingDue = false;
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
        }
        boolean shoeing = claim.shoeingRitual || claim.shoeingDue;
        if (shoeing) claim.shoeingStage = EarthenDrake.stage(player);
        boolean punishment = !shoeing && DrakeFeralization.due(claim);
        if (claim.soulbound) { SoulboundEquipment.enchant(player); if (!punishment && !shoeing) return; }
        if (player.isCreative() || EarthenDrake.stage(player) < 0) { cancel(player, claim); return; }
        if (missedAttendance(player, claim)) { cancel(player, claim); return; }
        if (!punishment && !shoeing && claim.goodTicks < SERVICE_TICKS) {
            claim.goodTicks += (int)Math.min(SERVICE_TICKS - claim.goodTicks, elapsed);
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
        }
        int hint = claim.goodTicks >= SERVICE_TICKS ? 3 : claim.goodTicks >= 8 * DAY_TICKS ? 2 : 1;
        if (!punishment && !shoeing && hint > claim.ritualHint) {
            claim.ritualHint = hint;
            hint(player, hint == 1 ? "service_promise" : hint == 2 ? "ritual_soon" : "ritual_ready");
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
        }
        if (!punishment && !shoeing && claim.goodTicks < SERVICE_TICKS) return;
        boolean serving = claim.world.equals(player.getWorld().getRegistryKey()) &&
                (DrakeCaptureGoal.near(claim.stable, player.getPos(), DrakeStableLayout.roamRange(claim.stable)) || DrakeBattleGoal.riding(player));
        if (!serving || claim.tryingToEscape) { cancel(player, claim); return; }
        if (player.hasPassengers() || player.hasVehicle() || DrakeBattleGoal.assigned(player)) { cancel(player, claim); return; }
        var world = player.getServerWorld();
        if (claim.attendants.isEmpty()) {
            var guards = world.getEntitiesByClass(PillagerEntity.class, Box.from(claim.stable).expand(DrakeStableLayout.roamRange(claim.stable)), guard -> {
                if (!available(guard)) return false;
                var stable = ((DrakeStableNavigation)guard.getNavigation()).stable();
                return stable != null && claim.matches(world, stable) && attendee(guard) == null;
            });
            guards.sort(Comparator.comparingDouble(player::squaredDistanceTo));
            if (guards.size() < 3) return;
            for (int i = 0; i < 3; i++) claim.attendants.add(guards.get(i).getUuid());
            claim.shoeingRitual = shoeing;
            role(player, ESCORT);
            hint(player, shoeing ? "shoeing_gathering" : punishment ? "punishment_gathering" : "ritual_gathering");
        }
        for (int i = 0; i < 3; i++) {
            var entity = world.getEntity(claim.attendants.get(i));
            if (!(entity instanceof PillagerEntity guard) || !available(guard)) { cancel(player, claim); return; }
        }
        if (!restrained(player)) {
            var guide = world.getEntity(claim.attendants.get(0));
            var helper = world.getEntity(claim.attendants.get(1));
            if (DrakeLeashing.holder(player) != guide || !atHay(player, claim)
                    || player.squaredDistanceTo(hayPosition(claim)) > 2.25
                    || guide.squaredDistanceTo(player) > 9 || helper.squaredDistanceTo(player) > 9) return;
            if (player.isSleeping()) player.wakeUp();
            player.clearActiveItem();
            role(player, shoeing ? SHOE_RESTRAINED : RESTRAINED);
            if (shoeing) shoeingPaw(player, Math.min(3, claim.shoeingTicks / DrakeShoes.PAW_TICKS));
            pin(player, claim);
            hint(player, shoeing ? "shoeing_held" : "ritual_held");
        }
        if (!atHay(player, claim)) { cancel(player, claim); return; }
        for (int i = 0; i < 3; i++) {
            var guard = (PillagerEntity)world.getEntity(claim.attendants.get(i));
            if (guard.squaredDistanceTo(attendancePosition(claim, i)) > 1.44 || !guard.getVisibilityCache().canSee(player)) {
                if (claim.ritualTicks > 0) hint(player, "ritual_interrupted");
                for (var id : claim.attendants) role((LivingEntity)world.getEntity(id), 0);
                if (!shoeing) { claim.ritualTicks = claim.feedingTicks = 0; soulTicks(player, 0); }
                return;
            }
        }
        if (shoeing) {
            shoe(player, claim);
            return;
        }
        if (punishment && claim.feedingTicks < 60) {
            for (int i = 0; i < 3; i++) role((LivingEntity)world.getEntity(claim.attendants.get(i)), i == 0 ? FEEDING : HOLDING);
            if (claim.feedingTicks == 0) hint(player, "punishment_feed");
            claim.feedingTicks += 20;
            if (claim.feedingTicks == 60) {
                var leader = (PillagerEntity)world.getEntity(claim.attendants.get(0));
                leader.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                var catalyst = new ItemStack(net.onixary.shapeShifterCurseFabric.items.RegCustomItem.POWERFUL_CATALYST);
                // The ritual owns the transformation; consuming the item normally would start a second SSC sequence.
                player.getHungerManager().eat(catalyst.getItem(), catalyst);
                world.spawnParticles(new net.minecraft.particle.ItemStackParticleEffect(net.minecraft.particle.ParticleTypes.ITEM, catalyst),
                        player.getX(), player.getEyeY() - .15, player.getZ(), 12, .15, .1, .15, .02);
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 1, .7f);
            }
            return;
        }
        if (claim.ritualTicks == 0) {
            if (TransformManager.getPlayerTransformData(player).isTransforming) return;
            for (int i = 0; i < 3; i++) role((LivingEntity)world.getEntity(claim.attendants.get(i)),
                    i == (punishment ? 0 : 2) ? CHANTING : HOLDING);
            hint(player, punishment ? "punishment_begin" : "ritual_begin");
            if (EarthenDrake.stage(player) < 2) hint(player, "ritual_change");
        }
        if (claim.ritualTicks % 80 == 0)
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, .65f, .7f);
        boolean waitingForBody = claim.ritualTicks == SOUL_TRANSFORM_TICKS
                && (EarthenDrake.stage(player) < 2 || TransformManager.getPlayerTransformData(player).isTransforming);
        if (!waitingForBody) {
            claim.ritualTicks += 20;
            soulTicks(player, claim.ritualTicks);
            if (claim.ritualTicks == SOUL_TRANSFORM_TICKS) hint(player, punishment ? "punishment_mind" : "ritual_soul");
            if (claim.ritualTicks == SOUL_RETURN_TICKS) hint(player, "ritual_soul_returned");
        }
        if (claim.ritualTicks >= RITUAL_TICKS && (punishment || EarthenDrake.stage(player) >= 2)
                && !TransformManager.getPlayerTransformData(player).isTransforming) complete(player, claim);
    }

    private static boolean missedAttendance(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        var world = player.getServer().getWorld(claim.world);
        if (world == null) return false;
        long time = world.getTimeOfDay(), night = Math.floorDiv(time, DAY_TICKS);
        if (Math.floorMod(time, DAY_TICKS) < 18000 || claim.lastAttendanceNight >= night) return false;
        var stall = Vec3d.ofCenter(claim.bed()).add(0, 1, 0);
        var keeper = world.getEntitiesByClass(PillagerEntity.class, claim.stall().expand(8), guard -> {
            if (guard instanceof DrakeVisitorEntity || !guard.isAlive() || guard.isAiDisabled()
                    || guard.hasActiveRaid() || guard.hasVehicle() || DrakeFaction.fighting(guard)) return false;
            var home = ((DrakeStableNavigation)guard.getNavigation()).stable();
            return home != null && claim.matches(world, home) && world.raycast(new RaycastContext(guard.getEyePos(), stall,
                    RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, guard)).getType() == HitResult.Type.MISS;
        }).stream().min(Comparator.comparingDouble(guard -> guard.squaredDistanceTo(stall))).orElse(null);
        if (keeper == null) return false;
        claim.lastAttendanceNight = night;
        boolean present = claim.world.equals(player.getWorld().getRegistryKey()) && claim.stall().contains(player.getPos())
                && keeper.getVisibilityCache().canSee(player);
        if (!present) disobey(player);
        DrakeOutpostOwnership.get(player.getServer()).markDirty();
        return !present;
    }

    private static boolean atHay(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        return claim.world.equals(player.getWorld().getRegistryKey()) && claim.stall().contains(player.getPos())
                && player.getWorld().getBlockState(player.getBlockPos().down()).isOf(Blocks.HAY_BLOCK);
    }

    static Vec3d hayPosition(DrakeOutpostOwnership.Claim claim) { return Vec3d.ofBottomCenter(claim.bed()).add(.2, 0, 0); }

    private static void pin(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        var pos = hayPosition(claim);
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0;
        if (player.squaredDistanceTo(pos) > .01)
            player.networkHandler.requestTeleport(pos.x, pos.y, pos.z, DrakeStableLayout.outwardYaw(claim.stable, claim.gate()), player.getPitch());
    }

    public static void hold(ServerPlayerEntity player) {
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim == null) { role(player, 0); return; }
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || EarthenDrake.stage(player) < 0
                || claim.attendants.size() != 3 || !atHay(player, claim) || player.squaredDistanceTo(hayPosition(claim)) > 16) {
            cancel(player, claim); return;
        }
        for (int i = 0; i < 2; i++) {
            var entity = player.getServerWorld().getEntity(claim.attendants.get(i));
            if (!(entity instanceof PillagerEntity guard) || !available(guard) || guard.squaredDistanceTo(player) > 16) {
                cancel(player, claim); return;
            }
        }
        pin(player, claim);
        if (!shoeing(player) && claim.ritualTicks > 0 && player.age % 2 == 0) for (int i = 0; i < 2; i++)
            player.getServerWorld().spawnParticles(StaticParams.PLAYER_TRANSFORM_PARTICLE,
                    player.getX() + (player.getRandom().nextDouble() - .5) * 1.4,
                    player.getY() + 1 + player.getRandom().nextDouble() * 1.5,
                    player.getZ() + (player.getRandom().nextDouble() - .5) * 1.4, 0, 0, -1, 0, 1);
    }

    static boolean available(PillagerEntity guard) {
        return !(guard instanceof DrakeVisitorEntity) && guard.isAlive() && !guard.isRemoved() && !guard.isAiDisabled() && !guard.hasActiveRaid()
                && !guard.hasVehicle() && !guard.hasPassengers() && !DrakeFaction.fighting(guard)
                && (DrakeBattleGoal.of(guard) == null || DrakeBattleGoal.of(guard).mount() == null)
                && ((DrakeCaptureGoal.Captor)guard).sscExtras$captureGoal().quarry() == null
                && ((DrakeFaction.EquipmentDisplay)guard).sscExtras$recruiting() == null;
    }

    static ServerPlayerEntity attendee(PillagerEntity guard) {
        if (!(guard.getWorld() instanceof ServerWorld world)) return null;
        for (var player : world.getPlayers()) {
            var claim = DrakeOutpostOwnership.claim(player);
            if (claim != null && claim.attendants.contains(guard.getUuid())) return player;
        }
        return null;
    }

    static Vec3d attendancePosition(DrakeOutpostOwnership.Claim claim, int index) {
        var bed = hayPosition(claim);
        if (claim.shoeingRitual && index == 2) {
            int paw = Math.min(3, claim.shoeingTicks / DrakeShoes.PAW_TICKS);
            int inward = DrakeStableLayout.inward(claim.stable, claim.gate());
            boolean quadruped = claim.shoeingStage == 3;
            double side = (paw % 2 == 0 ? -1.1 : 1.1) * (quadruped ? -1 : 1);
            double end = quadruped ? paw < 2 ? -.85 : 1.15 : paw < 2 ? .85 : -.85;
            return bed.add(side * inward, 0, end * inward);
        }
        return switch (index) {
            case 0 -> bed.add(-1.25, 0, .1 * DrakeStableLayout.inward(claim.stable, claim.gate()));
            case 1 -> bed.add(1.25, 0, .1 * DrakeStableLayout.inward(claim.stable, claim.gate()));
            default -> bed.add(0, 0, -2.25 * DrakeStableLayout.inward(claim.stable, claim.gate()));
        };
    }

    private static void cancel(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        if (restrained(player) || claim.ritualTicks > 0) hint(player, "ritual_interrupted");
        finishAttendance(player, claim);
    }

    private static void finishAttendance(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        role(player, 0);
        soulTicks(player, 0);
        var world = player.getServer().getWorld(claim.world);
        if (world != null) for (var id : claim.attendants) {
            if (world.getEntity(id) instanceof PillagerEntity guard) {
                role(guard, 0);
                ((DrakeFaction.EquipmentDisplay)guard).sscExtras$showEquipment(ItemStack.EMPTY);
                guard.getNavigation().stop();
            }
        }
        var holder = DrakeLeashing.holder(player);
        if (holder != null && claim.attendants.contains(holder.getUuid())) {
            if (player.getWorld() == world && player.squaredDistanceTo(Vec3d.ofCenter(claim.tie())) < 100
                    && world.getBlockState(claim.tie()).isIn(BlockTags.FENCES))
                DrakeLeashing.attach(player, LeashKnotEntity.getOrCreate(world, claim.tie()));
            else DrakeLeashing.detach(player, false);
        }
        claim.attendants.clear(); claim.ritualTicks = claim.feedingTicks = 0;
        claim.shoeingRitual = false; claim.shoeingTicks = 0;
    }

    private static void shoe(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        var world = player.getServerWorld();
        int paw = Math.min(3, claim.shoeingTicks / DrakeShoes.PAW_TICKS);
        shoeingPaw(player, paw);
        for (int i = 0; i < 3; i++) role((LivingEntity)world.getEntity(claim.attendants.get(i)), i == 2 ? SHOEING : HOLDING);
        if (claim.shoeingTicks % DrakeShoes.PAW_TICKS == 0)
            hint(player, "shoeing_paw_" + Math.min(3, claim.shoeingTicks / DrakeShoes.PAW_TICKS));
        var farrier = (PillagerEntity)world.getEntity(claim.attendants.get(2));
        shoeingPaw(farrier, paw);
        farrier.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.HOSTILE, .35f, 1.25f);
        int next = claim.shoeingTicks + 20;
        if ((next == DrakeShoes.PAW_TICKS * 2 || next >= DrakeShoes.RITUAL_TICKS)
                && !DrakeShoes.equipPair(player, next >= DrakeShoes.RITUAL_TICKS)) return;
        claim.shoeingTicks = next;
        if (next < DrakeShoes.RITUAL_TICKS) shoeingPaw(player, next / DrakeShoes.PAW_TICKS);
        if (next < DrakeShoes.RITUAL_TICKS) return;
        claim.shoeingDue = false;
        player.clearActiveItem();
        finishAttendance(player, claim);
        DrakeOutpostOwnership.get(player.getServer()).markDirty();
        hint(player, "shoeing_complete");
    }

    private static void complete(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        boolean punishment = DrakeFeralization.due(claim);
        if (!claim.soulbound) {
            claim.previousSpawn.putString("World", player.getSpawnPointDimension().getValue().toString());
            if (player.getSpawnPointPosition() != null) claim.previousSpawn.putLong("Pos", player.getSpawnPointPosition().asLong());
            claim.previousSpawn.putFloat("Angle", player.getSpawnAngle());
            claim.previousSpawn.putBoolean("Forced", player.isSpawnForced());
        }
        claim.soulbound = true;
        claim.soulboundStage = punishment ? 3 : EarthenDrake.stage(player);
        syncSoul(player);
        if (punishment && EarthenDrake.stage(player) != 3) FormAbilityManager.applyForm(player, EarthenDrake.FORMS[3]);
        if (punishment) {
            claim.feral = true;
            player.clearActiveItem();
            player.closeHandledScreen();
            DrakeFeralization.sync(player, claim);
        }
        SoulboundEquipment.enchant(player);
        player.setSpawnPoint(claim.world, claim.bed(), DrakeStableLayout.outwardYaw(claim.stable, claim.gate()), true, false);
        finishAttendance(player, claim);
        DrakeOutpostOwnership.get(player.getServer()).markDirty();
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1, .7f);
        hint(player, punishment ? "punishment_complete" : "ritual_complete");
    }

    public static void formChanged(PlayerEntity player, PlayerFormBase form) {
        if (player.getWorld().isClient || !player.isAlive()) return;
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim != null && claim.soulbound && form.getGroup() == EarthenDrake.GROUP && form.getIndex() > claim.soulboundStage) {
            claim.soulboundStage = form.getIndex();
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
        }
        if (claim != null && claim.soulbound && !claim.awaitingRespawn && EarthenDrake.stage(player) >= 0 && form.getGroup() != EarthenDrake.GROUP) {
            clearCurse(player, claim);
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
            hint(player, "soul_freed");
        }
    }

    static void clearCurse(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        cancel(player, claim);
        claim.shoeingDue = false;
        claim.feral = false;
        DrakeFeralization.stop(player, claim);
        DrakeFeralization.sync(player, claim);
        if (!claim.soulbound) return;
        claim.soulbound = false;
        syncSoul(player);
        claim.awaitingRespawn = false;
        var spawn = claim.previousSpawn;
        claim.previousSpawn = new net.minecraft.nbt.NbtCompound();
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        RegistryKey<World> dimension = spawn.contains("World")
                ? RegistryKey.of(RegistryKeys.WORLD, new Identifier(spawn.getString("World"))) : World.OVERWORLD;
        serverPlayer.setSpawnPoint(dimension, spawn.contains("Pos") ? BlockPos.fromLong(spawn.getLong("Pos")) : null,
                spawn.getFloat("Angle"), spawn.getBoolean("Forced"), false);
    }

    public static void beforeRespawn(ServerPlayerEntity player, boolean alive) {
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim == null || !claim.soulbound) return;
        claim.awaitingRespawn = true;
        if (!alive) player.setSpawnPoint(claim.world, claim.bed(), DrakeStableLayout.outwardYaw(claim.stable, claim.gate()), true, false);
    }

    public static void afterRespawn(ServerPlayerEntity player, boolean alive) {
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim == null || !claim.soulbound) return;
        FormAbilityManager.applyForm(player, EarthenDrake.FORMS[claim.soulboundStage]);
        claim.awaitingRespawn = false;
        SoulboundEquipment.enchant(player);
        if (!alive) {
            var world = player.getServer().getWorld(claim.world);
            if (world != null) {
                world.getChunk(claim.bed());
                var pos = respawnPosition(player, world, claim);
                if (pos != null) player.teleport(world, pos.x, pos.y, pos.z, DrakeStableLayout.outwardYaw(claim.stable, claim.gate()), 0);
            }
            hint(player, "soul_respawn");
        }
        player.setSpawnPoint(claim.world, claim.bed(), DrakeStableLayout.outwardYaw(claim.stable, claim.gate()), true, false);
        DrakeOutpostOwnership.tick(player);
        DrakeOutpostOwnership.get(player.getServer()).markDirty();
    }

    private static Vec3d respawnPosition(ServerPlayerEntity player, ServerWorld world, DrakeOutpostOwnership.Claim claim) {
        var center = claim.bed();
        for (int dy = 0; dy <= 3; dy++) for (int dz : new int[]{0, -1, 1, -2, 2}) for (int dx : new int[]{0, 1, -1}) {
            var block = center.add(dx, dy, dz);
            var pos = Vec3d.ofBottomCenter(block);
            if (world.getBlockState(block.down()).isSolidBlock(world, block.down())
                    && world.isSpaceEmpty(player, player.getDimensions(player.getPose()).getBoxAt(pos))) return pos;
        }
        return null;
    }

    private static void hint(PlayerEntity player, String key) {
        player.sendMessage(Text.translatable("message.ssc-extras.drake." + key).formatted(Formatting.LIGHT_PURPLE), false);
    }
}
