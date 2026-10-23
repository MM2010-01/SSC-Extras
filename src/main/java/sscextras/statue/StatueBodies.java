package sscextras.statue;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import sscextras.golem.AbstractGolemForm;
import sscextras.guardian.GuardianShellEntity;
import sscextras.petrification.Petrification;

/** Shared physical statue queries; material, potion tier and golem power remain separate. */
public final class StatueBodies {
    private StatueBodies() { }
    public static boolean golem(LivingEntity entity) {
        if (entity instanceof GuardianShellEntity shell) return shell.statue();
        if (!(entity instanceof PlayerEntity player)) return false;
        var form = AbstractGolemForm.get(player);
        return form != null && form.statue(player);
    }
    public static boolean locked(LivingEntity entity) {
        return StatueCrates.packed(entity) || Petrification.frozen(entity)
                || entity.isAlive() && !entity.isSpectator() && golem(entity);
    }
    public static boolean falls(LivingEntity entity) {
        return !StatueCrates.packed(entity) && (Petrification.stone(entity) || entity.isAlive() && !entity.isSpectator() && golem(entity));
    }
    public static boolean packable(LivingEntity entity) {
        return entity.isAlive() && !entity.isSpectator() && !StatueCrates.packed(entity)
                && !entity.hasVehicle() && !entity.hasPassengers() && (Petrification.stone(entity) || golem(entity));
    }
}
