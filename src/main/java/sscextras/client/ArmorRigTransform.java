package sscextras.client;

import mod.azure.azurelib.rewrite.model.AzBone;
import org.joml.Vector3f;

/** AzureLib armor offsets in native player-model coordinates; callers own anatomical fit. */
public final class ArmorRigTransform {
    private ArmorRigTransform() { }
    public static void position(AzBone bone, Vector3f point, float x, float y) {
        if (bone != null) bone.updatePosition(point.x*16-x, y-point.y*16, point.z*16);
    }
    public static void scale(AzBone bone, float x, float y, float z) {
        if (bone != null) { bone.setScaleX(x); bone.setScaleY(y); bone.setScaleZ(z); }
    }
}
