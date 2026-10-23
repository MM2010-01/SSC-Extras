package sscextras.golem;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

public final class GolemStatues {
    public static final Identifier TOGGLE = DrakeGolem.id("golem_statue_toggle");
    private GolemStatues() { }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(TOGGLE, (server, player, handler, buffer, sender) -> {
            if (buffer.readableBytes() != 0) return;
            server.execute(() -> {
                var form = AbstractGolemForm.get(player);
                if (form != null && form.supportsStatueControl() && !sscextras.statue.StatueCrates.packed(player)) form.toggleStatue(player);
            });
        });
    }
}
