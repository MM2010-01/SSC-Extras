package sscextras.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Formatting;
import sscextras.effigy.Infusions;
import sscextras.effigy.EffigyScreenHandler;
import sscextras.effigy.InfusionSlot;

public final class EffigyScreen extends HandledScreen<EffigyScreenHandler> {
    private static final Identifier[] EMPTY_ICONS = {
            PlayerScreenHandler.EMPTY_HELMET_SLOT_TEXTURE, PlayerScreenHandler.EMPTY_CHESTPLATE_SLOT_TEXTURE,
            PlayerScreenHandler.EMPTY_LEGGINGS_SLOT_TEXTURE, PlayerScreenHandler.EMPTY_BOOTS_SLOT_TEXTURE,
            new Identifier("minecraft", "item/iron_sword"), new Identifier("minecraft", "item/iron_pickaxe"),
            new Identifier("minecraft", "item/iron_axe"), new Identifier("minecraft", "item/iron_shovel"),
            new Identifier("minecraft", "item/iron_hoe")
    };

    public EffigyScreen(EffigyScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 176;
        backgroundHeight = 207;
        playerInventoryTitleY = 113;
    }

    @Override protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF373047);
        context.fill(x + 1, y + 1, x + backgroundWidth - 1, y + backgroundHeight - 1, 0xFFC6C6C6);
        context.fill(x + 3, y + 3, x + backgroundWidth - 3, y + 109, 0xFFB4A9C5);
        for (var slot : handler.slots) {
            if (!slot.isEnabled()) continue;
            int sx = x + slot.x, sy = y + slot.y;
            context.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF373737);
            context.fill(sx, sy, sx + 17, sy + 17, 0xFFFFFFFF);
            context.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
            if (slot.id < EffigyScreenHandler.INFUSION_COUNT && !slot.hasStack()) {
                var sprite = client.getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).apply(EMPTY_ICONS[slot.id]);
                if (InfusionSlot.values()[slot.id].armor()) {
                    context.drawSprite(sx, sy, 0, 16, 16, sprite);
                } else {
                    context.drawSprite(sx, sy, 0, 16, 16, sprite, 0, 0, 0, 0.45F);
                }
            }
        }
    }

    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        super.drawForeground(context, mouseX, mouseY);
        if (!handler.anyVisible()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.ssc-extras.effigy.empty"), 88, 61, 0xFFFFFF);
        }
    }

    @Override protected java.util.List<Text> getTooltipFromItem(ItemStack stack) {
        var tooltip = new java.util.ArrayList<>(super.getTooltipFromItem(stack));
        if (focusedSlot != null && focusedSlot.id < EffigyScreenHandler.INFUSION_COUNT && client.player != null) {
            InfusionSlot slot = InfusionSlot.values()[focusedSlot.id];
            if (!Infusions.restricted(client.player, slot, stack)) {
                tooltip.add(Text.translatable("screen.ssc-extras.effigy.inactive").formatted(Formatting.GRAY));
            }
            if (!slot.armor()) tooltip.add(Text.translatable("screen.ssc-extras.effigy.empty_hand").formatted(Formatting.GRAY));
            if (slot == InfusionSlot.HOE || slot == InfusionSlot.SHOVEL || slot == InfusionSlot.AXE) {
                tooltip.add(Text.translatable("screen.ssc-extras.effigy.tool_use").formatted(Formatting.GRAY));
            }
        }
        return tooltip;
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
        if (focusedSlot != null && focusedSlot.id < EffigyScreenHandler.INFUSION_COUNT && !focusedSlot.hasStack()) {
            context.drawTooltip(textRenderer, Text.translatable(InfusionSlot.values()[focusedSlot.id].translation()), mouseX, mouseY);
        }
    }
}
