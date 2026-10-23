package sscextras.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.GlyphRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FontStorage.class)
public interface FeralFontStorageAccessor {
    @Accessor("charactersByWidth") Int2ObjectMap<IntList> sscExtras$charactersByWidth();
    @Accessor("blankGlyphRenderer") GlyphRenderer sscExtras$blankGlyphRenderer();
}
