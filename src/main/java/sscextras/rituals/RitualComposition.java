package sscextras.rituals;

import net.minecraft.util.math.Vec3d;
import java.util.List;

public record RitualComposition(List<Participant> participants) {
    public record Participant(String slot, Vec3d offset, boolean escortRequired) {
        public Participant {
            if (slot == null || slot.isBlank() || offset == null) throw new IllegalArgumentException("Invalid ritual participant");
        }
    }
    public RitualComposition {
        participants = List.copyOf(participants);
        if (participants.isEmpty() || participants.stream().map(Participant::slot).distinct().count() != participants.size())
            throw new IllegalArgumentException("Ritual requires distinct participant slots");
    }
    public static RitualComposition threeEnforcers() {
        return new RitualComposition(List.of(
                new Participant("guide", new Vec3d(-1.25, 0, .1), true),
                new Participant("assistant", new Vec3d(1.25, 0, .1), true),
                new Participant("specialist", new Vec3d(0, 0, -2.25), false)));
    }
    public int size() { return participants.size(); }
    public int index(String slot) {
        for (int i = 0; i < size(); i++) if (participants.get(i).slot().equals(slot)) return i;
        throw new IllegalArgumentException("Unknown participant " + slot);
    }
    public Vec3d position(Vec3d origin, int inward, int index) {
        var offset = participants.get(index).offset();
        return origin.add(offset.x, offset.y, offset.z * inward);
    }
}
