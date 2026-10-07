package sscextras.rituals;

import net.minecraft.util.math.MathHelper;
import java.util.HashSet;
import java.util.List;

public record SoulAnimation(List<SoulAnimationStage> stages, float height, float opacity,
        float bobHeight, float bobSpeed, float returnScale) {
    public record Frame(int fromForm, int toForm, float blend, float height, float scale, float opacity) { }
    public SoulAnimation {
        stages = List.copyOf(stages);
        if (stages.isEmpty() || !Float.isFinite(height) || !Float.isFinite(opacity)
                || !Float.isFinite(bobHeight) || !Float.isFinite(bobSpeed) || !Float.isFinite(returnScale)
                || height < 0 || opacity < 0 || opacity > 1 || returnScale <= 0)
            throw new IllegalArgumentException("Invalid soul animation");
        var ids = new HashSet<String>();
        for (var stage : stages) if (!ids.add(stage.id())) throw new IllegalArgumentException("Duplicate soul stage");
    }
    public int duration() {
        int ticks = 0;
        for (var stage : stages) ticks = Math.addExact(ticks, stage.duration());
        return ticks;
    }
    public Frame sample(float time) {
        float local = MathHelper.clamp(time, 0, duration());
        var stage = stages.get(stages.size() - 1);
        for (var candidate : stages) {
            stage = candidate;
            if (local < stage.duration() || candidate == stages.get(stages.size() - 1)) break;
            local -= stage.duration();
        }
        float rise = stage.motion() == SoulAnimationStage.Motion.RISE ? smooth(local / stage.duration()) : 1;
        float returning = stage.motion() == SoulAnimationStage.Motion.RETURN ? smooth(local / stage.duration()) : 0;
        float alpha = opacity * (stage.motion() == SoulAnimationStage.Motion.RISE ? Math.min(1, local / stage.blendTicks()) : 1) * (1 - returning);
        float blend = stage.fromForm() == stage.toForm() ? 0 : smooth(local / stage.blendTicks());
        return new Frame(stage.fromForm(), stage.toForm(), blend,
                (height + bobHeight * MathHelper.sin(time * bobSpeed)) * rise * (1 - returning),
                1 + (returnScale - 1) * returning, alpha);
    }
    private static float smooth(float value) {
        value = MathHelper.clamp(value, 0, 1);
        return value * value * (3 - 2 * value);
    }
}
