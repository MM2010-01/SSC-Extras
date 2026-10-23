package sscextras.statue;

import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

public final class StatueCrateItem extends Item {
    public StatueCrateItem() { super(new Item.Settings().maxCount(1)); }
    @Override public ActionResult useOnBlock(ItemUsageContext context) { return StatueCrates.place(context); }
    @Override public Text getName(ItemStack stack) {
        return StatueCrates.id(stack) == null ? super.getName(stack)
                : Text.translatable("item.ssc-extras.statue_package_crate.filled", stack.getNbt().getString("StatueName"));
    }
    @Override public void appendTooltip(ItemStack stack, World world, List<Text> lines, TooltipContext context) {
        lines.add(Text.translatable("tooltip.ssc-extras.statue_crate." + (StatueCrates.id(stack) == null ? "empty" : "filled")).formatted(Formatting.GRAY));
    }
}
