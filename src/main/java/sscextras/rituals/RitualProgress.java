package sscextras.rituals;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import java.util.HashSet;
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
