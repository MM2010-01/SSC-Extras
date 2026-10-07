package sscextras.rituals;

public record SoulAnimationStage(String id, int duration, Motion motion, int fromForm, int toForm, int blendTicks) {
    public enum Motion { RISE, HOVER, RETURN }
    public SoulAnimationStage {
        if (id == null || id.isBlank() || duration <= 0 || motion == null || blendTicks <= 0)
            throw new IllegalArgumentException("Invalid soul animation stage");
    }
    public static SoulAnimationStage rise(String id, int duration, int form, int fadeTicks) {
        return new SoulAnimationStage(id, duration, Motion.RISE, form, form, fadeTicks);
    }
    public static SoulAnimationStage reshape(String id, int duration, int from, int to, int blendTicks) {
        return new SoulAnimationStage(id, duration, Motion.HOVER, from, to, blendTicks);
    }
    public static SoulAnimationStage returnToBody(String id, int duration, int form) {
        return new SoulAnimationStage(id, duration, Motion.RETURN, form, form, duration);
    }
}
