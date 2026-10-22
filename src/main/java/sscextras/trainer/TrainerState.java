package sscextras.trainer;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import sscextras.player.PlayerStance;

/** The player owns anatomical progress; accessories never carry or transfer it. */
public final class TrainerState {
    public interface Owner {
        TrainerState sscExtras$trainer();
        int sscExtras$trainerHands();
        int sscExtras$trainerFeet();
        int sscExtras$trainerQuad();
        int sscExtras$trainerEquipment();
        void sscExtras$trainerSnapshot(int hands, int feet, int quad, int equipment);
    }
    public int handsTicks, feetTicks;
    public int quad, recoveryTicks, milestones;
    public final int[] activityTicks = new int[3];
    public net.minecraft.util.math.Vec3d previousPosition;
    public int lastHint = Integer.MIN_VALUE;
    public net.minecraft.util.Identifier autoRejectedForm;
    public int lastTick = Integer.MIN_VALUE;
    public PlayerStance.Request stance;

    public static TrainerState get(PlayerEntity player) { return ((Owner)player).sscExtras$trainer(); }
    public void write(NbtCompound root) {
        var nbt = new NbtCompound();
        nbt.putInt("Hands", handsTicks); nbt.putInt("Feet", feetTicks);
        nbt.putInt("Quad", quad); nbt.putInt("Milestones", milestones);
        root.put("SscExtrasQuadrupedalTrainer", nbt);
    }
    public void read(NbtCompound root) {
        var nbt = root.getCompound("SscExtrasQuadrupedalTrainer");
        handsTicks = MathHelper.clamp(nbt.getInt("Hands"), 0, QuadrupedalTrainer.MAX_PROGRESS);
        feetTicks = MathHelper.clamp(nbt.getInt("Feet"), 0, QuadrupedalTrainer.MAX_PROGRESS);
        // Version 1.31.6 stored anatomy only; keep its visible training progress.
        quad = MathHelper.clamp(nbt.contains("Quad") ? nbt.getInt("Quad") : (handsTicks + feetTicks) / 2, 0, QuadrupedalTrainer.MAX_PROGRESS);
        milestones = nbt.contains("Milestones") ? nbt.getInt("Milestones") : (quad >= 1200 ? 1 : 0) | (quad >= 12000 ? 2 : 0) | (quad == 24000 ? 4 : 0);
        lastTick = Integer.MIN_VALUE; autoRejectedForm = null;
        resetActivity(); recoveryTicks = 0; previousPosition = null;
    }
    public void resetActivity() { java.util.Arrays.fill(activityTicks, 0); }
    public void sync(PlayerEntity player, int equipment) {
        ((Owner)player).sscExtras$trainerSnapshot(units(handsTicks), units(feetTicks), quad, equipment);
    }
    private static int units(int ticks) {
        // Preserve the old anatomy scale without rounding up to completion.
        return ticks == QuadrupedalTrainer.MAX_PROGRESS ? 1200 : Math.min(1199, (ticks + 19) / 20);
    }
}
