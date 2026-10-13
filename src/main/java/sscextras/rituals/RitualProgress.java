package sscextras.rituals;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RitualProgress {
    String stage = "";
    int elapsed, waiting;
    boolean finished;
    final Set<String> completed = new HashSet<>();

    public String stage() { return stage; }
    public int elapsed() { return elapsed; }
    public boolean finished() { return finished; }
    public boolean completed(String id) { return completed.contains(id); }

    /** Import a legacy linear timeline whose crossed boundaries already committed their effects. */
    public static RitualProgress fromTimeline(List<? extends RitualStage<?>> stages, int ticks) {
        if (stages.isEmpty() || ticks < 0) throw new IllegalArgumentException("Invalid legacy ritual timeline");
        var ids = new HashSet<String>();
        for (var stage : stages)
            if (stage.duration() <= 0 || !ids.add(stage.id())) throw new IllegalArgumentException("Timeline migration requires distinct, positive-duration stages");
        var progress = new RitualProgress();
        for (var stage : stages) {
            progress.stage = stage.id();
            progress.elapsed = Math.min(ticks, stage.duration());
            if (ticks < stage.duration()) return progress;
            progress.completed.add(stage.id());
            ticks -= stage.duration();
        }
        progress.finished = true;
        return progress;
    }

    public NbtCompound write() {
        var tag = new NbtCompound();
        tag.putString("Stage", stage); tag.putInt("Elapsed", elapsed); tag.putBoolean("Finished", finished);
        var commits = new NbtList();
        completed.stream().sorted().forEach(id -> commits.add(NbtString.of(id)));
        tag.put("Completed", commits);
        return tag;
    }

    public static RitualProgress read(NbtCompound tag) {
        var progress = new RitualProgress();
        progress.stage = tag.getString("Stage"); progress.elapsed = Math.max(0, tag.getInt("Elapsed"));
        progress.finished = tag.getBoolean("Finished");
        for (var id : tag.getList("Completed", NbtElement.STRING_TYPE)) progress.completed.add(id.asString());
        return progress;
    }
}
