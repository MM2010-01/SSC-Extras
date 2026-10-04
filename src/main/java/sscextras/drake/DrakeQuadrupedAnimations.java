package sscextras.drake;

import net.onixary.shapeShifterCurseFabric.player_animation.v3.AbstractAnimStateController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateControllerDP.OneAnimController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateControllerDP.SwimAnimController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateControllerDP.UseItemAnimControllerPro;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateControllerDP.WithSneakAnimController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateEnum;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimUtils;

/** SSC selects and blends these states; the animations use the authored quadruped rest pose. */
final class DrakeQuadrupedAnimations {
    private final AbstractAnimStateController idle = new WithSneakAnimController(data("idle"), data("sneak_idle"));
    private final AbstractAnimStateController walk = new WithSneakAnimController(data("walk"), data("sneak_walk"));
    private final AbstractAnimStateController run = new WithSneakAnimController(data("run"), data("sneak_walk"));
    private final AbstractAnimStateController use = new UseItemAnimControllerPro(data("idle"), data("walk"), data("sneak_idle"), data("sneak_walk"));
    private final AbstractAnimStateController swim = new SwimAnimController(data("float"), data("swim"));
    private final AbstractAnimStateController attack = one("attack"), dig = one("dig"), jump = one("jump"),
            fall = one("fall"), climb = one("climb"), sleep = one("sleep"), fly = one("fly");

    private static AnimUtils.AnimationHolderData data(String name) {
        return new AnimUtils.AnimationHolderData(EarthenDrake.id("earthen_drake_quad_" + name));
    }

    private static AbstractAnimStateController one(String name) { return new OneAnimController(data(name)); }

    AbstractAnimStateController controller(AnimStateEnum state) {
        if (state == null) return idle;
        return switch (state) {
            case ANIM_STATE_WALK -> walk;
            case ANIM_STATE_SPRINT -> run;
            case ANIM_STATE_USE_ITEM -> use;
            case ANIM_STATE_SWIM -> swim;
            case ANIM_STATE_ATTACK -> attack;
            case ANIM_STATE_MINING -> dig;
            case ANIM_STATE_JUMP -> jump;
            case ANIM_STATE_FALL -> fall;
            case ANIM_STATE_CLIMB -> climb;
            case ANIM_STATE_SLEEP -> sleep;
            case ANIM_STATE_FLYING, ANIM_STATE_FALL_FLYING -> fly;
            default -> idle;
        };
    }
}
