package sscextras.guardian;

/** Shared reserve math; each player component or resident entity remains its own saved state owner. */
public final class GuardianEnergy {
    public static final double PERMANENT_CAPACITY = 200, PEDESTAL_RECHARGE = .025, RESTART = 2;
    public static final int SUNLIGHT_ESCALATION_TICKS = 600;
    private GuardianEnergy() { }
    public static double clamp(double energy, double capacity) {
        return Double.isFinite(energy) ? net.minecraft.util.math.MathHelper.clamp(energy, 0, capacity) : 0;
    }
    public static double activity(int stage, double distance, boolean sprinting, boolean flying) {
        return 100.0 / 24000 + distance * (sprinting ? .008 : .003) + (flying ? stage == 3 ? .03 : .06 : 0);
    }
    public static double sunlight(double capacity, int ticks) {
        return ticks <= 0 ? 0 : capacity * (ticks > SUNLIGHT_ESCALATION_TICKS ? .04 : .02) / 20;
    }
}
