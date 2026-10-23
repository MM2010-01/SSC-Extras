package sscextras.client;

import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.Glyph;
import net.minecraft.client.font.GlyphRenderer;
import net.minecraft.util.math.MathHelper;
import sscextras.mixin.FeralFontStorageAccessor;

/** Fixed selection from the current font's native obfuscation width bucket. */
public final class StaticGibberish {
    private StaticGibberish() { }

    public static GlyphRenderer glyph(FontStorage storage, Glyph original, int index, int codePoint) {
        var font = (FeralFontStorageAccessor)storage;
        var choices = font.sscExtras$charactersByWidth().get(MathHelper.ceil(original.getAdvance(false)));
        if (choices == null || choices.isEmpty()) return font.sscExtras$blankGlyphRenderer();
        int seed = Integer.rotateLeft(codePoint * 0x9e3779b9, 13) ^ index * 0x85ebca6b;
        int choice = Math.floorMod(seed, choices.size());
        // Avoid revealing the source character when another native candidate exists.
        if (choices.size() > 1 && choices.getInt(choice) == codePoint) choice = (choice + 1) % choices.size();
        return storage.getGlyphRenderer(choices.getInt(choice));
    }
}
