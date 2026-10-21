package sscextras.drake;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import sscextras.collar.CleansingKeyItem;
import sscextras.guardian.GuardianRitual;
import java.util.Locale;
import java.util.function.ToIntBiFunction;

public final class DrakeRitualCommands {
    private DrakeRitualCommands() { }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
            var rituals = CommandManager.literal("ritual");
            for (var ritual : DrakeRituals.values()) {
                rituals.then(withPlayer(ritual.command(), (source, player) -> {
                    var error = DrakeSoulbinding.startRitual(player, ritual);
                    if (error != null) {
                        source.sendError(Text.empty().append(player.getDisplayName()).append(": ")
                                .append(Text.translatable("message.ssc-extras.ritual_command." + error)));
                        return 0;
                    }
                    source.sendFeedback(() -> Text.translatable("message.ssc-extras.ritual_command.started",
                            Text.translatable("ritual.ssc-extras." + ritual.command()), player.getDisplayName()), false);
                    return 1;
                }));
            }
            rituals.then(withPlayer("guardianawakening", (source, player) -> {
                var error = GuardianRitual.startCommand(player);
                if (error != null) {
                    source.sendError(Text.empty().append(player.getDisplayName()).append(": ")
                            .append(Text.translatable("message.ssc-extras.ritual_command." + error)));
                    return 0;
                }
                source.sendFeedback(() -> Text.translatable("message.ssc-extras.ritual_command.guardian_started",
                        Text.translatable("ritual.ssc-extras.guardianawakening"), player.getDisplayName()), false);
                return 1;
            }));
            var clear = CommandManager.literal("clear");
            for (var effect : new String[]{"PermanentMount", "Feralization"}) {
                for (var command : new String[]{effect, effect.toLowerCase(Locale.ROOT)}) {
                    clear.then(withPlayer(command, (source, player) -> {
                        if (effect.equals("PermanentMount")) DrakeSoulbinding.clearPermanentMount(player);
                        else DrakeSoulbinding.clearFeralization(player);
                        source.sendFeedback(() -> Text.translatable("message.ssc-extras.clear_command.cleared",
                                Text.translatable("special_effect.ssc-extras." + effect.toLowerCase(Locale.ROOT)),
                                player.getDisplayName()), false);
                        return 1;
                    }));
                }
            }
            var purge = withPlayer("purge-cursed-equipment", (source, player) -> {
                int removed = CleansingKeyItem.purgeCursedEquipment(player);
                if (removed == 0) {
                    source.sendFeedback(() -> Text.translatable("message.ssc-extras.purge_command.empty",
                            player.getDisplayName()), false);
                } else {
                    source.sendFeedback(() -> Text.translatable("message.ssc-extras.purge_command.removed",
                            removed, player.getDisplayName()), false);
                }
                return removed;
            });
            var intelligence = CommandManager.literal("int").then(withPlayer("get", (source, player) -> {
                source.sendFeedback(() -> Text.translatable("message.ssc-extras.int.current", player.getDisplayName(),
                        sscextras.player.PlayerMind.baseInt(player).text(), sscextras.player.PlayerMind.points(player),
                        sscextras.player.PlayerMind.effectiveInt(player).text(), sscextras.player.PlayerMind.effectivePoints(player))
                        .styled(style -> style.withInsertion("ssc-extras:mental_hint")), false);
                return sscextras.player.PlayerMind.effectiveInt(player).ordinal();
            }));
            var setInt = CommandManager.literal("set");
            for (var level : sscextras.player.IntLevel.values()) setInt.then(withPlayer(level.id(), (source, player) -> {
                sscextras.player.PlayerMind.setBaseInt(player, level);
                source.sendFeedback(() -> Text.translatable("message.ssc-extras.int.current", player.getDisplayName(),
                        level.text(), sscextras.player.PlayerMind.points(player),
                        sscextras.player.PlayerMind.effectiveInt(player).text(), sscextras.player.PlayerMind.effectivePoints(player))
                        .styled(style -> style.withInsertion("ssc-extras:mental_hint")), false);
                return 1;
            }));
            var points = CommandManager.argument("points", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, sscextras.player.PlayerMind.MAX_POINTS));
            points.executes(context -> setPoints(context.getSource(), defaultPlayer(context.getSource()),
                    com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "points")))
                    .then(CommandManager.argument("player", EntityArgumentType.player()).executes(context ->
                            setPoints(context.getSource(), EntityArgumentType.getPlayer(context, "player"),
                                    com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "points"))));
            dispatcher.register(CommandManager.literal("ssc-extras").requires(source -> source.hasPermissionLevel(2))
                    .then(rituals).then(clear).then(purge).then(intelligence.then(setInt)
                            .then(CommandManager.literal("points").then(points))));
        });
    }

    private static int setPoints(ServerCommandSource source, ServerPlayerEntity player, double points) {
        sscextras.player.PlayerMind.setPoints(player, points);
        source.sendFeedback(() -> Text.translatable("message.ssc-extras.int.current", player.getDisplayName(),
                sscextras.player.PlayerMind.baseInt(player).text(), sscextras.player.PlayerMind.points(player),
                sscextras.player.PlayerMind.effectiveInt(player).text(), sscextras.player.PlayerMind.effectivePoints(player))
                .styled(style -> style.withInsertion("ssc-extras:mental_hint")), false);
        return 1;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> withPlayer(String literal,
            ToIntBiFunction<ServerCommandSource, ServerPlayerEntity> action) {
        return CommandManager.literal(literal)
                .executes(context -> action.applyAsInt(context.getSource(), defaultPlayer(context.getSource())))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(context -> action.applyAsInt(context.getSource(),
                                EntityArgumentType.getPlayer(context, "player"))));
    }

    private static ServerPlayerEntity defaultPlayer(ServerCommandSource source) throws CommandSyntaxException {
        if (source.getEntity() instanceof ServerPlayerEntity player) return player;
        return EntityArgumentType.player().parse(new StringReader("@p")).getPlayer(source);
    }
}
