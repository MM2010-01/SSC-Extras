package sscextras.drake;

import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.regex.Pattern;

public final class FeralText {
    private FeralText() { }

    public static Text itemName(net.minecraft.item.ItemStack stack, Text original) {
        return DrakeFeralization.rawFood(stack) ? Text.literal("yummy!")
                : Text.literal(original.getString()).setStyle(Style.EMPTY.withObfuscated(true));
    }

    public static OrderedText scramble(OrderedText original, String mountName) {
        var text = new StringBuilder();
        var points = new ArrayList<Integer>();
        var styles = new ArrayList<Style>();
        original.accept((index, style, point) -> { text.appendCodePoint(point); points.add(point); styles.add(style); return true; });
        String value = text.toString();
        var readable = new boolean[value.length()];
        String words = "meat" + (mountName.isBlank() ? "" : "|" + Pattern.quote(mountName));
        var matches = Pattern.compile("(?iu)肉|(?<![\\p{L}\\p{N}_])(?:" + words + ")(?![\\p{L}\\p{N}_])").matcher(value);
        while (matches.find()) java.util.Arrays.fill(readable, matches.start(), matches.end(), true);
        if (value.equals("yummy!")) java.util.Arrays.fill(readable, true);
        return visitor -> {
            int offset = 0;
            for (int i = 0; i < points.size(); i++) {
                int point = points.get(i);
                var style = styles.get(i).withObfuscated(!readable[offset]).withHoverEvent(null).withClickEvent(null).withInsertion(null);
                if (!visitor.accept(offset, style, point)) return false;
                offset += Character.charCount(point);
            }
            return true;
        };
    }
}
