package sscextras.golem;

import net.onixary.shapeShifterCurseFabric.player_form.*;
import sscextras.player.StoneGolemForm;

public final class DrakeGolemForm extends StoneGolemForm {
    public DrakeGolemForm() {
        super(DrakeGolem.id("drake_golem_3"));
        setPhase(PlayerFormPhase.PHASE_3); setBodyType(PlayerFormBodyType.FERAL);
        setOverrideHandAnim(true); setIgnoreCursedMoon(true); setIgnoreCatalyst(true);
    }
}
