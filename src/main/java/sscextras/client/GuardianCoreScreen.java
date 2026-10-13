package sscextras.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import sscextras.guardian.GuardianCores;

public final class GuardianCoreScreen extends Screen {
    public GuardianCoreScreen() { super(Text.translatable("button.ssc-extras.guardian.give_up")); }

    @Override protected void init() {
        addDrawableChild(ButtonWidget.builder(title, button -> {
            button.active = false;
            ClientPlayNetworking.send(GuardianCores.GIVE_UP, PacketByteBufs.create());
        }).dimensions(width / 2 - 75, height - 55, 150, 20).build());
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { client.setScreen(new GameMenuScreen(true)); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && client.player != null && !isDragging()) {
            GuardianClient.look(client.player, dx, dy); return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
}
