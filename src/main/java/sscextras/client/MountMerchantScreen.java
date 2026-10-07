package sscextras.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import org.joml.Quaternionf;
import sscextras.drake.MountMerchantScreenHandler;
import java.util.ArrayList;
import java.util.List;

public final class MountMerchantScreen extends HandledScreen<MountMerchantScreenHandler> {
    private final List<ButtonWidget> buy = new ArrayList<>(), confirm = new ArrayList<>();
    private ButtonWidget offer, next, back;
    private int scroll, rowTop, rowHeight, visibleRows;
    public MountMerchantScreen(MountMerchantScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title); backgroundWidth = 370; backgroundHeight = 296;
    }
    private static Text text(String key, Object... args) { return Text.translatable("screen.ssc-extras.merchant." + key, args); }
    private Text greeting() {
        return Text.translatable("message.ssc-extras.merchant.greeting." + new String[]{"human", "other", "drake", "drake", "feral"}[handler.dialogue()]);
    }
    @Override protected void init() {
        backgroundWidth = Math.min(370, width - 12); backgroundHeight = Math.min(296, height - 12);
        super.init(); buy.clear(); confirm.clear();
        rowTop = 43 + textRenderer.getWrappedLinesHeight(greeting(), backgroundWidth - 36) + 9;
        visibleRows = Math.max(1, Math.min(3, (backgroundHeight - rowTop - 54) / 45));
        rowHeight = (backgroundHeight - rowTop - 54) / visibleRows;
        for (int i = 0; i < handler.listings().size(); i++) {
            int index = i;
            buy.add(addDrawableChild(ButtonWidget.builder(text("buy_selected"), ignored -> click(MountMerchantScreenHandler.BUY + index))
                    .dimensions(x + backgroundWidth - 77, y + rowTop, 58, 20).build()));
        }
        offer = button("offer", MountMerchantScreenHandler.OFFER, backgroundHeight - 29);
        next = button("continue", MountMerchantScreenHandler.CONTINUE, backgroundHeight - 62);
        back = button("back", MountMerchantScreenHandler.NO, backgroundHeight - 32);
        confirm.add(button("yes", MountMerchantScreenHandler.YES, backgroundHeight - 94));
        confirm.add(button("no", MountMerchantScreenHandler.NO, backgroundHeight - 64));
        confirm.add(button("pretend", MountMerchantScreenHandler.PRETEND, backgroundHeight - 34));
        updateButtons();
    }
    private void click(int id) { client.interactionManager.clickButton(handler.syncId, id); }
    private ButtonWidget button(String label, int id, int offset) {
        return addDrawableChild(ButtonWidget.builder(text(label), ignored -> click(id)).dimensions(x + 18, y + offset, backgroundWidth - 36, 22).build());
    }
    private List<Integer> rows() {
        var result = new ArrayList<Integer>();
        for (int i = 0; i < handler.listings().size(); i++) if (handler.present(i)) result.add(i);
        return result;
    }
    private void updateButtons() {
        var rows = rows(); scroll = Math.min(scroll, Math.max(0, rows.size() - visibleRows));
        buy.forEach(button -> button.visible = false);
        for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows); i++) {
            int index = rows.get(i); var button = buy.get(index);
            button.visible = handler.phase() == 0; button.active = handler.canBuy(index);
            button.setY(y + rowTop + (i - scroll) * rowHeight + (rowHeight - 20) / 2);
        }
        offer.visible = handler.phase() == 0; offer.active = handler.canOffer();
        next.visible = handler.phase() == 1; back.visible = handler.phase() == 1 || handler.phase() == 3;
        confirm.forEach(button -> button.visible = handler.confirming());
    }
    @Override protected void handledScreenTick() { super.handledScreenTick(); updateButtons(); }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (handler.phase() == 0) { scroll = Math.max(0, scroll - (int)Math.signum(amount)); updateButtons(); return true; }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }
    @Override protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF332C24);
        context.fill(x + 2, y + 2, x + backgroundWidth - 2, y + backgroundHeight - 2, 0xFFE0D4BC);
        context.fill(x + 7, y + 7, x + backgroundWidth - 7, y + 32, 0xFF514431);
        if (handler.phase() != 0) return;
        var rows = rows();
        for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows); i++) {
            var row = handler.listings().get(rows.get(i)); int top = y + rowTop + (i - scroll) * rowHeight;
            context.fill(x + 14, top, x + backgroundWidth - 14, top + rowHeight - 3, 0xFFCEBEA0);
            var entity = client.world.getEntityById(row.entityId());
            if (entity instanceof LivingEntity living && entity.getUuid().equals(row.id())) {
                context.enableScissor(x + 16, top, x + 89, top + rowHeight - 3);
                drawMount(context, x + 51, top + rowHeight - 10, Math.min(29, rowHeight / 2), living);
                context.disableScissor();
            }
        }
    }
    private static void drawMount(DrawContext context, int x, int y, int scale, LivingEntity entity) {
        float yaw = entity.getYaw(), previousYaw = entity.prevYaw, pitch = entity.getPitch(), previousPitch = entity.prevPitch;
        float body = entity.bodyYaw, previousBody = entity.prevBodyYaw, head = entity.headYaw, previousHead = entity.prevHeadYaw;
        try {
            entity.setYaw(180); entity.prevYaw = entity.bodyYaw = entity.prevBodyYaw = entity.headYaw = entity.prevHeadYaw = 180;
            entity.setPitch(0); entity.prevPitch = 0;
            InventoryScreen.drawEntity(context, x, y, scale, new Quaternionf().rotationZ((float)Math.PI).rotateY(-(float)Math.PI / 3), null, entity);
        } finally {
            entity.setYaw(yaw); entity.prevYaw = previousYaw; entity.setPitch(pitch); entity.prevPitch = previousPitch;
            entity.bodyYaw = body; entity.prevBodyYaw = previousBody; entity.headYaw = head; entity.prevHeadYaw = previousHead;
        }
    }
    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawCenteredTextWithShadow(textRenderer, title, backgroundWidth / 2, 15, 0xFFF4DEAD);
        if (handler.confirming()) {
            context.drawTextWrapped(textRenderer, text("sure"), 18, 44, backgroundWidth - 36, 0xFF423522);
            context.drawTextWrapped(textRenderer, text("consequence"), 18, 66, backgroundWidth - 36, 0xFF423522);
        } else if (handler.phase() != 0) {
            String line = handler.phase() == 3 ? "other" : new String[]{"human", "other", "drake", "ready", "other"}[handler.dialogue()];
            var dialogue = Text.translatable("message.ssc-extras.merchant.offer." + line);
            context.drawTextWrapped(textRenderer, dialogue, 18, 46, backgroundWidth - 36, 0xFF423522);
        } else {
            context.drawTextWrapped(textRenderer, greeting(), 18, 43, backgroundWidth - 36, 0xFF423522);
            var rows = rows();
            if (rows.isEmpty()) context.drawText(textRenderer, text("sold_out"),
                    (backgroundWidth - textRenderer.getWidth(text("sold_out"))) / 2, rowTop + 20, 0xFF423522, false);
            for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows); i++) {
                var row = handler.listings().get(rows.get(i)); int top = rowTop + (i - scroll) * rowHeight;
                context.drawText(textRenderer, textRenderer.trimToWidth(row.name().getString(), backgroundWidth - 180), 90, top + 9, 0xFF423522, false);
                context.drawText(textRenderer, text("cost", handler.price(rows.get(i))), 90, top + 24, 0xFF423522, false);
            }
            context.drawText(textRenderer, text("emeralds", handler.emeralds()), 18, backgroundHeight - 44, 0xFF423522, false);
        }
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) { renderBackground(context); super.render(context, mouseX, mouseY, delta); }
}
