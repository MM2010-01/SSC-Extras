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
    public static final int FEED_TICKS = 60;
    public static final int ESCORT = 1, RESTRAINED = 2, HOLDING = 3, CHANTING = 4, FEEDING = 5;
    public static final int SHOE_RESTRAINED = 6, SHOEING = 7;

    public interface State {
        int sscExtras$ritualRole();
        void sscExtras$ritualRole(int role);
        int sscExtras$shoeingPaw();
        void sscExtras$shoeingPaw(int paw);
        int sscExtras$soulTicks();
        void sscExtras$soulTicks(int ticks);
        String sscExtras$soulAnimation();
        void sscExtras$soulAnimation(String animation);
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
            DrakeRitualTransform.recover(handler.player);
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

    public static boolean drakeSoul(PlayerEntity player) { return bound(player) || DrakeFeralization.permanent(player); }

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

    public static String startRitual(ServerPlayerEntity player, DrakeRituals.Type ritual) {
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim == null) return "no_stall";
        if (claim.ritualUnavailable && claim.ritualCheckpoint.type.equals(ritual.command())) return "unavailable";
        if (!player.isAlive() || player.isSpectator()) return "not_ready";
        if (ritualActive(player) || player.hasVehicle() || player.hasPassengers() || DrakeBattleGoal.assigned(player)) return "busy";
        var world = player.getServer().getWorld(claim.world);
        if (world == null) return "unavailable";
        world.getChunk(claim.bed());
        if (!world.getBlockState(claim.bed().down()).isOf(Blocks.HAY_BLOCK)) return "unavailable";
        var run = createRitual(claim, ritual);
        var bed = hayPosition(claim);
        var guards = world.getEntitiesByClass(PillagerEntity.class, Box.from(claim.stable).expand(16), guard -> {
            if (!available(guard) || attendee(guard) != null) return false;
            var home = ((DrakeStableNavigation)guard.getNavigation()).stable();
            return home != null && claim.matches(world, home);
        });
        guards.sort(Comparator.comparingDouble(guard -> guard.squaredDistanceTo(bed)));
        var stable = DrakeCaptureGoal.findStable(world, claim.bed());
        while (guards.size() < run.composition().size()) {
            var guard = net.minecraft.entity.EntityType.PILLAGER.create(world);
            if (guard == null) return "unavailable";
            guard.setPosition(bed);
            guard.initialize(world, world.getLocalDifficulty(claim.bed()), net.minecraft.entity.SpawnReason.COMMAND, null, null);
            guard.setPersistent();
            if (stable != null) ((DrakeStableNavigation)guard.getNavigation()).home(stable);
            if (!world.spawnEntity(guard)) return "unavailable";
            guards.add(guard);
        }
        if (player.isSleeping()) player.wakeUp();
        DrakeLeashing.detach(player, false);
        DrakeRitualTransform.finish(player);
        if (EarthenDrake.stage(player) < 0) FormAbilityManager.applyForm(player, EarthenDrake.FORMS[0]);
        player.teleport(world, bed.x, bed.y, bed.z, DrakeStableLayout.outwardYaw(claim.stable, claim.gate()), 0);
        player.clearActiveItem(); player.setVelocity(Vec3d.ZERO); player.fallDistance = 0;
        claim.tryingToEscape = false;
        claim.commandRitual = ritual;
        claim.ritualCheckpoint.commanded = true;
        claim.shoeingRitual = run.targetRole() == SHOE_RESTRAINED;
        claim.shoeingStage = EarthenDrake.stage(player);
        for (int i = 0; i < run.composition().size(); i++) {
            var guard = guards.get(i);
            var pos = attendancePosition(claim, i);
            guard.getNavigation().stop(); guard.setTarget(null); guard.clearActiveItem();
            guard.refreshPositionAndAngles(pos.x, pos.y, pos.z, player.getYaw(), 0);
            claim.attendants.add(guard.getUuid());
        }
        if (!run.assign(player, claim)) { cancel(player, claim); return "busy"; }
        role(player, run.targetRole());
        if (claim.shoeingRitual) shoeingPaw(player, run.paw());
        DrakeLeashing.attachPillager(player, guards.get(run.composition().index("guide")));
        hint(player, run.hintPrefix() + "_held");
        tick(player, claim);
        return null;
    }

    static boolean punishment(DrakeOutpostOwnership.Claim claim) {
        return claim.ritualCheckpoint != null ? claim.ritualCheckpoint.type.equals(DrakeRituals.FERALIZATION.command())
                : DrakeFeralization.due(claim);
    }

    public static float instinctRate(PlayerEntity player) {
        if (!restrained(player) || shoeing(player)) return 0;
        int stage = EarthenDrake.stage(player);
        if (stage < 0 || stage >= 2) return 0;
        var claim = DrakeOutpostOwnership.claim(player);
        return claim != null && claim.ritualRun != null && claim.ritualRun.status() == sscextras.events.NpcEvent.Status.RUNNING
                && claim.ritualRun.growsBody() ? 1.5f : 0;
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
        boolean commanded = claim.commandRitual != null;
        long time = player.getWorld().getTimeOfDay();
        long elapsed = claim.lastServiceTime == Long.MIN_VALUE ? 20 : Math.max(20, time - claim.lastServiceTime);
        claim.lastServiceTime = time;
        if (!player.isAlive() || player.isSpectator() || claim.awaitingRespawn) { cancel(player, claim); return; }
        if (claim.ritualUnavailable) return;
        if (claim.shoeingDue && !claim.shoeingRitual && DrakeShoes.fullyEquipped(player)) {
            claim.shoeingDue = false;
            claim.shoeingViolations = 0;
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
        }
        boolean shoeing = claim.ritualRun != null ? claim.ritualRun.targetRole() == SHOE_RESTRAINED : claim.shoeingDue;
        if (shoeing) claim.shoeingStage = EarthenDrake.stage(player);
        boolean punishment = !shoeing && punishment(claim);
        if (claim.soulbound) {
            SoulboundEquipment.enchant(player);
            if (claim.ritualCheckpoint == null && !commanded && DrakeRituals.due(player, claim) == null) return;
        }
        if ((player.isCreative() && !commanded) || EarthenDrake.stage(player) < 0) { cancel(player, claim); return; }
        if (!commanded && missedAttendance(player, claim)) { cancel(player, claim); return; }
        if (!claim.soulbound && !commanded && !punishment && !shoeing && claim.goodTicks < SERVICE_TICKS) {
            claim.goodTicks += (int)Math.min(SERVICE_TICKS - claim.goodTicks, elapsed);
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
        }
        int hint = claim.goodTicks >= SERVICE_TICKS ? 3 : claim.goodTicks >= 8 * DAY_TICKS ? 2 : 1;
        if (!claim.soulbound && !commanded && !punishment && !shoeing && hint > claim.ritualHint) {
            claim.ritualHint = hint;
            hint(player, hint == 1 ? "service_promise" : hint == 2 ? "ritual_soon" : "ritual_ready");
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
        }
        var type = claim.ritualCheckpoint != null ? DrakeRituals.get(claim.ritualCheckpoint.type) : DrakeRituals.due(player, claim);
        if (type == null) return;
        boolean serving = claim.world.equals(player.getWorld().getRegistryKey()) &&
                (DrakeCaptureGoal.near(claim.stable, player.getPos(), DrakeStableLayout.roamRange(claim.stable)) || DrakeBattleGoal.riding(player));
        if (!serving || claim.tryingToEscape) { cancel(player, claim); return; }
        if (player.hasPassengers() || player.hasVehicle() || DrakeBattleGoal.assigned(player)) { cancel(player, claim); return; }
        var world = player.getServerWorld();
        var run = createRitual(claim, type);
        if (claim.attendants.isEmpty()) {
            var guards = world.getEntitiesByClass(PillagerEntity.class, Box.from(claim.stable).expand(DrakeStableLayout.roamRange(claim.stable)), guard -> {
                if (!available(guard)) return false;
                var stable = ((DrakeStableNavigation)guard.getNavigation()).stable();
                return stable != null && claim.matches(world, stable) && attendee(guard) == null && reachesRitual(guard, player, claim);
            });
            guards.sort(Comparator.comparingDouble(player::squaredDistanceTo));
            if (guards.size() < run.composition().size()) return;
            for (int i = 0; i < run.composition().size(); i++) claim.attendants.add(guards.get(i).getUuid());
            if (!run.assign(player, claim)) { claim.attendants.clear(); return; }
            claim.shoeingRitual = shoeing;
            role(player, ESCORT);
            hint(player, run.hintPrefix() + "_gathering");
        }
        for (int i = 0; i < run.composition().size(); i++) {
            var entity = world.getEntity(claim.attendants.get(i));
            if (entity instanceof PillagerEntity guard && available(guard)
                    && (restrained(player) || DrakeLeashing.holder(player) == world.getEntity(claim.attendants.get(run.composition().index("guide")))
                        || guard.getRootVehicle().squaredDistanceTo(player) <= 36 || reachesRitual(guard, player, claim))) continue;
            if (restrained(player)) { cancel(player, claim); return; }
            var replacements = world.getEntitiesByClass(PillagerEntity.class,
                    Box.from(claim.stable).expand(DrakeStableLayout.roamRange(claim.stable)), guard -> {
                if (!available(guard) || attendee(guard) != null) return false;
                var home = ((DrakeStableNavigation)guard.getNavigation()).stable();
                return home != null && claim.matches(world, home) && reachesRitual(guard, player, claim);
            });
            var replacement = replacements.stream().min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
            if (replacement == null || !run.replace(claim.attendants.get(i), replacement.getUuid())) { cancel(player, claim); return; }
            if (entity instanceof PillagerEntity previous) {
                DrakeBattleGoal.of(previous).stopPursuit();
                role(previous, 0); previous.getNavigation().stop();
                ((DrakeFaction.EquipmentDisplay)previous).sscExtras$showEquipment(ItemStack.EMPTY);
                if (DrakeLeashing.holder(player) == previous) DrakeLeashing.detach(player, false);
            }
            claim.attendants.set(i, replacement.getUuid());
        }
        if (!restrained(player)) {
            var guide = world.getEntity(claim.attendants.get(run.composition().index("guide")));
            if (DrakeLeashing.holder(player) != guide || !atHay(player, claim)
                    || player.squaredDistanceTo(hayPosition(claim)) > 2.25) return;
            for (int i = 0; i < run.composition().size(); i++)
                if (run.composition().participants().get(i).escortRequired()
                        && world.getEntity(claim.attendants.get(i)).squaredDistanceTo(player) > 9) return;
            if (player.isSleeping()) player.wakeUp();
            player.clearActiveItem();
            role(player, run.targetRole());
            if (shoeing) shoeingPaw(player, run.paw());
            pin(player, claim);
            hint(player, run.hintPrefix() + "_held");
        }
        if (!atHay(player, claim)) { cancel(player, claim); return; }
        boolean ready = ready(player, claim);
        if (!ready) {
            if (run.status() == sscextras.events.NpcEvent.Status.RUNNING) hint(player, "ritual_interrupted");
            for (var id : claim.attendants) if (world.getEntity(id) instanceof LivingEntity actor) role(actor, 0);
            soulTicks(player, 0);
        }
        var context = new AbstractRitual.Context(player, claim);
        var result = run.advance(context, ready);
        if (ready) {
            ((State)player).sscExtras$soulAnimation(run.soulAnimation());
            soulTicks(player, run.presentationTicks());
        }
        if (result == sscextras.rituals.RitualStageRunner.Result.COMPLETE) run.complete(context);
        else if (result == sscextras.rituals.RitualStageRunner.Result.CANCELLED) cancel(player, claim);
    }

    static AbstractRitual createRitual(DrakeOutpostOwnership.Claim claim, DrakeRituals.Type type) {
        if (claim.ritualCheckpoint == null || !claim.ritualCheckpoint.type.equals(type.command())) {
            if (claim.ritualRun != null) claim.ritualRun.finish();
            claim.ritualCheckpoint = new DrakeRitualCheckpoint(type.command());
            claim.ritualUnavailable = false;
            claim.ritualRun = null;
        }
        if (claim.ritualRun == null || claim.ritualRun.status() == sscextras.events.NpcEvent.Status.FINISHED)
            claim.ritualRun = type.create(claim.ritualCheckpoint);
        return claim.ritualRun;
    }

    private static boolean ready(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        var run = claim.ritualRun;
        if (run == null || claim.attendants.size() != run.composition().size()) return false;
        for (int i = 0; i < run.composition().size(); i++) {
            if (!(player.getServerWorld().getEntity(claim.attendants.get(i)) instanceof PillagerEntity guard)
                    || !available(guard) || guard.squaredDistanceTo(run.position(claim, i)) > 1.44
                    || !guard.getVisibilityCache().canSee(player)) return false;
        }
        return true;
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
        boolean present = attendanceExcused(player, claim);
        if (!present) disobey(player);
        DrakeOutpostOwnership.get(player.getServer()).markDirty();
        return !present;
    }

    private static boolean attendanceExcused(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        if (!claim.world.equals(player.getWorld().getRegistryKey())) return false;
        if (Box.from(claim.stable).contains(player.getPos())) return true;
        if (player.getFirstPassenger() instanceof PillagerEntity rider && homeKeeper(rider, claim)) return true;
        if (DrakeLeashing.holder(player) instanceof PillagerEntity guide && homeKeeper(guide, claim)) {
            var capture = ((DrakeCaptureGoal.Captor)guide).sscExtras$captureGoal();
            return capture != null && capture.quarry() == player && capture.shouldContinue() || claim.attendants.contains(guide.getUuid());
        }
        return false;
    }

    private static boolean homeKeeper(PillagerEntity guard, DrakeOutpostOwnership.Claim claim) {
        if (guard instanceof DrakeVisitorEntity || !guard.isAlive() || guard.isRemoved() || guard.isAiDisabled()) return false;
        var home = ((DrakeStableNavigation)guard.getNavigation()).stable();
        return home != null && claim.matches(guard.getWorld(), home);
    }

    private static boolean atHay(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        return claim.world.equals(player.getWorld().getRegistryKey()) && claim.stall().contains(player.getPos())
                && player.getWorld().getBlockState(player.getBlockPos().down()).isOf(Blocks.HAY_BLOCK);
    }

    static Vec3d hayPosition(DrakeOutpostOwnership.Claim claim) { return Vec3d.ofBottomCenter(claim.bed()).add(.2, 0, 0); }

    private static void pin(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        var pos = hayPosition(claim);
        float yaw = DrakeStableLayout.outwardYaw(claim.stable, claim.gate());
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0;
        player.setSprinting(false); player.setSneaking(false);
        if (player.squaredDistanceTo(pos) > .01 || Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(player.getYaw() - yaw)) > .01
                || Math.abs(player.getPitch()) > .01)
            player.networkHandler.requestTeleport(pos.x, pos.y, pos.z, yaw, 0);
        player.setYaw(yaw); player.setPitch(0);
        player.setBodyYaw(yaw); player.setHeadYaw(yaw);
    }

    public static void hold(ServerPlayerEntity player) {
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim == null) { role(player, 0); return; }
        if (!player.isAlive() || (player.isCreative() && claim.commandRitual == null) || player.isSpectator() || EarthenDrake.stage(player) < 0
                || claim.ritualRun == null || claim.attendants.size() != claim.ritualRun.composition().size() || !atHay(player, claim) || player.squaredDistanceTo(hayPosition(claim)) > 16) {
            cancel(player, claim); return;
        }
        for (int i = 0; i < claim.ritualRun.composition().size(); i++) {
            if (!claim.ritualRun.composition().participants().get(i).escortRequired()) continue;
            var entity = player.getServerWorld().getEntity(claim.attendants.get(i));
            if (!(entity instanceof PillagerEntity guard) || !available(guard) || guard.squaredDistanceTo(player) > 16) {
                cancel(player, claim); return;
            }
        }
        pin(player, claim);
        if (ready(player, claim)) claim.ritualRun.tickAction(new AbstractRitual.Context(player, claim));
        else {
            claim.ritualRun.interruptAction();
            soulTicks(player, 0);
        }
        if (!shoeing(player) && claim.ritualTicks() > 0 && player.age % 2 == 0) for (int i = 0; i < 2; i++)
            player.getServerWorld().spawnParticles(StaticParams.PLAYER_TRANSFORM_PARTICLE,
                    player.getX() + (player.getRandom().nextDouble() - .5) * 1.4,
                    player.getY() + 1 + player.getRandom().nextDouble() * 1.5,
                    player.getZ() + (player.getRandom().nextDouble() - .5) * 1.4, 0, 0, -1, 0, 1);
    }

    static boolean available(PillagerEntity guard) {
        return available(guard, false);
    }

    private static boolean reachesRitual(PillagerEntity guard, ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        var body = guard.getRootVehicle();
        var navigation = (DrakeStableNavigation)guard.getNavigation();
        if (attendee(guard) == player && (!navigation.isIdle() || !body.isOnGround() && !body.isTouchingWater() && !body.isInLava())) return true;
        return navigation.reaches(player.getBlockPos()) && navigation.reaches(claim.bed());
    }

    static boolean available(PillagerEntity guard, boolean defending) {
        var battle = DrakeBattleGoal.of(guard);
        boolean collecting = battle != null && battle.pursuing(attendee(guard));
        return !(guard instanceof DrakeVisitorEntity) && guard.isAlive() && !guard.isRemoved() && !guard.isAiDisabled() && !guard.hasActiveRaid()
                && (!guard.hasVehicle() || collecting && guard.getVehicle() == battle.mount())
                && !guard.hasPassengers() && (defending || !DrakeFaction.fighting(guard))
                && (battle == null || battle.mount() == null || collecting)
                && ((DrakeCaptureGoal.Captor)guard).sscExtras$captureGoal().quarry() == null
                && ((DrakeFaction.EquipmentDisplay)guard).sscExtras$recruiting() == null;
    }

    static ServerPlayerEntity attendee(PillagerEntity guard) {
        if (!(guard.getWorld() instanceof ServerWorld world)) return null;
        var owner = DrakeOutpostOwnership.get(world.getServer()).assignments().owner(guard.getUuid());
        if (!(owner instanceof AbstractRitual run) || run.target() == null) return null;
        return world.getEntity(run.target()) instanceof ServerPlayerEntity player ? player : null;
    }

    static Vec3d attendancePosition(DrakeOutpostOwnership.Claim claim, int index) {
        return claim.ritualRun != null ? claim.ritualRun.position(claim, index)
                : sscextras.rituals.RitualComposition.threeEnforcers().position(hayPosition(claim),
                    DrakeStableLayout.inward(claim.stable, claim.gate()), index);
    }

    private static void cancel(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        if (!claim.attendants.isEmpty() && (restrained(player) || claim.ritualTicks() > 0)) hint(player, "ritual_interrupted");
        releaseAttendance(player, claim);
    }

    private static void releaseAttendance(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        if (claim.attendants.isEmpty()) {
            if (claim.ritualRun != null) claim.ritualRun.finish();
            role(player, 0); soulTicks(player, 0);
            return;
        }
        if (claim.ritualTicks() > 0 && player instanceof ServerPlayerEntity serverPlayer) DrakeRitualTransform.finish(serverPlayer);
        role(player, 0);
        soulTicks(player, 0);
        var world = player.getServer().getWorld(claim.world);
        if (world != null) for (var id : claim.attendants) {
            if (DrakeOutpostOwnership.get(player.getServer()).assignments().owner(id) == claim.ritualRun
                    && world.getEntity(id) instanceof PillagerEntity guard) {
                DrakeBattleGoal.of(guard).stopPursuit();
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
        claim.attendants.clear();
        if (claim.ritualRun != null) { claim.ritualRun.interruptAction(); claim.ritualRun.finish(); }
        claim.shoeingRitual = false;
        DrakeOutpostOwnership.get(player.getServer()).markDirty();
    }

    static void finishRitual(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        releaseAttendance(player, claim);
        claim.ritualCheckpoint = null; claim.ritualRun = null; claim.commandRitual = null;
        claim.ritualUnavailable = false;
        DrakeOutpostOwnership.get(player.getServer()).markDirty();
    }

    static void completeSoul(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim, boolean punishment) {
        if (!punishment && !claim.soulbound) {
            claim.previousSpawn.putString("World", player.getSpawnPointDimension().getValue().toString());
            if (player.getSpawnPointPosition() != null) claim.previousSpawn.putLong("Pos", player.getSpawnPointPosition().asLong());
            claim.previousSpawn.putFloat("Angle", player.getSpawnAngle());
            claim.previousSpawn.putBoolean("Forced", player.isSpawnForced());
        }
        if (!punishment) claim.soulbound = true;
        if (punishment) {
            DrakeFeralization.fullyFeralize(player);
            player.clearActiveItem();
            player.closeHandledScreen();
            DrakeFeralization.sync(player, claim);
        }
        syncSoul(player);
        if (claim.soulbound) {
            claim.soulboundStage = EarthenDrake.stage(player);
            SoulboundEquipment.enchant(player);
            player.setSpawnPoint(claim.world, claim.bed(), DrakeStableLayout.outwardYaw(claim.stable, claim.gate()), true, false);
        }
        finishRitual(player, claim);
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
        if (claim != null && (claim.soulbound || claim.feral) && !claim.awaitingRespawn && EarthenDrake.stage(player) >= 0 && form.getGroup() != EarthenDrake.GROUP) {
            clearCurse(player, claim);
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
            hint(player, claim.feral ? "body_reverted_feral" : "soul_freed");
        }
    }

    static void clearPermanentMount(ServerPlayerEntity player) {
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim != null) {
            if (claim.ritualCheckpoint != null && claim.ritualCheckpoint.type.equals(DrakeRituals.SOULBINDING.command())) finishRitual(player, claim);
            claim.goodTicks = claim.ritualHint = 0;
            claim.lastServiceTime = player.getWorld().getTimeOfDay();
            clearSoulbond(player, claim);
            if (claim.returning) DrakeFeralization.stop(player, claim);
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
        }
        SoulboundEquipment.clear(player);
        syncSoul(player);
    }

    static void clearFeralization(ServerPlayerEntity player) {
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim != null) {
            if (claim.ritualCheckpoint != null && claim.ritualCheckpoint.type.equals(DrakeRituals.FERALIZATION.command())) finishRitual(player, claim);
            claim.feral = false;
            claim.escapes = 0;
            claim.tryingToEscape = false;
            if (!claim.soulbound) claim.awaitingRespawn = false;
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
        }
        player.removeStatusEffect(BeastizationCatalyst.TOTAL_FERALIZED);
        ((DrakeFeralization.State)player).sscExtras$feral(false);
        ((DrakeFeralization.State)player).sscExtras$sentience(0);
        if (claim == null || !claim.returning || !DrakeRoaming.mustReturn(player, claim)) DrakeFeralization.stop(player, claim);
        DrakeFeralization.sync(player, claim);
        syncSoul(player);
    }

    static void clearCurse(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        finishRitual(player, claim);
        claim.shoeingDue = false;
        claim.shoeingViolations = 0;
        DrakeFeralization.sync(player, claim);
        DrakeFeralization.stop(player, claim);
        clearSoulbond(player, claim);
    }

    private static void clearSoulbond(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
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
        if (claim == null || !claim.soulbound && !claim.feral) return;
        claim.awaitingRespawn = true;
        if (!alive && claim.soulbound) player.setSpawnPoint(claim.world, claim.bed(), DrakeStableLayout.outwardYaw(claim.stable, claim.gate()), true, false);
    }

    public static void afterRespawn(ServerPlayerEntity player, boolean alive) {
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim == null) return;
        if (!claim.soulbound) {
            if (!alive && claim.feral && EarthenDrake.stage(player) >= 0) {
                DrakeRitualTransform.finish(player);
                FormAbilityManager.applyForm(player, net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.ORIGINAL_SHIFTER);
            }
            claim.awaitingRespawn = false;
            var form = FormAbilityManager.getForm(player);
            if (claim.feral && (form == net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.ORIGINAL_SHIFTER
                    || form == net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.ORIGINAL_BEFORE_ENABLE)) {
                DrakeOutpostOwnership.release(player);
            } else {
                DrakeFeralization.sync(player, claim);
                syncSoul(player);
                DrakeOutpostOwnership.get(player.getServer()).markDirty();
            }
            return;
        }
        FormAbilityManager.applyForm(player, EarthenDrake.FORMS[claim.soulboundStage]);
        claim.awaitingRespawn = false;
        syncSoul(player);
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

    static void hint(PlayerEntity player, String key) {
        player.sendMessage(Text.translatable("message.ssc-extras.drake." + key).formatted(Formatting.LIGHT_PURPLE), false);
    }
}
