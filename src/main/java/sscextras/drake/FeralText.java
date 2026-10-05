package sscextras.drake;

import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.regex.Pattern;

public final class FeralText {
    private FeralText() { }

    public static Text itemName(net.minecraft.item.ItemStack stack, Text original) {
        return DrakeFeralization.rawFood(stack) ? Text.translatable("item.ssc-extras.feral_raw_food")
                : Text.literal(original.getString()).setStyle(Style.EMPTY.withObfuscated(true));
    }

    public static OrderedText scramble(OrderedText original, String mountName) {
        return scramble(original, mountName, 1);
    }

    public static OrderedText scramble(OrderedText original, String mountName, float strength) {
        if (strength <= 0) return original;
        var text = new StringBuilder();
        var points = new ArrayList<Integer>();
        var styles = new ArrayList<Style>();
        original.accept((index, style, point) -> { text.appendCodePoint(point); points.add(point); styles.add(style); return true; });
        String value = text.toString();
        if (value.codePoints().noneMatch(Character::isLetter)
                || value.equals(Text.translatable("message.ssc-extras.drake.feral_takeover").getString())
                || value.equals(Text.translatable("item.ssc-extras.feral_raw_food").getString())) return original;
        var readable = new boolean[value.length()];
        String words = "meat" + (mountName.isBlank() ? "" : "|" + Pattern.quote(mountName));
        var matches = Pattern.compile("(?iu)肉|(?<![\\p{L}\\p{N}_])(?:" + words + ")(?![\\p{L}\\p{N}_])").matcher(value);
        while (matches.find()) java.util.Arrays.fill(readable, matches.start(), matches.end(), true);
        return visitor -> {
            int offset = 0;
            for (int i = 0; i < points.size(); i++) {
                int point = points.get(i);
                int order = Math.floorMod(Integer.rotateLeft(point * 0x9e3779b9, i & 31) ^ i * 0x85ebca6b, 100);
                var style = styles.get(i).withObfuscated(!readable[offset] && (styles.get(i).isObfuscated() || order < strength * 100))
                        .withHoverEvent(null).withClickEvent(null).withInsertion(null);
                if (!visitor.accept(offset, style, point)) return false;
                offset += Character.charCount(point);
            }
            return true;
        };
    }
}
