package sscextras.golem;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;

/** Shared statue capability; each form retains ownership of its power and soul lifecycle. */
public abstract class AbstractGolemForm extends PlayerFormBase {
    protected AbstractGolemForm(Identifier id) { super(id); }
    public boolean supportsStatueControl() { return false; }
    public boolean statue(PlayerEntity player) { return false; }
    public boolean toggleStatue(ServerPlayerEntity player) { return false; }
    /** Update any form-owned absolute facing after the common saved pose is turned. */
    public void turnStatue(PlayerEntity player, float yaw) { }
    public static AbstractGolemForm get(PlayerEntity player) {
        return FormAbilityManager.getForm(player) instanceof AbstractGolemForm form ? form : null;
    }
}
