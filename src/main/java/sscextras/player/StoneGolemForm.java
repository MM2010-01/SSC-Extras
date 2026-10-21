package sscextras.player;

import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;

/** Stone material and physical traits, independent of a form's movement or soul lifecycle. */
public class StoneGolemForm extends PlayerFormBase {
    private final boolean solid;
    private final double suppliedArmor;

    public StoneGolemForm(Identifier id) { this(id, true, 0); }
    public StoneGolemForm(Identifier id, boolean solid, double suppliedArmor) {
        super(id); this.solid = solid; this.suppliedArmor = suppliedArmor;
    }
    public boolean solidStone() { return solid; }
    /** Armor already supplied by the form's native power; equipment is not included. */
    public double suppliedStoneArmor() { return suppliedArmor; }
}
