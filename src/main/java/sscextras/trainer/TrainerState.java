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
        int sscExtras$trainerEquipment();
        void sscExtras$trainerSnapshot(int hands, int feet, int equipment);
    }
    public int handsTicks, feetTicks;
    public int lastTick = Integer.MIN_VALUE;
    public boolean announced;
    public PlayerStance.Request stance;

    public static TrainerState get(PlayerEntity player) { return ((Owner)player).sscExtras$trainer(); }
    public void write(NbtCompound root) {
        var nbt = new NbtCompound();
        nbt.putInt("Hands", handsTicks); nbt.putInt("Feet", feetTicks); nbt.putBoolean("Announced", announced);
        root.put("SscExtrasQuadrupedalTrainer", nbt);
    }
    public void read(NbtCompound root) {
        var nbt = root.getCompound("SscExtrasQuadrupedalTrainer");
        handsTicks = MathHelper.clamp(nbt.getInt("Hands"), 0, QuadrupedalTrainer.DAY);
        feetTicks = MathHelper.clamp(nbt.getInt("Feet"), 0, QuadrupedalTrainer.DAY);
        announced = nbt.getBoolean("Announced"); lastTick = Integer.MIN_VALUE;
    }
    public void sync(PlayerEntity player, int equipment) {
        // One visual step per second, with exact elapsed ticks saved separately.
        ((Owner)player).sscExtras$trainerSnapshot(units(handsTicks), units(feetTicks), equipment);
    }
    private static int units(int ticks) {
        // An early rounded-up visual step must never advertise completion before
        // the exact 24,000-tick server deadline.
        return ticks == QuadrupedalTrainer.DAY ? 1200 : Math.min(1199, (ticks + 19) / 20);
    }
}
