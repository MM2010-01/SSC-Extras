package sscextras.drake;

import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.MathHelper;

public final class DrakeAttention {
    public interface State {
        long sscExtras$petUntil();
        void sscExtras$petUntil(long tick);
        boolean sscExtras$petting();
        void sscExtras$petting(boolean petting);
        MobEntity sscExtras$petter();
        void sscExtras$petter(MobEntity petter);
        long sscExtras$nextPet();
        void sscExtras$nextPet(long tick);
        long sscExtras$calledUntil();
        void sscExtras$calledUntil(long tick);
    }

    private DrakeAttention() { }

    public static void register() {
        var action = new ActionFactory<Entity>(EarthenDrake.id("drake_call"), new SerializableData(),
                (data, entity) -> { if (entity instanceof PlayerEntity player) call(player); });
        Registry.register(ApoliRegistries.ENTITY_ACTION, action.getSerializerId(), action);
    }

    public static void call(PlayerEntity player) {
        if (!player.getWorld().isClient && EarthenDrake.stage(player) >= 0 && player.isAlive()
                && !player.isSpectator() && !player.isCreative())
            ((State)player).sscExtras$calledUntil(player.getWorld().getTime() + 600);
    }

    public static boolean called(LivingEntity drake) {
        return drake instanceof PlayerEntity && drake.getWorld().getTime() < ((State)drake).sscExtras$calledUntil();
    }

    public static boolean beingPetted(LivingEntity drake) {
        return drake.getWorld().getTime() < ((State)drake).sscExtras$petUntil();
    }

    public static boolean available(LivingEntity drake, MobEntity petter) {
        var state = (State)drake;
        var current = state.sscExtras$petter();
        return drake.getWorld().getTime() >= state.sscExtras$nextPet()
                && (current == null || current == petter || !current.isAlive() || current.isRemoved()
                    || current.getWorld() != drake.getWorld());
    }

    public static void finish(LivingEntity drake, MobEntity petter, boolean petted) {
        var state = (State)drake;
        if (state.sscExtras$petter() != petter) return;
        state.sscExtras$petUntil(0);
        state.sscExtras$petter(null);
        if (petted) state.sscExtras$nextPet(drake.getWorld().getTime() + 1200);
    }

    public static float wag(float time, int segment) {
        return MathHelper.sin(time * .5f - segment * .45f) * (segment == 0 ? .18f : .07f);
    }
}
