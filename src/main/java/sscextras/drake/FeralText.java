package sscextras.drake;

import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.regex.Pattern;

public final class FeralText {
    private static final String STATIC_GIBBERISH = "ssc-extras:static_gibberish";
    private static final Text TAKEOVER = Text.translatable("message.ssc-extras.drake.feral_takeover");
    private static final Text RAW_FOOD = Text.translatable("item.ssc-extras.feral_raw_food");
    private static final Text OTHER_FOOD = Text.translatable("item.ssc-extras.feral_other_food");
    private record ReadableWords(String mountName, Pattern pattern) { }
    private static volatile ReadableWords readableWords;
    private FeralText() { }

    public static Text itemName(net.minecraft.item.ItemStack stack, Text original) {
        return DrakeFeralization.rawFood(stack) ? Text.translatable("item.ssc-extras.feral_raw_food")
                : stack.isFood() ? Text.translatable("item.ssc-extras.feral_other_food")
                : unreadable(original);
    }

    /** Mark only our unreadable text; ordinary Minecraft obfuscation remains animated. */
    public static Text unreadable(Text original) {
        return Text.literal(original.getString()).setStyle(unreadableStyle(original.getStyle()));
    }

    public static boolean staticGibberish(Style style) {
        return style.isObfuscated() && STATIC_GIBBERISH.equals(style.getInsertion());
    }

    private static Style unreadableStyle(Style style) {
        return style.withObfuscated(true).withHoverEvent(null).withClickEvent(null).withInsertion(STATIC_GIBBERISH);
    }

    public static OrderedText scramble(OrderedText original, String mountName) {
        return scramble(original, mountName, 1);
    }

    public static OrderedText scramble(OrderedText original, String mountName, float strength) {
        if (strength <= 0) return original;
        var text = new StringBuilder();
        var points = new IntArrayList();
        var styles = new ArrayList<Style>();
        original.accept((index, style, point) -> { text.appendCodePoint(point); points.add(point); styles.add(style); return true; });
        String value = text.toString();
        if (value.codePoints().noneMatch(Character::isLetter)
                || styles.stream().allMatch(FeralText::readableHint)
                || value.equals(TAKEOVER.getString())
                || value.equals(RAW_FOOD.getString())
                || value.equals(OTHER_FOOD.getString())) return original;
        var readable = new boolean[value.length()];
        var matches = readablePattern(mountName).matcher(value);
        while (matches.find()) java.util.Arrays.fill(readable, matches.start(), matches.end(), true);
        int offset = 0;
        for (int i = 0; i < points.size(); i++) {
            int point = points.getInt(i);
            var style = styles.get(i);
            int order = Math.floorMod(Integer.rotateLeft(point * 0x9e3779b9, i & 31) ^ i * 0x85ebca6b, 100);
            if (!readableHint(style)) {
                boolean hidden = !readable[offset] && (style.isObfuscated() || order < strength * 100);
                styles.set(i, hidden ? unreadableStyle(style)
                        : style.withObfuscated(false).withHoverEvent(null).withClickEvent(null).withInsertion(null));
            }
            offset += Character.charCount(point);
        }
        return visitor -> {
            int index = 0;
            for (int i = 0; i < points.size(); i++) {
                int point = points.getInt(i);
                if (!visitor.accept(index, styles.get(i), point)) return false;
                index += Character.charCount(point);
            }
            return true;
        };
    }

    private static Pattern readablePattern(String mountName) {
        var cached = readableWords;
        if (cached == null || !cached.mountName().equals(mountName)) {
            String words = "meat" + (mountName.isBlank() ? "" : "|" + Pattern.quote(mountName));
            cached = new ReadableWords(mountName,
                    Pattern.compile("(?iu)肉|(?<![\\p{L}\\p{N}_])(?:" + words + ")(?![\\p{L}\\p{N}_])"));
            readableWords = cached;
        }
        return cached.pattern();
    }

    private static boolean readableHint(Style style) {
        return DrakeFeralization.TAKEOVER_TEXT_MARKER.equals(style.getInsertion())
                || "ssc-extras:mental_hint".equals(style.getInsertion())
                || SentientCatalyst.HINT_MARKER.equals(style.getInsertion());
    }
}
