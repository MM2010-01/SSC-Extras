package sscextras.drake;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AbstractAnimStateController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateControllerDP.OneAnimController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateEnum;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimSystem;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimUtils;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormPhase;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_FeralBase;

public final class EarthenDrakeForm extends Form_FeralBase {
    private final int stage;
    private final AbstractAnimStateController hunchedIdle = animation("earthen_drake_hunched_idle");
    private final AbstractAnimStateController hunchedWalk = animation("earthen_drake_hunched_walk");
    private final DrakeQuadrupedAnimations quadruped = new DrakeQuadrupedAnimations();

    public EarthenDrakeForm(int stage) {
        super(EarthenDrake.id("earthen_drake_" + stage));
        this.stage = stage;
        setPhase(PlayerFormPhase.valueOf("PHASE_" + stage));
        setBodyType(stage >= 2 ? PlayerFormBodyType.FERAL : PlayerFormBodyType.NORMAL);
        setOverrideHandAnim(stage >= 2);
    }

    private static AbstractAnimStateController animation(String path) {
        return new OneAnimController(new AnimUtils.AnimationHolderData(EarthenDrake.id(path)));
    }

    @Environment(EnvType.CLIENT)
    @Override public Vec3d getCapeIdleLoc(AbstractClientPlayerEntity player) {
        return stage == 3 ? new Vec3d(0, player.isInSneakingPose() ? .66875 : .70, -.35)
                : super.getCapeIdleLoc(player);
    }

    @Environment(EnvType.CLIENT)
    @Override public float getCapeBaseRotateAngle(AbstractClientPlayerEntity player) {
        return stage == 3 ? (player.isInSneakingPose() ? 57 : 82) : super.getCapeBaseRotateAngle(player);
    }

    @Override public AbstractAnimStateController getAnimStateController(PlayerEntity player,
            AnimSystem.AnimSystemData data, Identifier stateId) {
        if (stage < 2) return null;
        var state = AnimStateEnum.getStateEnum(stateId);
        if (stage == 3) return quadruped.controller(state);
        if (stage == 2 && !EarthenDrake.onAllFours(player)) {
            if (state == AnimStateEnum.ANIM_STATE_WALK) return hunchedWalk;
            if (state == AnimStateEnum.ANIM_STATE_IDLE || state == AnimStateEnum.ANIM_STATE_USE_ITEM
                    || state == AnimStateEnum.ANIM_STATE_ATTACK || state == AnimStateEnum.ANIM_STATE_MINING) return hunchedIdle;
        }
        return super.getAnimStateController(player, data, stateId);
    }
}
