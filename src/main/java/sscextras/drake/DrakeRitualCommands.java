package sscextras.drake;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;

public final class DrakeRitualCommands {
    private DrakeRitualCommands() { }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
            var rituals = CommandManager.literal("ritual");
            for (var ritual : DrakeSoulbinding.Ritual.values()) {
                rituals.then(CommandManager.literal(ritual.command).executes(context -> {
                    var source = context.getSource();
                    var error = DrakeSoulbinding.startRitual(source.getPlayerOrThrow(), ritual);
                    if (error != null) {
                        source.sendError(Text.translatable("message.ssc-extras.ritual_command." + error));
                        return 0;
                    }
                    source.sendFeedback(() -> Text.translatable("message.ssc-extras.ritual_command.started",
                            Text.translatable("ritual.ssc-extras." + ritual.command)), false);
                    return 1;
                }));
            }
            dispatcher.register(CommandManager.literal("ssc-extras").requires(source -> source.hasPermissionLevel(2)).then(rituals));
        });
    }
}
