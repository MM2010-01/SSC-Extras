package sscextras.guardian;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import sscextras.collar.CollarSlots;
import java.util.List;

public final class GolemCoreItem extends AccessoryItem {
    public GolemCoreItem() { super(new Settings().maxCount(1)); }
    @Override public boolean canEquip(ItemStack stack, LivingEntity entity, SlotData slot) {
        var id = GuardianCores.id(stack);
        return entity instanceof PlayerEntity player && id != null && CollarSlots.isActive(slot)
                && (slot.slot().getPath().equals("core") || slot.slot().getPath().equals("chest/core"))
                && (id.equals(RuneboundGuardian.state(player).loadingCoreId)
                    || RuneboundGuardian.stage(player) == 3 && !GuardianCombat.locked(player)
                        && (player.getWorld().isClient || GuardianCores.valid(player.getServer(), stack)));
    }
    @Override public boolean canUnequip(ItemStack stack, LivingEntity entity, SlotData slot) { return true; }
    @Override public Text getName(ItemStack stack) {
        if (stack.hasNbt() && stack.getNbt().getBoolean("Abandoned")) return Text.translatable("item.ssc-extras.abandoned_golem_core");
        return stack.hasNbt() && stack.getNbt().contains("GuardianName")
                ? Text.translatable("item.ssc-extras.named_golem_core", stack.getNbt().getString("GuardianName")) : super.getName(stack);
    }
    @Override public void appendTooltip(ItemStack stack, World world, List<Text> lines, TooltipContext context) {
        lines.add(Text.translatable(stack.hasNbt() && stack.getNbt().getBoolean("Abandoned")
                ? "tooltip.ssc-extras.abandoned_golem_core" : "tooltip.ssc-extras.golem_core"));
    }
    @Override public boolean hasGlint(ItemStack stack) { return GuardianCores.id(stack) != null && !stack.getNbt().getBoolean("Abandoned"); }
}
