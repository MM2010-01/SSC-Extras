package sscextras.client;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import java.util.function.Consumer;
final class GuardianForgeHud {
    private GuardianForgeHud() { }
    static boolean register() {
        if(!FabricLoader.getInstance().isModLoaded("connector"))return false;
        try {
            var event=Class.forName("net.minecraftforge.client.event.RenderGuiOverlayEvent");
            var pre=Class.forName("net.minecraftforge.client.event.RenderGuiOverlayEvent$Pre");
            var priority=Class.forName("net.minecraftforge.eventbus.api.EventPriority");
            var overlay=event.getMethod("getOverlay");var id=Class.forName("net.minecraftforge.client.gui.overlay.NamedGuiOverlay").getMethod("id");
            var context=event.getMethod("getGuiGraphics");var cancel=Class.forName("net.minecraftforge.eventbus.api.Event").getMethod("setCanceled",boolean.class);
            Consumer<Object> listener=value->{
                if(!GuardianClient.energy())return;
                try {
                    if(!id.invoke(overlay.invoke(value)).toString().equals("minecraft:food_level"))return;
                    var client=MinecraftClient.getInstance();
                    if(client.options.hudHidden||client.player.isCreative()||client.player.isSpectator())return;
                    GuardianClient.energyBar((DrawContext)context.invoke(value),client.getWindow().getScaledWidth(),client.getWindow().getScaledHeight());
                    cancel.invoke(value,true);
                } catch(ReflectiveOperationException exception) {throw new IllegalStateException("Guardian Forge energy display failed",exception);}
            };
            var bus=Class.forName("net.minecraftforge.common.MinecraftForge").getField("EVENT_BUS").get(null);
            Class.forName("net.minecraftforge.eventbus.api.IEventBus").getMethod("addListener",priority,boolean.class,Class.class,Consumer.class)
                    .invoke(bus,priority.getField("NORMAL").get(null),false,pre,listener);
            return true;
        } catch(ReflectiveOperationException exception) {throw new IllegalStateException("Guardian Forge energy display could not register",exception);}
    }
}
