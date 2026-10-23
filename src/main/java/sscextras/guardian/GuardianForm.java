package sscextras.guardian;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import sscextras.player.StoneGolemForm;

/** Guardian adapter for the common golem statue contract, without another state owner. */
public final class GuardianForm extends StoneGolemForm {
    private final int stage;
    public GuardianForm(int stage) {
        super(RuneboundGuardian.id("runebound_guardian_" + stage), stage == 3, GuardianBodyPower.armor(stage), stage >= 2);
        this.stage = stage;
    }
    @Override public boolean statue(PlayerEntity player) {
        var state = RuneboundGuardian.state(player);
        return stage == 3 && state.deactivated && !state.detached && state.ritualTicks == 0;
    }
    @Override public boolean supportsStatueControl() { return stage == 3; }
    @Override public boolean toggleStatue(ServerPlayerEntity player) { return RuneboundGuardian.state(player).toggleStatueMode(); }
    @Override public void turnStatue(PlayerEntity player, float yaw) {
        var state = RuneboundGuardian.state(player); state.frozenYaw = yaw; state.sync();
    }
}
