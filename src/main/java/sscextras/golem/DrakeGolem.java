package sscextras.golem;

import io.github.apace100.apoli.registry.ApoliRegistries;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.*;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import sscextras.player.PlayerStance;

/** The approved mature stone body; earlier stages and Guardian soul mechanics are separate work. */
public final class DrakeGolem {
    public static final PlayerFormBase FORM = RegPlayerForms.registerPlayerForm(new DrakeGolemForm());
    public static final PlayerFormGroup GROUP = RegPlayerForms.registerPlayerFormGroup(
            new PlayerFormGroup(id("drake_golem")).addForm(FORM, 3));
    private DrakeGolem() { }
    public static Identifier id(String path) { return new Identifier("ssc-extras", path); }
    public static boolean is(PlayerEntity player) { return FormAbilityManager.getForm(player) == FORM; }
    public static void register() {
        var factory = DrakeGolemBody.factory();
        Registry.register(ApoliRegistries.POWER_FACTORY, factory.getSerializerId(), factory);
        PlayerStance.registerForm(FORM.FormID, new PlayerStance.Profile() {
            public boolean supports(PlayerEntity player, PlayerStance.Stance stance) { return stance != PlayerStance.Stance.UPRIGHT; }
            public PlayerStance.Stance current(PlayerEntity player) { return PlayerStance.Stance.QUADRUPEDAL; }
            public void refresh(net.minecraft.server.network.ServerPlayerEntity player) {
                var body = DrakeGolemBody.get(player); if (body != null) body.resize();
            }
        });
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            var command = net.minecraft.server.command.CommandManager.literal("drake_golem").requires(source -> source.hasPermissionLevel(2));
            var targets = net.minecraft.server.command.CommandManager.argument("targets", net.minecraft.command.argument.EntityArgumentType.players());
            for (boolean weathered : new boolean[]{false, true}) {
                var material = net.minecraft.server.command.CommandManager.literal(weathered ? "weathered" : "stone");
                for (String state : new String[]{"active", "runes_off", "empty"}) {
                    material.then(net.minecraft.server.command.CommandManager.literal(state).executes(context -> {
                        int count = 0;
                        for (var player : net.minecraft.command.argument.EntityArgumentType.getPlayers(context, "targets")) {
                            var body = DrakeGolemBody.get(player);
                            if (is(player) && body != null) { body.appearance(weathered, state.equals("active"), !state.equals("empty")); count++; }
                        }
                        int changed = count;
                        context.getSource().sendFeedback(() -> net.minecraft.text.Text.translatable("commands.ssc-extras.drake_golem.appearance", changed), false);
                        return count;
                    }));
                }
                targets.then(material);
            }
            dispatcher.register(command.then(targets));
        });
    }
}
