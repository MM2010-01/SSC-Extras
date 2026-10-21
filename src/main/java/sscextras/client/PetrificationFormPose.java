package sscextras.client;

import java.util.*;
import mod.azure.azurelib.cache.object.GeoBone;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import sscextras.petrification.Petrification;

/** Snapshots the existing SSC bones; never substitutes a model or a player body. */
public final class PetrificationFormPose {
    private record Bone(GeoBone bone, float x, float y, float z, float rx, float ry, float rz, float sx, float sy, float sz) {
        Bone(GeoBone bone) { this(bone, bone.getPosX(), bone.getPosY(), bone.getPosZ(), bone.getRotX(), bone.getRotY(), bone.getRotZ(), bone.getScaleX(), bone.getScaleY(), bone.getScaleZ()); }
        void apply() {
            bone.setPosX(x); bone.setPosY(y); bone.setPosZ(z);
            bone.setRotX(rx); bone.setRotY(ry); bone.setRotZ(rz);
            bone.setScaleX(sx); bone.setScaleY(sy); bone.setScaleZ(sz);
        }
    }
    private record Pose(float epoch, List<Bone> bones) { }
    private static final Map<PlayerEntity, Map<FormModel, Pose>> POSES = new WeakHashMap<>();
    private PetrificationFormPose() { }
    public static void apply(PlayerEntity player, FormModel model) {
        if (!Petrification.frozen(player)) { POSES.remove(player); return; }
        var models = POSES.computeIfAbsent(player, key -> new IdentityHashMap<>());
        float epoch = Petrification.state(player).animationAge;
        var pose = models.get(model);
        var bones = model.getAnimationProcessor().getRegisteredBones();
        if (pose == null || pose.epoch != epoch || pose.bones.size() != bones.size()
                || !pose.bones.isEmpty() && !bones.contains(pose.bones.get(0).bone)) {
            var snapshot = new ArrayList<Bone>();
            for (var bone : bones) snapshot.add(new Bone((GeoBone)bone));
            pose = new Pose(epoch, snapshot); models.put(model, pose);
        }
        pose.bones.forEach(Bone::apply);
    }
    public static void clear() { POSES.clear(); }
    public static void forget(PlayerEntity player) { POSES.remove(player); }
    public static void releaseCured() { POSES.keySet().removeIf(player -> !Petrification.frozen(player)); }
}
