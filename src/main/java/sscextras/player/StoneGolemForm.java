package sscextras.player;

import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;

/** Stone material and physical traits, independent of a form's movement or soul lifecycle. */
public class StoneGolemForm extends sscextras.golem.AbstractGolemForm {
    private final boolean solid;
    private final double suppliedArmor;
    private final boolean petrificationResistant;

    public StoneGolemForm(Identifier id) { this(id, true, 0); }
    public StoneGolemForm(Identifier id, boolean solid, double suppliedArmor) {
        this(id, solid, suppliedArmor, solid);
    }
    public StoneGolemForm(Identifier id, boolean solid, double suppliedArmor, boolean petrificationResistant) {
        super(id); this.solid = solid; this.suppliedArmor = suppliedArmor; this.petrificationResistant = petrificationResistant;
    }
    public boolean solidStone() { return solid; }
    /** Partly stone resistant forms still admit swallowed potions and head submersion. */
    public boolean petrificationImmune(boolean concentrated) { return solid || petrificationResistant && !concentrated; }
    /** Armor already supplied by the form's native power; equipment is not included. */
    public double suppliedStoneArmor() { return suppliedArmor; }
}
