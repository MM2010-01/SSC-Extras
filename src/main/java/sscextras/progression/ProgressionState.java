package sscextras.progression;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProgressionState extends PersistentState {
    private static final class Quest {
        final Map<String, Long> counts = new LinkedHashMap<>();
        boolean completed;
    }
    private record Subscription(QuestDefinition definition, Quest quest, QuestObjective objective) { }
    private static final class Account {
        final Map<Identifier, Long> totals = new LinkedHashMap<>();
        final Map<Identifier, Quest> quests = new LinkedHashMap<>();
        final Map<Identifier, List<Subscription>> byMetric = new HashMap<>();
        long points;
        int revision = -1;
        void index() {
            if (revision == QuestRegistry.revision()) return;
            byMetric.clear();
            quests.forEach((id, quest) -> {
                var definition = QuestRegistry.get(id);
                if (definition != null && !quest.completed) for (var objective : definition.objectives())
                    byMetric.computeIfAbsent(objective.metric(), ignored -> new ArrayList<>()).add(new Subscription(definition, quest, objective));
            });
            revision = QuestRegistry.revision();
        }
    }
    private final Map<UUID, Account> accounts = new LinkedHashMap<>();
    public static ProgressionState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(ProgressionState::read,
                ProgressionState::new, "ssc_extras_progression");
    }
    public boolean accept(UUID subject, Identifier questId) {
        if (QuestRegistry.get(questId) == null) return false;
        var account = accounts.computeIfAbsent(subject, ignored -> new Account());
        if (account.quests.putIfAbsent(questId, new Quest()) != null) return false;
        account.revision = -1; markDirty(); return true;
    }
    public void record(UUID subject, ProgressEvent event) {
        var account = accounts.computeIfAbsent(subject, ignored -> new Account());
        account.totals.merge(event.metric(), event.amount(), ProgressionState::add);
        account.index();
        for (var entry : account.byMetric.getOrDefault(event.metric(), List.of())) {
            var quest = entry.quest(); var objective = entry.objective();
            if (quest.completed || !objective.accepts().test(event)) continue;
            quest.counts.compute(objective.id(), (id, previous) -> Math.min(objective.required(), add(previous == null ? 0 : previous, event.amount())));
            if (entry.definition().objectives().stream().allMatch(o -> quest.counts.getOrDefault(o.id(), 0L) >= o.required())) {
                quest.completed = true;
                account.points = add(account.points, entry.definition().rewardPoints());
                account.revision = -1;
            }
        }
        markDirty();
    }
    private static long add(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
    public long total(UUID subject, Identifier metric) {
        var account = accounts.get(subject); return account == null ? 0 : account.totals.getOrDefault(metric, 0L);
    }
    public long points(UUID subject) { var account = accounts.get(subject); return account == null ? 0 : account.points; }
    public boolean completed(UUID subject, Identifier id) {
        var account = accounts.get(subject); var quest = account == null ? null : account.quests.get(id);
        return quest != null && quest.completed;
    }
    public long progress(UUID subject, Identifier id, String objective) {
        var account = accounts.get(subject); var quest = account == null ? null : account.quests.get(id);
        return quest == null ? 0 : quest.counts.getOrDefault(objective, 0L);
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putInt("Version", 1);
        var list = new NbtList();
        accounts.forEach((id, account) -> {
            var tag = new NbtCompound(); tag.putUuid("Subject", id); tag.putLong("Points", account.points);
            var totals = new NbtCompound(); account.totals.forEach((metric, value) -> totals.putLong(metric.toString(), value));
            tag.put("Totals", totals);
            var quests = new NbtCompound();
            account.quests.forEach((questId, quest) -> {
                var value = new NbtCompound(); value.putBoolean("Completed", quest.completed);
                var counts = new NbtCompound(); quest.counts.forEach(counts::putLong);
                value.put("Counts", counts); quests.put(questId.toString(), value);
            });
            tag.put("Quests", quests); list.add(tag);
        });
        nbt.put("Accounts", list); return nbt;
    }
    public static ProgressionState read(NbtCompound nbt) {
        var result = new ProgressionState();
        for (var element : nbt.getList("Accounts", NbtElement.COMPOUND_TYPE)) {
            var tag = (NbtCompound)element;
            if (!tag.containsUuid("Subject")) continue;
            var account = new Account(); account.points = Math.max(0, tag.getLong("Points"));
            var totals = tag.getCompound("Totals");
            for (var key : totals.getKeys()) { var id = Identifier.tryParse(key); if (id != null) account.totals.put(id, Math.max(0, totals.getLong(key))); }
            var quests = tag.getCompound("Quests");
            for (var key : quests.getKeys()) {
                var id = Identifier.tryParse(key); if (id == null) continue;
                var value = quests.getCompound(key); var quest = new Quest(); quest.completed = value.getBoolean("Completed");
                var counts = value.getCompound("Counts");
                for (var name : counts.getKeys()) quest.counts.put(name, Math.max(0, counts.getLong(name)));
                account.quests.put(id, quest);
            }
            result.accounts.put(tag.getUuid("Subject"), account);
        }
        return result;
    }
}
