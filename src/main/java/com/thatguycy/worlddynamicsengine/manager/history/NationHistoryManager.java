package com.thatguycy.worlddynamicsengine.manager.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.palmergames.bukkit.towny.object.Nation;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.NationChronicle;
import com.thatguycy.worlddynamicsengine.model.WDEnation;
import com.thatguycy.worlddynamicsengine.manager.organization.OrgManager.MilitaryWarState;
import com.thatguycy.worlddynamicsengine.manager.organization.OrgManager.PeaceTreatyProposalState;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 以数据库为事件真本，原子生成 JSON 文件投影；模板修改不改写历史事实。
 * 国家存档后的快照差异覆盖命令、菜单、选举和第三方 Towny 变更。
 */
public final class NationHistoryManager {
    private static final String CATEGORY = "nation_history";
    private static final DateTimeFormatter REAL_DATE = DateTimeFormatter.ofPattern("yyyy.M.d");
    private final WorldDynamicsEngine plugin;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final Map<String, NationChronicle> books;
    private final Map<String, Long> pendingArchives = new LinkedHashMap<>();
    private YamlConfiguration templates;
    private boolean enabled;
    private int transactionDepth;
    private boolean resumeBaseline;

    public NationHistoryManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        books = plugin.getDataStorageManager().loadStringKeyed(CATEGORY, NationChronicle.class);
        reload();
        // Rebuild missing/stale JSON projections from the durable database, including archives.
        for (NationChronicle book : books.values()) project(book);
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "event.yml");
        if (!file.exists()) plugin.saveResource("event.yml", false);
        templates = YamlConfiguration.loadConfiguration(file);
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource("event.yml")), StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            templates.setDefaults(defaults);
        } catch (IOException ex) {
            plugin.getLogger().warning("Cannot load history templates: " + ex.getMessage());
        }
        boolean nextEnabled = plugin.getNationConfig().getBoolean("history.enabled", false);
        resumeBaseline = !enabled && nextEnabled;
        enabled = nextEnabled;
    }

    public boolean enabled() { return enabled; }

    public void initialize() {
        for (NationChronicle book : new ArrayList<>(books.values())) {
            if (!book.archived) {
                Nation nation = plugin.getNationManager().getTownyNation(book.name);
                if (nation == null) {
                    nation = com.palmergames.bukkit.towny.TownyUniverse.getInstance().getNations().stream()
                            .filter(candidate -> candidate.getUUID().toString().equals(book.nationUuid)).findFirst().orElse(null);
                    if (nation != null) rename(book.name, nation.getName());
                }
                if (nation == null || !nation.getUUID().toString().equals(book.nationUuid)) archive(book.name);
                else if (resumeBaseline) {
                    book.snapshot.clear();
                    book.snapshot.putAll(snapshot(plugin.getNationManager().getNation(nation.getName()), nation));
                    persist(book);
                }
            }
        }
        if (enabled) {
            for (WDEnation nation : plugin.getNationManager().getAllNations()) observe(nation);
        }
        resumeBaseline = false;
    }

    public void created(Nation nation) {
        if (!enabled) return;
        NationChronicle existing = active(nation.getName());
        if (existing != null && existing.nationUuid.equals(nation.getUUID().toString())) return;
        if (existing != null) archive(existing.name);
        open(nation, nation.getRegistered() > 0 ? nation.getRegistered() : System.currentTimeMillis(), "NATION_CREATED");
    }

    private NationChronicle open(Nation nation, long foundedAt, String type) {
        NationChronicle book = new NationChronicle(nation.getUUID().toString(), nation.getName(), foundedAt);
        WDEnation record = plugin.getNationManager().getNation(nation.getName());
        book.snapshot.putAll(snapshot(record, nation));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("leader", nation.hasKing() ? nation.getKing().getName() : "");
        book.events.add(event(type, book, values));
        persist(book);
        books.put(book.id, book);
        return book;
    }

    public void observe(WDEnation record) {
        if (!enabled || record == null) return;
        Nation nation = plugin.getNationManager().getTownyNation(record.getNationName());
        if (nation == null) return;
        NationChronicle book = active(record.getNationName());
        if (book == null) {
            open(nation, nation.getRegistered(), "CHRONICLER_STARTED");
            return;
        }
        if (!book.nationUuid.equals(nation.getUUID().toString())) {
            archive(book.name);
            open(nation, nation.getRegistered(), "CHRONICLER_STARTED");
            return;
        }
        Map<String, Object> next = snapshot(record, nation);
        List<Map<String, Object>> changes = new ArrayList<>();
        for (String field : List.of("government", "leader", "king", "ideology")) {
            if (!Objects.equals(book.snapshot.get(field), next.get(field))) {
                String type = switch (field) {
                    case "government" -> "GOVERNMENT_CHANGED";
                    case "ideology" -> "IDEOLOGY_CHANGED";
                    case "king" -> "KING_CHANGED";
                    default -> "LEADER_CHANGED";
                };
                changes.add(event(type, book, Map.of("before", book.snapshot.getOrDefault(field, ""),
                        "after", next.get(field))));
            }
        }
        Set<String> before = strings(book.snapshot.get("towns")), after = strings(next.get("towns"));
        for (String town : after) if (!before.contains(town)) changes.add(event("TOWN_JOINED", book, Map.of("town", town)));
        for (String town : before) if (!after.contains(town)) changes.add(event("TOWN_LEFT", book, Map.of("town", town)));
        if (!changes.isEmpty()) {
            book.events.addAll(changes);
            book.snapshot.clear();
            book.snapshot.putAll(next);
            persist(book);
        }
    }

    private Map<String, Object> snapshot(WDEnation record, Nation nation) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("government", record == null ? "" : text(record.getGovernmentType()));
        data.put("leader", record == null ? "" : text(record.getGovernmentLeader()));
        data.put("king", nation.hasKing() ? nation.getKing().getName() : "");
        data.put("ideology", record == null ? "" : text(record.getReligionName()));
        data.put("towns", nation.getTowns().stream().map(town -> town.getName()).sorted().toList());
        return data;
    }

    public void rename(String oldName, String newName) {
        NationChronicle book = active(oldName);
        if (book == null) return;
        if (enabled) book.events.add(event("NATION_RENAMED", book, Map.of("before", oldName, "after", newName)));
        book.name = newName;
        persist(book);
    }

    public void renameTown(String nation, String oldName, String newName) {
        NationChronicle book = active(nation);
        if (book == null) return;
        Set<String> towns = strings(book.snapshot.get("towns"));
        if (!towns.remove(oldName)) return;
        towns.add(newName);
        book.snapshot.put("towns", new ArrayList<>(towns));
        if (enabled) book.events.add(event("TOWN_RENAMED", book, Map.of("before", oldName, "after", newName)));
        persist(book);
    }

    public void archive(String name) {
        NationChronicle book = active(name);
        if (book == null) return;
        if (transactionDepth > 0) {
            pendingArchives.putIfAbsent(book.id, System.currentTimeMillis());
            return;
        }
        finishArchive(book, System.currentTimeMillis());
    }

    private void finishArchive(NationChronicle book, long endedAt) {
        if (book.archived) return;
        if (enabled) book.events.add(event("NATION_DISSOLVED", book, Map.of()));
        book.endedAt = endedAt;
        book.archived = true;
        persist(book);
    }

    // Treaty execution can dissolve a nation before its remaining clauses are recorded.
    public void beginSettlement() { transactionDepth++; }
    public void endSettlement() {
        if (--transactionDepth != 0) return;
        pendingArchives.forEach((id, time) -> finishArchive(books.get(id), time));
        pendingArchives.clear();
    }

    public void record(String name, String type, Map<String, Object> values) {
        if (!enabled || name == null || name.startsWith("town:")) return;
        NationChronicle book = active(name);
        if (book == null) {
            WDEnation nation = plugin.getNationManager().getNation(name);
            if (nation != null) observe(nation);
            book = active(name);
        }
        if (book == null) return;
        book.events.add(event(type, book, values));
        persist(book);
    }

    public void warStarted(MilitaryWarState war) {
        for (String nation : war.getAttackerNationNames()) record(nation, "WAR_DECLARED",
                Map.of("war", war.getWarKey(), "opponent", war.getDefenderNationName(),
                        "reason", war.getReason().name(), "role", "ATTACKER"));
        for (String nation : war.getDefenderNationNames()) record(nation, "WAR_DECLARED",
                Map.of("war", war.getWarKey(), "opponent", war.getAttackerNationName(),
                        "reason", war.getReason().name(), "role", "DEFENDER"));
    }

    public void warEnded(MilitaryWarState war) {
        Map<String, Object> values = Map.of("war", war.getWarKey(), "attacker", war.getAttackerNationName(),
                "defender", war.getDefenderNationName(), "attackerScore", war.getAttackerScore(),
                "defenderScore", war.getDefenderScore());
        for (String nation : war.getAttackerNationNames()) record(nation, "WAR_ENDED", values);
        for (String nation : war.getDefenderNationNames()) record(nation, "WAR_ENDED", values);
    }

    public void treaty(PeaceTreatyProposalState proposal, boolean success) {
        String beneficiary = proposal.getProposalType() == PeaceTreatyProposalState.ProposalType.DEMAND
                ? proposal.getProposerNationName() : proposal.getTargetNationName();
        String conceding = beneficiary.equals(proposal.getProposerNationName())
                ? proposal.getTargetNationName() : proposal.getProposerNationName();
        if (!success) {
            for (String nation : List.of(beneficiary, conceding)) record(nation, "TREATY_FAILED",
                    Map.of("war", proposal.getWarKey(), "opponent", nation.equals(beneficiary) ? conceding : beneficiary));
            return;
        }
        record(beneficiary, "WAR_SETTLED", Map.of("war", proposal.getWarKey(), "opponent", conceding, "outcome", "BENEFICIARY"));
        record(conceding, "WAR_SETTLED", Map.of("war", proposal.getWarKey(), "opponent", beneficiary, "outcome", "CONCEDING"));
        record(conceding, "WAR_DEFEAT", Map.of("war", proposal.getWarKey(), "opponent", beneficiary));
        for (var clause : proposal.getClauses()) {
            Map<String, Object> values = new LinkedHashMap<>(clause.toMap());
            values.put("war", proposal.getWarKey());
            values.put("beneficiary", beneficiary);
            values.put("conceding", conceding);
            for (String nation : List.of(beneficiary, conceding)) {
                values.put("role", nation.equals(beneficiary) ? "BENEFICIARY" : "CONCEDING");
                record(nation, "TREATY_CLAUSE", values);
            }
            if (clause.getType() == com.thatguycy.worlddynamicsengine.manager.organization.OrgManager.PeaceTreatyClauseState.ClauseType.LIBERATE_SUBJECT) {
                record(clause.getString("subjectNation"), "SUBJECT_LIBERATED",
                        Map.of("beneficiary", beneficiary, "conceding", conceding, "war", proposal.getWarKey()));
            }
        }
    }

    private Map<String, Object> event(String type, NationChronicle book, Map<String, Object> values) {
        long now = System.currentTimeMillis();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", UUID.randomUUID().toString());
        event.put("type", type);
        event.put("recordedAt", now);
        event.put("nation", book.name);
        event.put("clock", "REAL_TIME");
        event.put("day", Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay());
        if ("REALISTIC_SEASONS".equalsIgnoreCase(plugin.getNationConfig().getString("history.clock", "REAL_TIME"))) {
            String world = plugin.getNationConfig().getString("history.world", "world");
            var date = plugin.getWarTruceClock().currentDate(world);
            if (date != null) {
                event.put("clock", "REALISTIC_SEASONS");
                event.put("world", world);
                event.put("day", date.dayNumber());
                event.put("calendarDate", date.display());
                event.put("calendarSignature", date.calendarSignature());
            }
        }
        event.put("values", new LinkedHashMap<>(values));
        return event;
    }

    private void persist(NationChronicle book) {
        plugin.getDataStorageManager().saveEntity(CATEGORY, book.id, book);
        project(book);
    }

    private void project(NationChronicle book) {
        try {
            Path folder = new File(plugin.getDataFolder(), "history/" + UUID.fromString(book.id)).toPath();
            Files.createDirectories(folder);
            for (Map<String, Object> event : book.events) {
                Path target = folder.resolve(UUID.fromString(String.valueOf(event.get("id"))) + ".json");
                if (!Files.exists(target)) atomicWrite(target, gson.toJson(event));
            }
        } catch (IOException | IllegalArgumentException ex) {
            plugin.getLogger().severe("History JSON projection failed for " + book.id + ": " + ex.getMessage());
        }
    }

    private static void atomicWrite(Path target, String content) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), "history-", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    public List<NationChronicle> list() { return new ArrayList<>(books.values()); }
    public NationChronicle find(String idOrName) {
        NationChronicle exact = books.get(idOrName);
        if (exact != null) return exact;
        NationChronicle active = active(idOrName);
        if (active != null) return active;
        return books.values().stream().filter(book -> displayName(book).equalsIgnoreCase(idOrName)).findFirst().orElse(null);
    }
    private NationChronicle active(String name) {
        return books.values().stream().filter(book -> !book.archived && book.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }
    public String displayName(NationChronicle book) {
        if (!book.archived) return book.name;
        return book.name + "(" + realDate(book.foundedAt > 0 ? book.foundedAt : book.openedAt)
                + "-" + realDate(book.endedAt) + ")";
    }
    public String title(NationChronicle book) { return templates.getString("book.title", "%nation%的史书").replace("%nation%", displayName(book)); }
    public String author() { return templates.getString("book.author", "史官"); }

    public String message(String key, Map<String, Object> values) {
        String message = templates.getString("messages." + key, key);
        for (var entry : values.entrySet()) message = message.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
    }

    public List<String> render(NationChronicle book) {
        List<String> lines = new ArrayList<>();
        String lastDay = "";
        for (Map<String, Object> event : book.events) {
            String key = event.get("clock") + ":" + event.getOrDefault("world", "") + ":"
                    + event.getOrDefault("calendarSignature", "") + ":" + event.get("day");
            if (!key.equals(lastDay)) {
                String date = event.containsKey("calendarDate") ? String.valueOf(event.get("calendarDate"))
                        : realDate(((Number) event.get("recordedAt")).longValue());
                lines.add(templates.getString("day", "[%date%]").replace("%date%", date));
                lastDay = key;
            }
            Map<String, String> values = new LinkedHashMap<>();
            values.put("nation", String.valueOf(event.get("nation")));
            String date = event.containsKey("calendarDate") ? String.valueOf(event.get("calendarDate"))
                    : realDate(((Number) event.get("recordedAt")).longValue());
            String[] parts = date.split("[/.]");
            values.put("date", parts.length == 3 ? parts[0] + "年" + parts[1] + "月" + parts[2] + "日" : date);
            String type = String.valueOf(event.get("type"));
            if (event.get("values") instanceof Map<?, ?> map) {
                map.forEach((field, value) -> values.put(String.valueOf(field), label(String.valueOf(value))));
                if (map.get("parameters") instanceof Map<?, ?> params) {
                    params.forEach((field, value) -> values.put(String.valueOf(field), String.valueOf(value)));
                }
                if ("TREATY_CLAUSE".equals(type)) type += "_" + map.get("type") + "_" + map.get("role");
            }
            String line = templates.getString("events." + type, templates.getString("unknown", "%nation%发生了事件：%event%"));
            values.put("event", type);
            for (var entry : values.entrySet()) line = line.replace("%" + entry.getKey() + "%", entry.getValue());
            lines.add(line);
        }
        return lines;
    }

    public Path exportText(NationChronicle book) throws IOException {
        Path folder = new File(plugin.getDataFolder(), "history/" + book.id).toPath();
        Files.createDirectories(folder);
        Path target = folder.resolve("chronicle.txt");
        atomicWrite(target, title(book) + System.lineSeparator() + String.join(System.lineSeparator(), render(book)));
        return target;
    }

    private String label(String value) { return templates.getString("labels." + value, value); }
    private static String realDate(long time) { return REAL_DATE.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())); }
    private static String text(String value) { return value == null ? "" : value; }
    private static Set<String> strings(Object value) {
        Set<String> result = new LinkedHashSet<>();
        if (value instanceof Collection<?> list) list.forEach(item -> result.add(String.valueOf(item)));
        return result;
    }
}
