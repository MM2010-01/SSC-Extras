package sscextras.player;

import java.util.Locale;
import net.minecraft.text.Text;

public enum IntLevel {
    FERAL, DUMB, SIMPLE, SUB_PAR, REGULAR;

    public boolean permits(IntLevel required) { return ordinal() >= required.ordinal(); }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public Text text() { return Text.translatable("int.ssc-extras." + id()); }
    public static IntLevel fromId(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "steam_age", "futuristic" -> REGULAR; // Existing saves and data packs.
            default -> valueOf(id.toUpperCase(Locale.ROOT));
        };
    }
    public static IntLevel fromOrdinal(int value) { return values()[Math.max(0, Math.min(values().length - 1, value))]; }
    public static IntLevel lower(IntLevel a, IntLevel b) { return a.ordinal() < b.ordinal() ? a : b; }
    public static IntLevel higher(IntLevel a, IntLevel b) { return a.ordinal() > b.ordinal() ? a : b; }
}
