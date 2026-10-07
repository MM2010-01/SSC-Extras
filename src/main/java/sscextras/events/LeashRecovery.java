package sscextras.events;

import net.minecraft.util.math.Vec3d;

public final class LeashRecovery {
    public enum Phase { FOLLOWING, REGROUPING, FAILED }
    public static final int STALL_TICKS = 40, REGROUP_TICKS = 160;
    private Phase phase = Phase.FOLLOWING;
    private Vec3d lastProgress;
    private long lastTick = Long.MIN_VALUE, since;
    private int attempts;

    public Phase phase() { return phase; }
    public void reset() { phase = Phase.FOLLOWING; lastProgress = null; lastTick = Long.MIN_VALUE; attempts = 0; }
    public Phase sample(long tick, Vec3d target, Vec3d guide, boolean taut, boolean constrained, boolean grounded) {
        if (tick == lastTick || phase == Phase.FAILED) return phase;
        if (lastProgress == null || tick < lastTick || tick - lastTick > 20) { lastProgress = target; since = tick; }
        lastTick = tick;
        if (phase == Phase.REGROUPING) {
            if (grounded && guide.squaredDistanceTo(target) <= 4 && Math.abs(guide.y - target.y) <= 1.125) {
                phase = Phase.FOLLOWING; since = tick; lastProgress = target;
            } else if (tick - since >= REGROUP_TICKS) phase = Phase.FAILED;
        } else if (!taut || !constrained || target.squaredDistanceTo(lastProgress) >= .0625) {
            since = tick; lastProgress = target;
            if (!taut) attempts = 0;
        } else if (tick - since >= STALL_TICKS) {
            phase = ++attempts > 2 ? Phase.FAILED : Phase.REGROUPING;
            since = tick;
        }
        return phase;
    }
}
