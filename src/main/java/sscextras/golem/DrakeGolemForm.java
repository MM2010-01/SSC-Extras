package sscextras.golem;

import net.onixary.shapeShifterCurseFabric.player_form.*;
import sscextras.player.StoneGolemForm;

public final class DrakeGolemForm extends StoneGolemForm {
    public DrakeGolemForm() {
        super(DrakeGolem.id("drake_golem_3"));
        setPhase(PlayerFormPhase.PHASE_3); setBodyType(PlayerFormBodyType.FERAL);
        setOverrideHandAnim(true); setIgnoreCursedMoon(true); setIgnoreCatalyst(true);
    }
    @Override public boolean statue(net.minecraft.entity.player.PlayerEntity player) {
        var body = DrakeGolemBody.get(player); return body != null && body.statue();
    }
    @Override public boolean supportsStatueControl() { return true; }
    @Override public boolean toggleStatue(net.minecraft.server.network.ServerPlayerEntity player) {
        var body = DrakeGolemBody.get(player); return body != null && body.toggleStatue();
    }
}
