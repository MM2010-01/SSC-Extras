package sscextras.drake;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class DrakeVisitorEntity extends PillagerEntity {
    private DrakeStablePiece stable;
    private BlockPos departure;
    private int elapsed, pets;

    public DrakeVisitorEntity(EntityType<? extends PillagerEntity> type, World world) { super(type, world); }

    public void visit(DrakeStablePiece stable, BlockPos departure) {
        this.stable = stable; this.departure = departure;
        ((DrakeStableNavigation)getNavigation()).home(stable);
    }

    public DrakeStablePiece stable() { return stable; }
    public boolean leaving() { return stable == null || pets >= 2 || elapsed >= 1200 || !getWorld().isDay() || hurtTime > 0; }
    public void finishedPet() { pets++; }

    @Override protected EntityNavigation createNavigation(World world) { return new DrakeStableNavigation(this, world); }

    @Override protected void initGoals() {
        super.initGoals();
        goalSelector.clear(goal -> true);
        targetSelector.clear(goal -> true);
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new DrakePetGoal(this));
    }

    @Override protected void mobTick() {
        elapsed++;
        if (hurtTime > 0) elapsed = Math.max(elapsed, 1200);
        if (elapsed >= 2400 || stable == null || departure == null) { discard(); return; }
        if (leaving()) {
            if (squaredDistanceTo(Vec3d.ofBottomCenter(departure)) < 4) { discard(); return; }
            if (elapsed % 20 == 0) getNavigation().startMovingTo(departure.getX() + .5, departure.getY(), departure.getZ() + .5, .7);
        }
    }

    @Override public ActionResult interactMob(PlayerEntity player, Hand hand) { return ActionResult.PASS; }
    @Override public boolean canImmediatelyDespawn(double distance) { return false; }
    @Override public boolean canJoinRaid() { return false; }
    @Override public boolean canLead() { return false; }
    @Override public boolean canTarget(net.minecraft.entity.LivingEntity target) { return false; }

    @Override public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("VisitElapsed", elapsed); nbt.putInt("VisitPets", pets);
        if (departure != null) nbt.putLong("VisitDeparture", departure.asLong());
        if (stable != null) {
            var box = stable.getBoundingBox();
            nbt.putIntArray("VisitStable", new int[]{box.getMinX(), box.getMinY(), box.getMinZ(), (box.getBlockCountX() - 1) / 7});
        }
    }

    @Override public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        elapsed = nbt.getInt("VisitElapsed"); pets = nbt.getInt("VisitPets");
        int[] home = nbt.getIntArray("VisitStable");
        if (home.length == 4 && home[3] >= 2 && home[3] <= 32 && nbt.contains("VisitDeparture"))
            visit(new DrakeStablePiece(home[0], home[1], home[2], home[3]), BlockPos.fromLong(nbt.getLong("VisitDeparture")));
    }
}
