package sscextras.drake;

import net.minecraft.nbt.NbtCompound;
import sscextras.rituals.RitualProgress;

public final class DrakeRitualCheckpoint {
    public final String type;
    public final RitualProgress stages;
    private int version = 1;
    private NbtCompound unsupportedData;
    int catalystsFed;
    int feedingTicks;
    boolean commanded;

    public DrakeRitualCheckpoint(String type) { this(type, new RitualProgress()); }
    private DrakeRitualCheckpoint(String type, RitualProgress stages) { this.type = type; this.stages = stages; }
    public int catalystsFed() { return catalystsFed; }
    public boolean supported() { return version == 1; }
    public NbtCompound write() {
        if (unsupportedData != null) return unsupportedData.copy();
        var tag = new NbtCompound(); tag.putInt("Version", version); tag.putString("Type", type); tag.put("Stages", stages.write());
        tag.putInt("CatalystsFed", catalystsFed);
        tag.putBoolean("Commanded", commanded);
        return tag;
    }
    public static DrakeRitualCheckpoint read(NbtCompound tag) {
        var checkpoint = new DrakeRitualCheckpoint(tag.getString("Type"), RitualProgress.read(tag.getCompound("Stages")));
        checkpoint.version = tag.contains("Version") ? tag.getInt("Version") : 1;
        if (!checkpoint.supported()) checkpoint.unsupportedData = tag.copy();
        checkpoint.catalystsFed = Math.max(0, tag.getInt("CatalystsFed"));
        checkpoint.commanded = tag.getBoolean("Commanded");
        return checkpoint;
    }
}
