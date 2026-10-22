package sscextras.guardian;

import io.github.apace100.apoli.registry.ApoliRegistries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.*;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.status_effects.BaseTransformativeStatusEffect;
import net.onixary.shapeShifterCurseFabric.status_effects.RegTStatusEffect;
import net.onixary.shapeShifterCurseFabric.status_effects.transformative_effects.TransformativeStatus;
import net.onixary.shapeShifterCurseFabric.status_effects.transformative_effects.TransformativeStatusPotion;

public final class RuneboundGuardian {
    public static final Identifier STATUE_TOGGLE = id("guardian_statue_toggle");
    public static final PlayerFormBase[] FORMS = new PlayerFormBase[4];
    public static final PlayerFormGroup GROUP;
    public static final BaseTransformativeStatusEffect CURSE;
    public static final Potion POTION;
    public static final TagKey<Item> TOOLS = TagKey.of(RegistryKeys.ITEM, id("guardian_tools"));
    public static final TagKey<Item> STONES = TagKey.of(RegistryKeys.ITEM, id("guardian_stones"));

    static {
        var group = new PlayerFormGroup(id("runebound_guardian"));
        for (int stage = 0; stage < 4; stage++) {
            var form = new sscextras.player.StoneGolemForm(id("runebound_guardian_" + stage), stage == 3, GuardianBodyPower.armor(stage))
                    .setPhase(PlayerFormPhase.valueOf("PHASE_" + stage))
                    .setBodyType(PlayerFormBodyType.NORMAL).setIgnoreCursedMoon(stage == 3);
            FORMS[stage] = RegPlayerForms.registerPlayerForm(form);
            group.addForm(form, stage);
        }
        GROUP = RegPlayerForms.registerPlayerFormGroup(group);
        CURSE = RegTStatusEffect.register(id("to_runebound_guardian_0_effect"), new TransformativeStatus(FORMS[0]));
        var effect = Registry.register(Registries.STATUS_EFFECT, id("to_runebound_guardian_0_potion"),
                new TransformativeStatusPotion(CURSE));
        POTION = Registry.register(Registries.POTION, id("runebound_guardian"),
                new Potion("ssc_extras_runebound_guardian", new StatusEffectInstance(effect)));
    }

    private RuneboundGuardian() { }
    public static Identifier id(String path) { return new Identifier("ssc-extras", path); }
    public static int stage(PlayerEntity player) {
        var form = FormAbilityManager.getForm(player);
        return form.getGroup() == GROUP ? form.getIndex() : -1;
    }
    public static GuardianState state(PlayerEntity player) { return GuardianComponents.KEY.get(player); }
    public static boolean construct(PlayerEntity player) { return stage(player) >= 2; }

    public static void register() {
        sscextras.player.PlayerStance.registerGroup(GROUP.GroupID, new sscextras.player.PlayerStance.Profile() {
            public boolean supports(PlayerEntity player, sscextras.player.PlayerStance.Stance stance) { return true; }
            public sscextras.player.PlayerStance.Stance current(PlayerEntity player) {
                return sscextras.player.PlayerStance.requested(player) ? sscextras.player.PlayerStance.Stance.QUADRUPEDAL : sscextras.player.PlayerStance.Stance.UPRIGHT;
            }
            public void refresh(net.minecraft.server.network.ServerPlayerEntity player) {
                io.github.apace100.apoli.component.PowerHolderComponent.getPowers(player, GuardianBodyPower.class).forEach(GuardianBodyPower::resize);
            }
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(STATUE_TOGGLE,
                (server, player, handler, buffer, sender) -> server.execute(() -> state(player).toggleStatueMode()));
        var body = GuardianBodyPower.factory();
        Registry.register(ApoliRegistries.POWER_FACTORY, body.getSerializerId(), body);
        var ability = GuardianAbilityPower.factory();
        Registry.register(ApoliRegistries.POWER_FACTORY, ability.getSerializerId(), ability);
        var flight = new io.github.apace100.apoli.power.factory.condition.ConditionFactory<net.minecraft.entity.Entity>(id("guardian_can_fly"),
                new io.github.apace100.calio.data.SerializableData(), (data, entity) -> entity instanceof PlayerEntity player
                && stage(player) >= 2 && state(player).energy > 0 && !GuardianCombat.locked(player) && !player.isSleeping());
        Registry.register(ApoliRegistries.ENTITY_CONDITION, flight.getSerializerId(), flight);
        GuardianEquipment.register();
        Registry.register(Registries.ITEM,id("guardian_chisel"),GuardianRitual.CHISEL);
        Registry.register(Registries.ITEM,id("liquid_stone_bucket"),GuardianRitual.LIQUID_STONE);
        net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.ALLOW_SLEEP_TIME.register((player,pos,vanilla)->
                stage(player)<1?net.minecraft.util.ActionResult.PASS:GuardianSleep.canSleepNow(player)?net.minecraft.util.ActionResult.SUCCESS:net.minecraft.util.ActionResult.FAIL);
        GuardianPedestal.register();
        GuardianCombat.register();
        GuardianVillages.register();
        GuardianCores.register();
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(GuardianPedestal.ITEM));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(GuardianEquipment.CUFFS); entries.add(GuardianEquipment.COLLAR);
            entries.add(GuardianCores.INACTIVE_CORE);
        });
    }
}
