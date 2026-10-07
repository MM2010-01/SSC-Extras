package sscextras.drake;

import sscextras.rituals.SoulAnimation;
import sscextras.rituals.SoulAnimationStage;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public final class DrakeSoulAnimations {
    private static final Map<String, SoulAnimation> ANIMATIONS = new HashMap<>();
    public static final String TRANSFORMATION_ID = "ssc-extras:drake_transformation";
    public static final SoulAnimation TRANSFORMATION = register(TRANSFORMATION_ID, new SoulAnimation(List.of(
            SoulAnimationStage.rise("raise_soul", 60, -1, 18),
            SoulAnimationStage.reshape("reshape_0", 60, -1, 0, 30),
            SoulAnimationStage.reshape("reshape_1", 60, 0, 1, 30),
            SoulAnimationStage.reshape("reshape_2", 60, 1, 2, 30),
            SoulAnimationStage.reshape("reshape_3", 60, 2, 3, 30),
            SoulAnimationStage.returnToBody("return_soul", 60, 3)), 2.2f, .38f, .045f, .07f, .35f));
    private DrakeSoulAnimations() { }
    public static SoulAnimation register(String id, SoulAnimation animation) {
        if (id == null || id.isBlank() || animation == null) throw new IllegalArgumentException("Invalid soul animation registration");
        for (var stage : animation.stages())
            if (stage.fromForm() < -1 || stage.fromForm() > 3 || stage.toForm() < -1 || stage.toForm() > 3)
                throw new IllegalArgumentException("Unknown drake soul form");
        if (ANIMATIONS.putIfAbsent(id, animation) != null) throw new IllegalArgumentException("Duplicate soul animation " + id);
        return animation;
    }
    public static SoulAnimation get(String id) { return ANIMATIONS.get(id); }
}
