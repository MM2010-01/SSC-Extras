package sscextras.client;

import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.render.form_render.DefaultModelAnimationSystem;
import sscextras.mixin.FormTailDataAccessor;
import sscextras.mixin.FormTailStateAccessor;
import mod.azure.azurelib.cache.object.GeoBone;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** SSC's existing drag simulation for body rigs that do not use its GeoBone renderer. */
public final class FormTailPhysics {
    public record Drag(float horizontal, float vertical) {
        public static final Drag NONE = new Drag(0, 0);
    }
    private static final DefaultModelAnimationSystem ANIMATION = new DefaultModelAnimationSystem();

    private FormTailPhysics() { }

    /** Keep an attached tail's native sway axes level while its parent torso tilts. */
    public static void counterTilt(GeoBone tail, GeoBone body) {
        if (tail == null || body == null) return;
        var rotation = new Quaternionf().rotationX(-body.getRotX()).rotateZ(tail.getRotZ())
                .rotateY(tail.getRotY()).rotateX(tail.getRotX()).getEulerAnglesZYX(new Vector3f());
        tail.setRotX(rotation.x); tail.setRotY(rotation.y); tail.setRotZ(rotation.z);
    }

    public static Drag sample(PlayerEntity player, float tickDelta) {
        // In SSC 1.9.2 these two hooks use only the player and tick delta.
        ANIMATION.beforeRender(null, null, null, player, 0, 0, tickDelta, 0, 0, 0);
        var state = (FormTailDataAccessor) FormTailStateAccessor.sscExtras$tails().get(player.getUuid());
        var drag = new Drag(state.sscExtras$horizontalDrag(), state.sscExtras$verticalDrag());
        ANIMATION.afterRender(null, null, null, player, 0, 0, tickDelta, 0, 0, 0);
        return drag;
    }

    public static void reset(PlayerEntity player) { FormTailStateAccessor.sscExtras$tails().remove(player.getUuid()); }
    public static void clear() { FormTailStateAccessor.sscExtras$tails().clear(); }
}
