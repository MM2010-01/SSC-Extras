package sscextras.petrification;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import dev.onyxstudios.cca.api.v3.component.tick.ServerTickingComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class PetrificationState implements AutoSyncedComponent, ServerTickingComponent {
    private final LivingEntity owner;
    private long nextDose;
    private boolean liquid, locked, applyingInternalDose, drinking;
    private long lastMovementBreak = Long.MIN_VALUE;
    private int bottledTicks;
    public float yaw, pitch, headYaw, bodyYaw, limbPos, limbSpeed, animationAge, swing;
    LivingEntity miningTarget;
    net.minecraft.item.ItemStack miningTool = net.minecraft.item.ItemStack.EMPTY;
    float miningProgress;
    long miningSignal;
    public boolean miningPermissionCheck, shattering;
    boolean shatteredBody;
    private record Crack(int stage, long expires) { }
    private final java.util.Map<java.util.UUID, Crack> cracks = new java.util.HashMap<>();
    private int crackStage = -1;
    private java.util.UUID poseToken;
    private PetrificationPose pose = new PetrificationPose();
    private boolean poseSaved, poseSent, effectDirty;
    /** Physical crate identity, never a copy of the player's inventory or form. */
    public java.util.UUID crateId;

    public java.util.UUID poseToken() { return poseToken; }
    public PetrificationPose pose() { return pose; }
    public boolean poseSaved() { return poseSaved; }
    public boolean submitPose() {
        if (poseToken == null || poseSaved || poseSent || !pose.hasBody()) return false;
        poseSent = true;
        return true;
    }
    public boolean acceptPose(java.util.UUID token, PetrificationPose snapshot) {
        if (owner.getWorld().isClient || !Petrification.poseLocked(owner) || poseSaved
                || poseToken == null || !poseToken.equals(token) || !snapshot.hasBody()) return false;
        pose = snapshot; poseSaved = true;
        PetrificationComponents.KEY.sync(owner);
        return true;
    }
    public void effectChanged() { if (!owner.getWorld().isClient) effectDirty = true; }
    public void effectRemoved() {
        if (crateId != null || sscextras.statue.StatueBodies.golem(owner)) { effectChanged(); return; }
        // clearStatusEffects notifies before removing its map entry; do not query it here.
        locked = false; poseToken = null; pose = new PetrificationPose(); poseSaved = poseSent = false;
        liquid = false; bottledTicks = 0;
        effectChanged();
    }

    public PetrificationState(LivingEntity owner) { this.owner = owner; }

    public int crackStage() {
        int healthCracks = Petrification.stoneAppearance(owner) && owner.getHealth() < owner.getMaxHealth()
                ? net.minecraft.util.math.MathHelper.clamp((int)((1 - owner.getHealth() / owner.getMaxHealth()) * 10), 0, 9) : -1;
        return Math.max(crackStage, healthCracks);
    }
    void miningCracks(java.util.UUID miner, float progress) {
        cracks.put(miner, new Crack(Math.min(9, (int)(progress * 10)), owner.getWorld().getTime() + 8));
        syncCracks();
    }
    void clearCracks(java.util.UUID miner) { if (cracks.remove(miner) != null) syncCracks(); }
    private void syncCracks() {
        int next = cracks.values().stream().mapToInt(Crack::stage).max().orElse(-1);
        if (next == crackStage) return;
        crackStage = next;
        if (!owner.getWorld().isClient && !owner.isRemoved()) PetrificationComponents.KEY.sync(owner);
    }

    public boolean rejectsDose() {
        return !owner.getWorld().isClient && sscextras.player.StoneBodies.petrificationImmune(owner, applyingInternalDose || drinking);
    }
    /** Scope only the native swallowed-effect call, preserving other admission and potion hooks. */
    public boolean drink(java.util.function.BooleanSupplier apply) {
        boolean previous = drinking;
        drinking = true;
        try { return apply.getAsBoolean(); }
        finally { drinking = previous; }
    }
    public StatusEffectInstance dose(StatusEffectInstance incoming) {
        if (incoming.getEffectType() != Petrification.EFFECT || owner.getWorld().isClient || applyingInternalDose || rejectsDose()) return incoming;
        var current = owner.getStatusEffect(Petrification.EFFECT);
        int tier = incoming.getAmplifier() == 0 && current != null ? Math.min(2, current.getAmplifier() + 1)
                : Math.min(2, incoming.getAmplifier());
        int duration = incoming.getDuration();
        if (tier == 2) duration = Petrification.STONE_DURATION;
        if (liquid) {
            bottledTicks = longer(bottledTicks, duration);
            duration = -1;
        } else if (current != null) duration = longer(current.getDuration(), duration);
        return new StatusEffectInstance(Petrification.EFFECT, duration, tier, incoming.isAmbient(),
                incoming.shouldShowParticles(), incoming.shouldShowIcon());
    }

    private static int longer(int first, int second) { return first == -1 || second == -1 ? -1 : Math.max(first, second); }

    /** Server-authoritative escape; a held key or duplicate packets cannot multiply rolls within a tick. */
    public boolean tryMovementBreak() {
        if (!(owner instanceof net.minecraft.server.network.ServerPlayerEntity player) || !Petrification.stone(owner)
                || crateId != null
                || sscextras.player.StoneBodies.petrificationImmune(owner, true)) return false;
        long now = owner.getWorld().getTime();
        if (lastMovementBreak == now) return false;
        lastMovementBreak = now;
        if (owner.getRandom().nextFloat() >= sscextras.SscExtrasConfig.petrificationIIIMovementBreakChance()) return false;
        liquid = false; bottledTicks = 0;
        setInternalEffect(new StatusEffectInstance(Petrification.EFFECT, Petrification.BOTTLE_DURATION, 0));
        updateLock();
        player.sendMessage(net.minecraft.text.Text.translatable("message.ssc-extras.petrification.break_free"), true);
        return true;
    }
    private void setInternalEffect(StatusEffectInstance effect) {
        boolean previous = applyingInternalDose; applyingInternalDose = true;
        try { owner.setStatusEffect(effect, null); }
        finally { applyingInternalDose = previous; }
    }

    public void touch() {
        if (owner.getWorld().isClient || !owner.isAlive() || owner.isSpectator()
                || !liquidExposure()) return;
        var effect = owner.getStatusEffect(Petrification.EFFECT);
        long now = owner.getWorld().getTime();
        if (effect == null && now < nextDose) return;
        if (!liquid) { bottledTicks = effect == null ? 0 : effect.getDuration(); liquid = true; }
        int tier = effect == null ? -1 : effect.getAmplifier();
        if (now >= nextDose) { tier = Math.min(2, tier + 1); nextDose = now + Petrification.DOSE_INTERVAL; }
        if (effect == null || effect.getDuration() != -1 || effect.getAmplifier() != tier) {
            var dose = new StatusEffectInstance(Petrification.EFFECT, -1, Math.max(0, tier));
            applyingInternalDose = true;
            try {
                boolean accepted = owner.addStatusEffect(dose);
                if (accepted && owner.getStatusEffect(Petrification.EFFECT).getDuration() != -1) owner.setStatusEffect(dose, null);
            }
            finally { applyingInternalDose = false; }
            if (owner.getStatusEffect(Petrification.EFFECT) == null) { liquid = false; bottledTicks = 0; }
        }
        updateLock();
    }

    private boolean liquidExposure() {
        return !sscextras.player.StoneBodies.petrificationImmune(owner, PetrifyingFluid.headSubmerged(owner));
    }
    private boolean touching() {
        var box = owner.getBoundingBox().contract(.001);
        for (var pos : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ), BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
            var fluid = owner.getWorld().getFluidState(pos);
            if (fluid.getFluid().matchesType(Petrification.STILL)
                    && box.minY < pos.getY() + fluid.getHeight(owner.getWorld(), pos)) return true;
        }
        return false;
    }

    @Override public void serverTick() {
        if (crateId != null && owner instanceof net.minecraft.server.network.ServerPlayerEntity player) {
            sscextras.statue.StatueCrates.tick(player); return;
        }
        if (!cracks.isEmpty()) {
            if (!sscextras.player.StoneBodies.mineable(owner)) cracks.clear();
            else cracks.values().removeIf(crack -> crack.expires() <= owner.getWorld().getTime());
            syncCracks();
        }
        if (owner instanceof net.minecraft.server.network.ServerPlayerEntity player && miningTarget != null) PetrificationMining.tick(player);
        if (liquid) {
            if (bottledTicks > 0) bottledTicks--;
            var effect = owner.getStatusEffect(Petrification.EFFECT);
            if (effect == null) { liquid = false; bottledTicks = 0; }
            else if (liquidExposure() && touching()) touch();
            else {
                liquid = false;
                setInternalEffect(new StatusEffectInstance(Petrification.EFFECT,
                        effect.getAmplifier() >= 2 ? Petrification.STONE_DURATION : longer(Petrification.DOSE_INTERVAL, bottledTicks), effect.getAmplifier()));
                bottledTicks = 0;
            }
        }
        updateLock();
        hold();
        if (effectDirty) { effectDirty = false; PetrificationComponents.KEY.sync(owner); }
    }

    public void updateLock() {
        boolean next = Petrification.poseLocked(owner);
        if (next == locked) {
            // Upgrade older locked saves without discarding their original animation inputs.
            if (next && poseToken == null && !owner.getWorld().isClient) poseToken = java.util.UUID.randomUUID();
            return;
        }
        locked = next;
        pose = new PetrificationPose(); poseSaved = poseSent = false;
        poseToken = next && !owner.getWorld().isClient ? java.util.UUID.randomUUID() : null;
        if (next) {
            yaw = owner.getYaw(); pitch = owner.getPitch(); headYaw = owner.headYaw; bodyYaw = owner.bodyYaw;
            limbPos = owner.limbAnimator.getPos(); limbSpeed = owner.limbAnimator.getSpeed();
            animationAge = owner.age; swing = owner.handSwingProgress;
            if (!owner.getWorld().isClient) owner.stopRiding();
            if (owner instanceof MobEntity mob) mob.getNavigation().stop();
        }
        if (!owner.getWorld().isClient) { effectDirty = false; PetrificationComponents.KEY.sync(owner); }
    }

    public void hold() {
        if (!sscextras.statue.StatueBodies.locked(owner)) return;
        updateLock();
        if (sscextras.statue.StatueBodies.falls(owner)) {
            owner.setVelocity(0, Math.min(0, owner.getVelocity().y), 0);
            if (owner instanceof net.minecraft.entity.player.PlayerEntity player && player.getAbilities().flying) {
                player.getAbilities().flying = false;
                if (!player.getWorld().isClient) player.sendAbilitiesUpdate();
            }
        } else { owner.setVelocity(Vec3d.ZERO); owner.fallDistance = 0; }
        owner.setYaw(yaw); owner.prevYaw = yaw;
        owner.setPitch(pitch); owner.prevPitch = pitch;
        owner.headYaw = owner.prevHeadYaw = headYaw;
        owner.bodyYaw = owner.prevBodyYaw = bodyYaw;
        owner.setSprinting(false);
    }

    /** Rotate the whole saved statue without changing its head/body offset or model-local pose. */
    public void face(float facing) {
        float turn = net.minecraft.util.math.MathHelper.wrapDegrees(facing - bodyYaw);
        yaw += turn; headYaw += turn; bodyYaw += turn;
        pose.turn(turn);
        if (owner instanceof net.minecraft.entity.player.PlayerEntity player) {
            var form = sscextras.golem.AbstractGolemForm.get(player);
            if (form != null) form.turnStatue(player, yaw);
        }
        owner.setYaw(yaw); owner.prevYaw = yaw; owner.setPitch(pitch); owner.prevPitch = pitch;
        owner.bodyYaw = owner.prevBodyYaw = bodyYaw; owner.headYaw = owner.prevHeadYaw = headYaw;
        if (!owner.getWorld().isClient) PetrificationComponents.KEY.sync(owner);
    }

    @Override public void writeToNbt(NbtCompound tag) {
        if (crateId != null) tag.putUuid("Crate", crateId);
        tag.putLong("NextDose", nextDose); tag.putBoolean("Liquid", liquid); tag.putInt("BottledTicks", bottledTicks);
        tag.putBoolean("Locked", locked); tag.putFloat("Yaw", yaw); tag.putFloat("Pitch", pitch);
        tag.putFloat("HeadYaw", headYaw); tag.putFloat("BodyYaw", bodyYaw);
        tag.putFloat("LimbPos", limbPos); tag.putFloat("LimbSpeed", limbSpeed);
        tag.putFloat("AnimationAge", animationAge); tag.putFloat("Swing", swing);
        if (poseToken != null) tag.putUuid("PoseToken", poseToken);
        if (poseSaved) tag.put("ModelPose", pose.write());
    }
    @Override public void readFromNbt(NbtCompound tag) {
        crateId = tag.containsUuid("Crate") ? tag.getUuid("Crate") : null;
        cracks.clear(); crackStage = -1;
        nextDose = tag.getLong("NextDose"); liquid = tag.getBoolean("Liquid"); bottledTicks = tag.getInt("BottledTicks");
        locked = tag.getBoolean("Locked"); yaw = tag.getFloat("Yaw"); pitch = tag.getFloat("Pitch");
        headYaw = tag.getFloat("HeadYaw"); bodyYaw = tag.getFloat("BodyYaw");
        limbPos = tag.getFloat("LimbPos"); limbSpeed = tag.getFloat("LimbSpeed");
        animationAge = tag.getFloat("AnimationAge"); swing = tag.getFloat("Swing");
        var token = tag.containsUuid("PoseToken") ? tag.getUuid("PoseToken") : null;
        if (!java.util.Objects.equals(token, poseToken)) poseSent = false;
        poseToken = token;
        pose = PetrificationPose.read(tag.getCompound("ModelPose")); poseSaved = pose.hasBody();
    }
    @Override public void writeSyncPacket(net.minecraft.network.PacketByteBuf buffer, net.minecraft.server.network.ServerPlayerEntity recipient) {
        AutoSyncedComponent.super.writeSyncPacket(buffer, recipient);
        buffer.writeVarInt(crackStage);
        var effect = owner.getStatusEffect(Petrification.EFFECT);
        buffer.writeNbt(effect == null ? null : effect.writeNbt(new NbtCompound()));
    }
    @Override public void applySyncPacket(net.minecraft.network.PacketByteBuf buffer) {
        AutoSyncedComponent.super.applySyncPacket(buffer);
        crackStage = buffer.readVarInt();
        var tag = buffer.readNbt();
        var effect = tag == null ? null : StatusEffectInstance.fromNbt(tag);
        if (effect != null && effect.getEffectType() == Petrification.EFFECT) owner.setStatusEffect(effect, null);
        else owner.removeStatusEffect(Petrification.EFFECT);
    }
}
