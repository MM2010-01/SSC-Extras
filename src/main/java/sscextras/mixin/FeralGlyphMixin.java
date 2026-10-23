package sscextras.mixin;

import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.Glyph;
import net.minecraft.client.font.GlyphRenderer;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import sscextras.client.StaticGibberish;
import sscextras.drake.FeralText;

@Mixin(targets = "net.minecraft.client.font.TextRenderer$Drawer")
public abstract class FeralGlyphMixin {
    @Redirect(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/FontStorage;getObfuscatedGlyphRenderer(Lnet/minecraft/client/font/Glyph;)Lnet/minecraft/client/font/GlyphRenderer;"))
    private GlyphRenderer sscExtras$fixedGlyph(FontStorage storage, Glyph glyph, int index, Style style, int codePoint) {
        return FeralText.staticGibberish(style) ? StaticGibberish.glyph(storage, glyph, index, codePoint)
                : storage.getObfuscatedGlyphRenderer(glyph);
    }
}
