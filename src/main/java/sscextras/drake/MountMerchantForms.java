package sscextras.drake;

import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import sscextras.collar.CollarSlots;
import sscextras.collar.Collars;
import sscextras.collar.TamingCollar;

public final class MountMerchantForms {
    private MountMerchantForms() { }
    public static boolean convertible(PlayerEntity player) {
        var form = FormAbilityManager.getForm(player);
        return EarthenDrake.stage(player) >= 0 || form == RegPlayerForms.ORIGINAL_SHIFTER || form == RegPlayerForms.ORIGINAL_BEFORE_ENABLE;
    }
    public static int dialogue(PlayerEntity player) {
        int stage = EarthenDrake.stage(player);
        if (stage >= 0) return stage >= 2 ? 3 : 2;
        if (convertible(player)) return 0;
        return FormAbilityManager.getForm(player).getBodyType() == PlayerFormBodyType.FERAL || DrakeFeralization.feral(player) ? 4 : 1;
    }
    public static String recruit(PlayerEntity player) {
        if (!convertible(player)) return null;
        if (TamingCollar.worn(player) || CollarSlots.get(player).stream().anyMatch(slot -> slot.stack().isOf(Collars.CURSED)
                && Collars.infusion(slot.stack()) == EarthenDrake.CURSE)) return "lost";
        if (EarthenDrake.stage(player) == 3 || EarthenDrake.stage(player) >= 0 && DrakeFeralization.feral(player)) return "return";
        if (!DrakeEquipment.equipped(player, DrakeEquipment.REINS).isEmpty() && !DrakeEquipment.equipped(player, DrakeEquipment.SADDLE).isEmpty())
            return EarthenDrake.stage(player) >= 2 ? "return" : "recruit";
        return null;
    }
    public static String name(PlayerEntity player) {
        var name = Collars.mountName(player); return name.isEmpty() ? player.getName().getString() : name;
    }
}
