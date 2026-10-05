package sscextras.drake;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.SignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class DrakeOutpostOwnership extends PersistentState {
    public interface Display {
        String sscExtras$mountName();
        void sscExtras$mountName(String name);
    }

    public static final class Claim {
        public final RegistryKey<World> world;
        public final BlockBox stable;
        public final String name;
        public long nextMeal;
        public int escapes;
        public boolean tryingToEscape;
        public boolean collarIssued;
        public int stallIndex, goodTicks, ritualHint;
        public boolean soulbound, awaitingRespawn;
        public NbtCompound previousSpawn = new NbtCompound();
        int ritualTicks;
        final java.util.List<UUID> attendants = new java.util.ArrayList<>();
        boolean outside, warnedEdge, spotted;
        long nextOutsideHint, seenSince = -1, recallUntil;
        int witness;
        Claim(RegistryKey<World> world, BlockBox stable, String name) {
            this.world = world; this.stable = stable; this.name = name;
            stallIndex = (stable.getBlockCountX() - 1) / 7 - 1;
        }
        public BlockPos sign() { return new BlockPos(stable.getMinX() + stallIndex * 7 + 2, stable.getMinY() + 1, stable.getMinZ() + 2); }
        public BlockPos tie() { return new BlockPos(stable.getMinX() + stallIndex * 7 + 3, stable.getMinY() + 1, stable.getMinZ() + 12); }
        public BlockPos bed() { return tie().north(4); }
        public BlockPos gate() { return tie().north(9); }
        public Box stall() { return new Box(stable.getMinX() + stallIndex * 7 + 1, stable.getMinY() + 1, stable.getMinZ() + 4,
                stable.getMinX() + stallIndex * 7 + 7, stable.getMinY() + 5, stable.getMinZ() + 12); }
        public boolean matches(World other, DrakeStablePiece piece) {
            return world.equals(other.getRegistryKey()) && stable.equals(piece.getBoundingBox());
        }
    }

    private final Map<UUID, Claim> mounts = new LinkedHashMap<>();
    private final Map<GlobalPos, String> pendingSigns = new LinkedHashMap<>();
    private final java.util.Set<GlobalPos> vacantResidents = new java.util.LinkedHashSet<>();
    private final Map<UUID, java.util.Set<GlobalPos>> introductions = new LinkedHashMap<>();

    public static DrakeOutpostOwnership get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(DrakeOutpostOwnership::read,
                DrakeOutpostOwnership::new, "ssc_extras_drake_outposts");
    }

    public static Claim claim(PlayerEntity player) {
        return player.getWorld().isClient ? null : get(player.getServer()).mounts.get(player.getUuid());
    }

    public static boolean owns(PlayerEntity player, DrakeStablePiece stable) {
        var claim = claim(player);
        return claim != null && claim.matches(player.getWorld(), stable);
    }

    public static boolean reservedForAnother(ServerWorld world, DrakeStablePiece stable, PlayerEntity player) {
        return availableStall(world, stable, player) < 0;
    }

    public static int availableStall(ServerWorld world, DrakeStablePiece stable, PlayerEntity player) {
        var owned = claim(player);
        if (owned != null && owned.matches(world, stable)) return owned.stallIndex;
        if (owned != null && owned.soulbound) return -1;
        for (int i = stable.stallCount() - 1; i >= 0; i--)
            if (available(world, stable, i, player) && world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class,
                    stable.stall(i), entity -> entity.isAlive() && entity != player && !entity.isSpectator()
                            && !(entity instanceof net.minecraft.entity.mob.PillagerEntity)).isEmpty()) return i;
        return -1;
    }

    public static boolean available(ServerWorld world, DrakeStablePiece stable, int stall, PlayerEntity player) {
        if (stall < 0 || stall >= stable.stallCount()) return false;
        var data = get(world.getServer());
        var owned = claim(player);
        if (owned != null && owned.soulbound && (!owned.matches(world, stable) || owned.stallIndex != stall)) return false;
        if (stall < stable.residentCount() && !data.vacantResidents.contains(GlobalPos.create(world.getRegistryKey(), stable.sign(stall)))) return false;
        if (data.mounts.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(player.getUuid())
                && entry.getValue().matches(world, stable) && entry.getValue().stallIndex == stall)) return false;
        return world.getEntitiesByClass(net.minecraft.entity.mob.PillagerEntity.class, Box.from(stable.getBoundingBox()).expand(DrakeCaptureGoal.RANGE), guard -> {
            var capture = ((DrakeCaptureGoal.Captor)guard).sscExtras$captureGoal();
            var battle = DrakeBattleGoal.of(guard);
            return guard.isAlive() && (capture != null && capture.reserves(stable, stall, player)
                    || battle != null && battle.reserves(stable, stall, player));
        }).isEmpty();
    }

    public static void vacateResident(MinecraftServer server, String dimension, BlockBox stable, int stall) {
        var data = get(server);
        var world = RegistryKey.of(RegistryKeys.WORLD, new Identifier(dimension));
        var sign = GlobalPos.create(world, new BlockPos(stable.getMinX() + stall * 7 + 2, stable.getMinY() + 1, stable.getMinZ() + 2));
        if (!data.vacantResidents.add(sign)) return;
        data.updateSign(server, sign, "", false);
        data.markDirty();
    }

    public static void capture(PlayerEntity player, DrakeStablePiece stable) {
        capture(player, stable, availableStall((ServerWorld)player.getWorld(), stable, player));
    }

    public static void capture(PlayerEntity player, DrakeStablePiece stable, int stall) {
        if (BondOfTheBeastCompat.hasOwner(player)) return;
        if (!available((ServerWorld)player.getWorld(), stable, stall, player)) return;
        BlindingRein.setClosed(player, false);
        if (owns(player, stable)) {
            var claim = claim(player);
            if (claim.tryingToEscape && !DrakeBattleGoal.riding(player)) {
                claim.escapes++;
                DrakeSoulbinding.disobey(player);
                claim.tryingToEscape = false;
                if (claim.escapes == 3) player.sendMessage(Text.translatable("message.ssc-extras.drake.last_escape_warning")
                        .formatted(net.minecraft.util.Formatting.DARK_PURPLE), false);
                get(player.getServer()).markDirty();
            }
            if (claim.escapes > 3) sscextras.collar.TamingCollar.equip(player);
            issueCollar(player, claim);
            return;
        }
        release(player);
        var data = get(player.getServer());
        var claim = new Claim(player.getWorld().getRegistryKey(), stable.getBoundingBox(),
                DrakeMountNames.choose(player.getRandom(), stable.residentNames()));
        claim.stallIndex = stall;
        data.mounts.put(player.getUuid(), claim);
        data.updateSign(player.getServer(), GlobalPos.create(claim.world, claim.sign()), claim.name, true);
        data.markDirty();
        display(player, claim.name);
        issueCollar(player, claim);
    }

    private static void issueCollar(PlayerEntity player, Claim claim) {
        if (!claim.collarIssued && sscextras.collar.Collars.equipOwnedDrake(player, claim.name)) {
            claim.collarIssued = true;
            get(player.getServer()).markDirty();
        }
    }

    public static void release(PlayerEntity player) {
        if (player.getWorld().isClient) return;
        var data = get(player.getServer());
        var old = data.mounts.remove(player.getUuid());
        if (old != null) {
            DrakeSoulbinding.clearCurse(player, old);
            data.updateSign(player.getServer(), GlobalPos.create(old.world, old.sign()), "", false);
            if (DrakeLeashing.holder(player) instanceof LeashKnotEntity knot
                    && player.getWorld().getRegistryKey().equals(old.world) && knot.getDecorationBlockPos().equals(old.tie()))
                DrakeLeashing.detach(player, true);
            data.markDirty();
        }
        display(player, "");
    }

    private static void display(PlayerEntity player, String name) {
        var state = (Display)player;
        if (state.sscExtras$mountName().equals(name)) return;
        state.sscExtras$mountName(name);
        if (player instanceof ServerPlayerEntity serverPlayer)
            player.getServer().getPlayerManager().sendToAll(new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, serverPlayer));
    }

    public static void tick(PlayerEntity player) {
        var claim = claim(player);
        if (claim == null) { display(player, ""); return; }
        var form = FormAbilityManager.getForm(player);
        if (!claim.soulbound && ((form == RegPlayerForms.ORIGINAL_SHIFTER || form == RegPlayerForms.ORIGINAL_BEFORE_ENABLE)
                && (!player.getWorld().getRegistryKey().equals(claim.world) || !DrakeCaptureGoal.near(claim.stable, player.getPos(), 16))
                || BondOfTheBeastCompat.hasOwner(player))) {
            release(player); return;
        }
        display(player, claim.name);
        introduce(player, claim);
        DrakeRoaming.tick(player, claim);
        DrakeSoulbinding.tick((ServerPlayerEntity)player, claim);
        if (player.getWorld().getRegistryKey().equals(claim.world)
                && DrakeLeashing.holder(player) instanceof LeashKnotEntity ownedKnot
                && ownedKnot.getDecorationBlockPos().equals(claim.tie())) issueCollar(player, claim);
    }

    private static void introduce(PlayerEntity player, Claim claim) {
        if (EarthenDrake.stage(player) < 0 || player.isSleeping() || !player.isAlive() || player.isSpectator()
                || !claim.world.equals(player.getWorld().getRegistryKey())
                || !DrakeCaptureGoal.near(claim.stable, player.getPos(), 16)
                || net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager.getPlayerTransformData(player).isTransforming) return;
        var data = get(player.getServer());
        var site = GlobalPos.create(claim.world, new BlockPos(claim.stable.getMinX(), claim.stable.getMinY(), claim.stable.getMinZ()));
        var seen = data.introductions.computeIfAbsent(player.getUuid(), id -> new java.util.LinkedHashSet<>());
        if (seen.contains(site)) return;
        for (var guard : player.getWorld().getEntitiesByClass(net.minecraft.entity.mob.PillagerEntity.class,
                player.getBoundingBox().expand(16), entity -> entity.isAlive() && !entity.hasActiveRaid()
                        && !DrakeFaction.fighting(entity) && entity.getVisibilityCache().canSee(player))) {
            var home = ((DrakeStableNavigation)guard.getNavigation()).stable();
            if (home == null || !claim.matches(player.getWorld(), home)) continue;
            guard.getLookControl().lookAt(player, 30, 30);
            DrakeDialogue.say(player, "stable_rules");
            seen.add(site); data.markDirty(); return;
        }
    }

    public static void register() {
        DrakeSoulbinding.register();
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof PlayerEntity player)) return;
            var claim = claim(player);
            if (claim == null) return;
            if (!claim.soulbound) release(player);
            else {
                claim.awaitingRespawn = true;
                get(player.getServer()).markDirty();
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var data = get(server);
            var signs = data.pendingSigns.entrySet().iterator();
            while (signs.hasNext()) {
                var entry = signs.next();
                var world = server.getWorld(entry.getKey().getDimension());
                if (world != null && world.isChunkLoaded(entry.getKey().getPos())) {
                    writeSign(world, entry.getKey().getPos(), entry.getValue(), false);
                    signs.remove(); data.markDirty();
                }
            }
            for (var player : server.getPlayerManager().getPlayerList()) {
                if (server.getTicks() % 20 == 0) tick(player);
                else {
                    var claim = claim(player);
                    if (claim != null) DrakeRoaming.tick(player, claim);
                }
            }
        });
    }

    private void updateSign(MinecraftServer server, GlobalPos pos, String name, boolean create) {
        var world = server.getWorld(pos.getDimension());
        if (world != null && world.isChunkLoaded(pos.getPos())) {
            writeSign(world, pos.getPos(), name, create);
            pendingSigns.remove(pos);
        } else pendingSigns.put(pos, name);
    }

    public static void writeSign(World world, BlockPos pos, String name, boolean create) {
        if (create && world.isAir(pos) && world.getBlockState(pos.down()).isSolidBlock(world, pos.down()))
            world.setBlockState(pos, Blocks.DARK_OAK_SIGN.getDefaultState().with(SignBlock.ROTATION, 8), 3);
        if (world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            var text = new SignText().withMessage(1, Text.literal(name));
            sign.setText(text, true); sign.setText(text, false);
            sign.markDirty();
        }
    }

    public static DrakeOutpostOwnership read(NbtCompound nbt) {
        var data = new DrakeOutpostOwnership();
        for (var element : nbt.getList("VacantResidents", NbtElement.COMPOUND_TYPE)) {
            var tag = (NbtCompound)element;
            data.vacantResidents.add(GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, new Identifier(tag.getString("World"))),
                    BlockPos.fromLong(tag.getLong("Pos"))));
        }
        for (var element : nbt.getList("Introductions", NbtElement.COMPOUND_TYPE)) {
            var tag = (NbtCompound)element;
            if (tag.containsUuid("Player")) data.introductions.computeIfAbsent(tag.getUuid("Player"), id -> new java.util.LinkedHashSet<>())
                    .add(GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, new Identifier(tag.getString("World"))), BlockPos.fromLong(tag.getLong("Pos"))));
        }
        for (var element : nbt.getList("Mounts", NbtElement.COMPOUND_TYPE)) {
            var tag = (NbtCompound)element;
            int[] box = tag.getIntArray("Stable");
            if (box.length != 6 || !tag.containsUuid("Player")) continue;
            var claim = new Claim(RegistryKey.of(RegistryKeys.WORLD, new Identifier(tag.getString("World"))),
                    new BlockBox(box[0], box[1], box[2], box[3], box[4], box[5]), tag.getString("Name"));
            claim.nextMeal = tag.getLong("NextMeal");
            claim.escapes = Math.max(0, tag.getInt("Escapes"));
            claim.tryingToEscape = tag.getBoolean("TryingToEscape");
            claim.collarIssued = tag.getBoolean("CollarIssued");
            if (tag.contains("Stall")) claim.stallIndex = Math.max(0, Math.min(claim.stallIndex, tag.getInt("Stall")));
            claim.goodTicks = Math.max(0, Math.min(DrakeSoulbinding.SERVICE_TICKS, tag.getInt("GoodTicks")));
            claim.ritualHint = tag.getInt("RitualHint");
            claim.soulbound = tag.getBoolean("Soulbound");
            claim.awaitingRespawn = tag.getBoolean("AwaitingRespawn");
            claim.previousSpawn = tag.getCompound("PreviousSpawn").copy();
            data.mounts.put(tag.getUuid("Player"), claim);
        }
        for (var element : nbt.getList("Signs", NbtElement.COMPOUND_TYPE)) {
            var tag = (NbtCompound)element;
            data.pendingSigns.put(GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, new Identifier(tag.getString("World"))),
                    BlockPos.fromLong(tag.getLong("Pos"))), tag.getString("Name"));
        }
        return data;
    }

    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        var mounts = new NbtList();
        this.mounts.forEach((id, claim) -> {
            var tag = new NbtCompound(); tag.putUuid("Player", id); tag.putString("World", claim.world.getValue().toString());
            var box = claim.stable;
            tag.putIntArray("Stable", new int[]{box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ()});
            tag.putString("Name", claim.name); tag.putLong("NextMeal", claim.nextMeal);
            tag.putInt("Escapes", claim.escapes); tag.putBoolean("TryingToEscape", claim.tryingToEscape);
            tag.putBoolean("CollarIssued", claim.collarIssued);
            tag.putInt("Stall", claim.stallIndex); tag.putInt("GoodTicks", claim.goodTicks); tag.putInt("RitualHint", claim.ritualHint);
            tag.putBoolean("Soulbound", claim.soulbound); tag.putBoolean("AwaitingRespawn", claim.awaitingRespawn);
            tag.put("PreviousSpawn", claim.previousSpawn.copy()); mounts.add(tag);
        });
        var signs = new NbtList();
        pendingSigns.forEach((pos, name) -> {
            var tag = new NbtCompound(); tag.putString("World", pos.getDimension().getValue().toString());
            tag.putLong("Pos", pos.getPos().asLong()); tag.putString("Name", name); signs.add(tag);
        });
        var introductions = new NbtList();
        this.introductions.forEach((id, sites) -> sites.forEach(site -> {
            var tag = new NbtCompound(); tag.putUuid("Player", id);
            tag.putString("World", site.getDimension().getValue().toString()); tag.putLong("Pos", site.getPos().asLong());
            introductions.add(tag);
        }));
        var vacancies = new NbtList();
        vacantResidents.forEach(pos -> {
            var tag = new NbtCompound(); tag.putString("World", pos.getDimension().getValue().toString());
            tag.putLong("Pos", pos.getPos().asLong()); vacancies.add(tag);
        });
        nbt.put("VacantResidents", vacancies);
        nbt.put("Mounts", mounts); nbt.put("Signs", signs); nbt.put("Introductions", introductions); return nbt;
    }
}
