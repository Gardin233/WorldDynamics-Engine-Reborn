package com.thatguycy.worlddynamicsengine.manager.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Town;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.TownChronicle;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

/** Records town facts in the database and projects each event to a recoverable JSON file. */
public final class TownHistoryManager {
    private static final String CATEGORY = "town_history";
    private static final DateTimeFormatter REAL_DATE = DateTimeFormatter.ofPattern("yyyy.M.d");

    private final WorldDynamicsEngine plugin;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final Map<String, TownChronicle> books;
    private YamlConfiguration templates;
    private boolean enabled;
    private boolean resumeBaseline;

    public TownHistoryManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        books = plugin.getDataStorageManager().loadStringKeyed(CATEGORY, TownChronicle.class);
        reload();
        for (TownChronicle book : books.values()) project(book);
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "event.yml");
        if (!file.exists()) plugin.saveResource("event.yml", false);
        templates = YamlConfiguration.loadConfiguration(file);
        try (Reader reader = new InputStreamReader(
                Objects.requireNonNull(plugin.getResource("event.yml")), StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            templates.setDefaults(defaults);
        } catch (IOException ex) {
            plugin.getLogger().warning("Cannot load town history templates: " + ex.getMessage());
        }
        boolean nextEnabled = plugin.getTownConfig().getBoolean("history.enabled", false);
        resumeBaseline = !enabled && nextEnabled;
        enabled = nextEnabled;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Reconciles persisted town identities after Towny has completed its startup import. */
    public void initialize() {
        for (TownChronicle book : new ArrayList<>(books.values())) {
            if (book.archived) continue;
            Town town = townByUuid(book.townUuid);
            if (town == null) {
                archive(book.id);
                continue;
            }
            if (!book.name.equals(town.getName())) rename(book, town.getName());
            if (resumeBaseline) {
                WDEtown record = plugin.getTownManager().getTown(town.getName());
                book.snapshot.clear();
                book.snapshot.putAll(snapshot(record, town));
                persist(book);
            }
        }

        if (enabled) {
            for (WDEtown record : plugin.getTownManager().getAllTowns()) observe(record);
        }
        resumeBaseline = false;
    }

    public void created(Town town) {
        if (!enabled || town == null) return;
        TownChronicle existing = active(town.getUUID().toString());
        if (existing != null) return;
        TownChronicle sameName = activeByName(town.getName());
        if (sameName != null) archive(sameName.id);
        open(town, town.getRegistered() > 0 ? town.getRegistered() : System.currentTimeMillis(),
                "TOWN_CREATED");
    }

    /** Snapshot changes from the TownManager save path so all town governance commands are covered. */
    public void observe(WDEtown record) {
        if (!enabled || record == null) return;
        Town town = townByName(record.getTownName());
        if (town == null) return;

        TownChronicle book = active(town.getUUID().toString());
        if (book == null) {
            TownChronicle sameName = activeByName(town.getName());
            if (sameName != null) archive(sameName.id);
            open(town, town.getRegistered(), "TOWN_CHRONICLER_STARTED");
            return;
        }
        if (!book.name.equals(town.getName())) rename(book, town.getName());

        Map<String, Object> next = snapshot(record, town);
        List<Map<String, Object>> changes = new ArrayList<>();
        for (String field : List.of("government", "leader", "mayor", "religion", "nation", "population")) {
            if (!Objects.equals(book.snapshot.get(field), next.get(field))) {
                String type = switch (field) {
                    case "government" -> "TOWN_GOVERNMENT_CHANGED";
                    case "leader" -> "TOWN_LEADER_CHANGED";
                    case "mayor" -> "TOWN_MAYOR_CHANGED";
                    case "religion" -> "TOWN_IDEOLOGY_CHANGED";
                    case "nation" -> "TOWN_NATION_CHANGED";
                    default -> "TOWN_POPULATION_CHANGED";
                };
                changes.add(event(type, book, Map.of(
                        "before", book.snapshot.getOrDefault(field, ""),
                        "after", next.getOrDefault(field, ""))));
            }
        }
        if (!changes.isEmpty()) {
            book.events.addAll(changes);
            book.snapshot.clear();
            book.snapshot.putAll(next);
            persist(book);
        }
    }

    private TownChronicle open(Town town, long foundedAt, String eventType) {
        TownChronicle book = new TownChronicle(town.getUUID().toString(), town.getName(), foundedAt);
        book.snapshot.putAll(snapshot(plugin.getTownManager().getTown(town.getName()), town));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("mayor", town.getMayor() == null ? "" : town.getMayor().getName());
        values.put("population", town.getNumResidents());
        book.events.add(event(eventType, book, values));
        books.put(book.id, book);
        persist(book);
        return book;
    }

    private Map<String, Object> snapshot(WDEtown record, Town town) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("government", record == null ? "" : text(record.getGovernmentType()));
        data.put("leader", record == null ? "" : text(record.getTownLeader()));
        data.put("mayor", town.getMayor() == null ? "" : town.getMayor().getName());
        data.put("religion", record == null ? "" : text(record.getReligionName()));
        data.put("nation", town.getNationOrNull() == null ? "None" : town.getNationOrNull().getName());
        data.put("population", town.getNumResidents());
        return data;
    }

    private void rename(TownChronicle book, String newName) {
        String oldName = book.name;
        if (oldName.equals(newName)) return;
        if (enabled) book.events.add(event("TOWN_RENAMED", book, Map.of("before", oldName, "after", newName)));
        book.name = newName;
        persist(book);
    }

    /** Soft-archives a town before TownManager removes its runtime record. */
    public void archive(String idOrName) {
        TownChronicle book = books.get(idOrName);
        if (book == null) book = activeByName(idOrName);
        if (book == null || book.archived) return;
        if (enabled) book.events.add(event("TOWN_DISSOLVED", book, Map.of()));
        book.endedAt = System.currentTimeMillis();
        book.archived = true;
        persist(book);
    }

    private Map<String, Object> event(String type, TownChronicle book, Map<String, Object> values) {
        long now = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", UUID.randomUUID().toString());
        result.put("type", type);
        result.put("recordedAt", now);
        result.put("town", book.name);
        result.put("clock", "REAL_TIME");
        result.put("day", Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay());
        if ("REALISTIC_SEASONS".equalsIgnoreCase(plugin.getTownConfig().getString("history.clock", "REAL_TIME"))) {
            String world = plugin.getTownConfig().getString("history.world", "world");
            var date = plugin.getWarTruceClock().currentDate(world);
            if (date != null) {
                result.put("clock", "REALISTIC_SEASONS");
                result.put("world", world);
                result.put("day", date.dayNumber());
                result.put("calendarDate", date.display());
                result.put("calendarSignature", date.calendarSignature());
            }
        }
        result.put("values", new LinkedHashMap<>(values));
        return result;
    }

    private void persist(TownChronicle book) {
        plugin.getDataStorageManager().saveEntity(CATEGORY, book.id, book);
        project(book);
    }

    private void project(TownChronicle book) {
        try {
            Path folder = new File(plugin.getDataFolder(), "history/towns/" + UUID.fromString(book.id)).toPath();
            Files.createDirectories(folder);
            for (Map<String, Object> event : book.events) {
                Path target = folder.resolve(UUID.fromString(String.valueOf(event.get("id"))) + ".json");
                if (!Files.exists(target)) atomicWrite(target, gson.toJson(event));
            }
        } catch (IOException | IllegalArgumentException ex) {
            plugin.getLogger().severe("Town history JSON projection failed for " + book.id + ": " + ex.getMessage());
        }
    }

    private static void atomicWrite(Path target, String content) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), "town-history-", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public List<TownChronicle> list() {
        return books.values().stream()
                .sorted(Comparator.comparingLong(book -> book.openedAt))
                .toList();
    }

    public TownChronicle find(String idOrName) {
        if (idOrName == null) return null;
        TownChronicle exact = books.get(idOrName);
        if (exact != null) return exact;
        TownChronicle current = activeByName(idOrName);
        if (current != null) return current;
        return books.values().stream()
                .filter(book -> displayName(book).equalsIgnoreCase(idOrName))
                .findFirst().orElse(null);
    }

    public String displayName(TownChronicle book) {
        if (!book.archived) return book.name;
        return book.name + "(" + realDate(book.foundedAt > 0 ? book.foundedAt : book.openedAt)
                + "-" + realDate(book.endedAt);
    }

    public String title(TownChronicle book) {
        return templates.getString("townBook.title", "%town%的城镇史书")
                .replace("%town%", displayName(book));
    }

    public String author() {
        return templates.getString("townBook.author", "镇史官");
    }

    public String message(String key, Map<String, Object> values) {
        String message = templates.getString("townMessages." + key, key);
        for (var entry : values.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        }
        return com.thatguycy.worlddynamicsengine.util.TextColors.translateAlternateColorCodes('&', message);
    }

    public List<String> render(TownChronicle book) {
        List<String> lines = new ArrayList<>();
        String lastDay = "";
        for (Map<String, Object> event : book.events) {
            String key = event.get("clock") + ":" + event.getOrDefault("world", "") + ":"
                    + event.getOrDefault("calendarSignature", "") + ":" + event.get("day");
            if (!key.equals(lastDay)) {
                String date = event.containsKey("calendarDate") ? String.valueOf(event.get("calendarDate"))
                        : realDate(((Number) event.get("recordedAt")).longValue());
                lines.add(templates.getString("townDay", "[%date%]").replace("%date%", date));
                lastDay = key;
            }

            Map<String, String> values = new LinkedHashMap<>();
            values.put("town", String.valueOf(event.get("town")));
            String date = event.containsKey("calendarDate") ? String.valueOf(event.get("calendarDate"))
                    : realDate(((Number) event.get("recordedAt")).longValue());
            String[] parts = date.split("[/.]");
            values.put("date", parts.length == 3 ? parts[0] + "年" + parts[1] + "月" + parts[2] + "日" : date);
            String type = String.valueOf(event.get("type"));
            if (event.get("values") instanceof Map<?, ?> map) {
                map.forEach((field, value) -> values.put(String.valueOf(field), label(String.valueOf(value))));
            }
            String line = templates.getString("townEvents." + type,
                    templates.getString("townUnknown", "%town%发生了事件：%event%。"));
            values.put("event", type);
            for (var entry : values.entrySet()) {
                line = line.replace("%" + entry.getKey() + "%", entry.getValue());
            }
            lines.add(line);
        }
        return lines;
    }

    public Path exportText(TownChronicle book) throws IOException {
        Path folder = new File(plugin.getDataFolder(), "history/towns/" + book.id).toPath();
        Files.createDirectories(folder);
        Path target = folder.resolve("chronicle.txt");
        atomicWrite(target, title(book) + System.lineSeparator()
                + String.join(System.lineSeparator(), render(book)));
        return target;
    }

    private String label(String value) {
        return templates.getString("townLabels." + value, templates.getString("labels." + value, value));
    }

    private TownChronicle active(String townUuid) {
        return books.values().stream()
                .filter(book -> !book.archived && book.townUuid.equalsIgnoreCase(townUuid))
                .findFirst().orElse(null);
    }

    private TownChronicle activeByName(String name) {
        return books.values().stream()
                .filter(book -> !book.archived && book.name.equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    private Town townByUuid(String townUuid) {
        try {
            return TownyUniverse.getInstance().getTown(UUID.fromString(townUuid));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Town townByName(String townName) {
        try {
            return TownyUniverse.getInstance().getTown(townName);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String realDate(long time) {
        return REAL_DATE.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
