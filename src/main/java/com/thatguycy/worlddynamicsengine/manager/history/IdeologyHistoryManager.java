package com.thatguycy.worlddynamicsengine.manager.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.ChurchChronicle;
import com.thatguycy.worlddynamicsengine.model.IdeologyChronicle;
import com.thatguycy.worlddynamicsengine.model.IdeologyCouncilProposal;
import com.thatguycy.worlddynamicsengine.model.WDEChurch;
import com.thatguycy.worlddynamicsengine.model.WDEnation;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import java.math.BigDecimal;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

/** Independent, privacy-filtered ideology and church timelines. */
public final class IdeologyHistoryManager {
    private static final String IDEOLOGY_CATEGORY = "ideology_history";
    private static final String CHURCH_CATEGORY = "church_history";
    private static final DateTimeFormatter REAL_DATE = DateTimeFormatter.ofPattern("yyyy.M.d");
    private static final DateTimeFormatter REAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy.M.d HH:mm");

    private final WorldDynamicsEngine plugin;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final Map<String, IdeologyChronicle> ideologyBooks = new LinkedHashMap<>();
    private final Map<String, ChurchChronicle> churchBooks = new LinkedHashMap<>();
    private YamlConfiguration templates;
    private boolean enabled;
    private boolean resumeBaseline;
    private boolean initialized;

    public IdeologyHistoryManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        ideologyBooks.putAll(plugin.getDataStorageManager()
                .loadStringKeyed(IDEOLOGY_CATEGORY, IdeologyChronicle.class));
        churchBooks.putAll(plugin.getDataStorageManager()
                .loadStringKeyed(CHURCH_CATEGORY, ChurchChronicle.class));
        reload();
        ideologyBooks.values().forEach(this::project);
        churchBooks.values().forEach(this::project);
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
            plugin.getLogger().warning("Cannot load ideology history templates: " + ex.getMessage());
        }
        boolean nextEnabled = plugin.getIdeologyConfig().getBoolean("history.enabled", false);
        resumeBaseline = !enabled && nextEnabled;
        enabled = nextEnabled;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Reconciles UUID identities after ideology and church records have loaded. */
    public synchronized void initialize() {
        List<WDEreligion> currentIdeologies = plugin.getReligionManager().getAllReligions();
        Map<String, AffiliationCounts> counts = enabled
                ? affiliationCounts(currentIdeologies) : Map.of();
        for (IdeologyChronicle book : new ArrayList<>(ideologyBooks.values())) {
            if (book.archived) continue;
            WDEreligion current = findIdeology(currentIdeologies, book.ideologyUuid);
            if (current == null) {
                archiveIdeology(book, "IDEOLOGY_DISSOLVED");
                continue;
            }
            reconcileIdeologyName(book, current.getReligionName());
            if (resumeBaseline && enabled) {
                book.snapshot.clear();
                book.snapshot.putAll(snapshot(current, counts));
                seedProposalStates(book, current, true);
                persist(book);
            }
        }
        if (enabled) {
            for (WDEreligion ideology : currentIdeologies) {
                IdeologyChronicle book = activeIdeology(ideology.getIdeologyUuid().toString());
                if (book == null) {
                    openIdeology(ideology, "IDEOLOGY_CHRONICLER_STARTED", counts);
                    book = activeIdeology(ideology.getIdeologyUuid().toString());
                }
                if (book != null) seedProposalStates(book, ideology, false);
            }
        }

        List<WDEChurch> currentChurches = plugin.getIdeologyChurchManager().getAllChurches();
        for (ChurchChronicle book : new ArrayList<>(churchBooks.values())) {
            if (book.archived) continue;
            WDEChurch current = findChurch(currentChurches, book.churchUuid);
            if (current == null) {
                archiveChurch(book, "CHURCH_ARCHIVED", System.currentTimeMillis());
                continue;
            }
            reconcileChurchName(book, current.getChurchName());
        }
        if (enabled) {
            for (WDEChurch church : currentChurches) {
                ChurchChronicle any = anyChurch(church.getChurchUuid().toString());
                if (any == null) {
                    openChurch(church, "CHURCH_CHRONICLER_STARTED", true);
                } else if (!any.archived) {
                    observeChurch(church, resumeBaseline);
                }
            }
        }
        initialized = true;
    }

    public synchronized void observeIdeologies(Collection<WDEreligion> ideologies) {
        if (ideologies == null) return;
        List<WDEreligion> current = List.copyOf(ideologies);
        Set<String> identities = new LinkedHashSet<>();
        for (WDEreligion ideology : current) identities.add(ideology.getIdeologyUuid().toString());
        if (enabled) {
            Map<String, AffiliationCounts> counts = affiliationCounts(current);
            for (WDEreligion ideology : current) observeIdeology(ideology, counts);
        }
        for (IdeologyChronicle book : new ArrayList<>(ideologyBooks.values())) {
            if (!book.archived && !identities.contains(book.ideologyUuid)) {
                archiveIdeology(book, "IDEOLOGY_DISSOLVED");
            }
        }
    }

    public synchronized void observeIdeology(WDEreligion ideology) {
        if (ideology == null || !enabled) return;
        observeIdeology(ideology, affiliationCounts(plugin.getReligionManager().getAllReligions()));
    }

    /** Recounts organization affiliations without exposing any individual follower identity. */
    public synchronized void refreshAffiliations() {
        if (!enabled) return;
        observeIdeologies(plugin.getReligionManager().getAllReligions());
    }

    private void observeIdeology(WDEreligion ideology, Map<String, AffiliationCounts> counts) {
        String identity = ideology.getIdeologyUuid().toString();
        IdeologyChronicle book = activeIdeology(identity);
        if (book == null) {
            IdeologyChronicle sameName = activeIdeologyByName(ideology.getReligionName());
            if (sameName != null) archiveIdeology(sameName, "IDEOLOGY_DISSOLVED");
            openIdeology(ideology, initialized ? "IDEOLOGY_CREATED" : "IDEOLOGY_CHRONICLER_STARTED", counts);
            return;
        }
        reconcileIdeologyName(book, ideology.getReligionName());

        Map<String, Object> next = snapshot(ideology, counts);
        List<Map<String, Object>> changes = new ArrayList<>();
        for (String field : List.of("governance", "leader", "treasury", "tithe",
                "memes", "doctrines", "cardinals", "electionOpen", "parentIdeology", "parentChurch")) {
            if (!Objects.equals(book.snapshot.get(field), next.get(field))) {
                String type = ideologyEventType(field);
                changes.add(event(type, book.name, "ideology", System.currentTimeMillis(),
                        Map.of("before", book.snapshot.getOrDefault(field, ""),
                                "after", next.getOrDefault(field, ""))));
            }
        }
        for (String field : List.of("playerAffiliations", "townAffiliations", "nationAffiliations")) {
            if (!Objects.equals(book.snapshot.get(field), next.get(field))) {
                changes.add(event(switch (field) {
                    case "playerAffiliations" -> "IDEOLOGY_PLAYER_AFFILIATIONS_CHANGED";
                    case "townAffiliations" -> "IDEOLOGY_TOWN_AFFILIATIONS_CHANGED";
                    default -> "IDEOLOGY_NATION_AFFILIATIONS_CHANGED";
                }, book.name, "ideology", System.currentTimeMillis(),
                        Map.of("before", book.snapshot.getOrDefault(field, 0),
                                "after", next.getOrDefault(field, 0))));
            }
        }
        for (String field : List.of("excommunicatedPlayers", "excommunicatedTowns", "excommunicatedNations")) {
            if (!Objects.equals(book.snapshot.get(field), next.get(field))) {
                changes.add(event("IDEOLOGY_EXCOMMUNICATIONS_CHANGED", book.name, "ideology",
                        System.currentTimeMillis(), Map.of("scope", switch (field) {
                            case "excommunicatedPlayers" -> "玩家";
                            case "excommunicatedTowns" -> "城镇";
                            default -> "国家";
                        },
                                "before", book.snapshot.getOrDefault(field, 0),
                                "after", next.getOrDefault(field, 0))));
            }
        }
        if (!changes.isEmpty()) {
            book.events.addAll(changes);
            book.snapshot.clear();
            book.snapshot.putAll(next);
            persist(book);
        }
    }

    public synchronized void observeChurches(Collection<WDEChurch> churches) {
        if (!enabled || churches == null) return;
        for (WDEChurch church : churches) observeChurch(church, false);
    }

    public synchronized void observeChurch(WDEChurch church) {
        if (church == null || !enabled) return;
        observeChurch(church, false);
    }

    private void observeChurch(WDEChurch church, boolean baseline) {
        String identity = church.getChurchUuid().toString();
        ChurchChronicle book = activeChurch(identity);
        if (book == null) {
            openChurch(church, initialized ? "CHURCH_CREATED" : "CHURCH_CHRONICLER_STARTED",
                    !initialized);
            return;
        }
        reconcileChurchName(book, church.getChurchName());
        Map<String, Object> next = churchSnapshot(church, church.getHistory().size());
        int cursor = readInt(book.snapshot.get("historyCursor"), 0);
        List<Map<String, Object>> sourceEvents = church.getHistory();
        if (!baseline) {
            int memberCount = readInt(book.snapshot.get("memberCount"), church.getMembers().size());
            BigDecimal treasury = decimal(book.snapshot.get("treasury"));
            for (int index = Math.min(cursor, sourceEvents.size()); index < sourceEvents.size(); index++) {
                Map<String, Object> raw = sourceEvents.get(index);
                String rawType = String.valueOf(raw.getOrDefault("type", ""));
                Map<String, Object> parameters = raw.get("parameters") instanceof Map<?, ?> map
                        ? stringMap(map) : Map.of();
                if ("MEMBER_JOINED".equals(rawType)) memberCount++;
                if ("MEMBER_LEFT".equals(rawType)) memberCount = Math.max(0, memberCount - 1);
                BigDecimal treasuryBefore = treasury;
                BigDecimal amount = decimal(parameters.get("amount"));
                if ("TREASURY_CREDIT".equals(rawType)) treasury = treasury.add(amount);
                if ("TREASURY_DEBIT".equals(rawType)) treasury = treasury.subtract(amount);
                Map<String, Object> converted = renderableChurchEvent(
                        church, raw, memberCount, treasuryBefore, treasury);
                if (converted != null) book.events.add(converted);
            }
        }
        book.snapshot.clear();
        book.snapshot.putAll(next);
        if (baseline && !church.isActive()) {
            book.endedAt = Math.max(book.openedAt, church.getClosedAt());
            book.archived = true;
        } else if (!church.isActive()) {
            book.endedAt = church.getClosedAt() > 0L ? church.getClosedAt() : System.currentTimeMillis();
            book.archived = true;
            persist(book);
            return;
        }
        persist(book);
    }

    public synchronized void observeProposal(IdeologyCouncilProposal proposal) {
        if (!enabled || proposal == null) return;
        IdeologyChronicle book = activeIdeology(proposal.getSourceIdeologyUuid().toString());
        if (book == null) {
            WDEreligion ideology = plugin.getReligionManager().getReligion(proposal.getSourceIdeologyName());
            if (ideology != null) {
                observeIdeology(ideology);
                book = activeIdeology(proposal.getSourceIdeologyUuid().toString());
            }
        }
        if (book == null) return;

        String key = proposal.getProposalUuid().toString();
        String current = proposal.getState().name();
        String previous = book.proposalStates.get(key);
        if (previous == null) {
            book.events.add(event("COUNCIL_PROPOSAL_CREATED", book.name, "ideology",
                    proposal.getCreatedAt(), Map.of("proposalType", proposal.getType().name())));
        } else if (!previous.equals(current)) {
            book.events.add(event("COUNCIL_PROPOSAL_STATE_CHANGED", book.name, "ideology",
                    proposal.getResolvedAt() > 0 ? proposal.getResolvedAt() : System.currentTimeMillis(),
                    Map.of("proposalType", proposal.getType().name(),
                            "before", previous, "after", current)));
        }
        book.proposalStates.put(key, current);
        persist(book);

        Set<String> affectedChurches = new LinkedHashSet<>();
        if (proposal.getProposerChurchUuid() != null) {
            affectedChurches.add(proposal.getProposerChurchUuid().toString());
        }
        if (proposal.getTargetChurchUuid() != null) {
            affectedChurches.add(proposal.getTargetChurchUuid().toString());
        }
        String type = previous == null ? "CHURCH_PROPOSAL_CREATED" : "CHURCH_PROPOSAL_STATE_CHANGED";
        for (String churchId : affectedChurches) {
            ChurchChronicle churchBook = anyChurch(churchId);
            if (churchBook == null) continue;
            Map<String, Object> values = previous == null
                    ? Map.of("proposalType", proposal.getType().name())
                    : Map.of("proposalType", proposal.getType().name(), "before", previous, "after", current);
            churchBook.events.add(event(type, churchBook.name, "church",
                    proposal.getResolvedAt() > 0 ? proposal.getResolvedAt()
                            : proposal.getCreatedAt(), values));
            persist(churchBook);
        }
    }

    private void seedProposalStates(IdeologyChronicle book, WDEreligion ideology, boolean reset) {
        boolean changed = reset && !book.proposalStates.isEmpty();
        if (reset) book.proposalStates.clear();
        for (IdeologyCouncilProposal proposal
                : plugin.getIdeologyChurchManager().getProposals(ideology.getReligionName(), false)) {
            changed |= book.proposalStates.putIfAbsent(proposal.getProposalUuid().toString(),
                    proposal.getState().name()) == null;
        }
        if (changed) persist(book);
    }

    private void reconcileIdeologyName(IdeologyChronicle book, String nextName) {
        if (book.name.equals(nextName)) return;
        if (enabled) book.events.add(event("IDEOLOGY_RENAMED", book.name, "ideology",
                System.currentTimeMillis(), Map.of("before", book.name, "after", nextName)));
        book.name = nextName;
        persist(book);
    }

    private void reconcileChurchName(ChurchChronicle book, String nextName) {
        if (book.name.equals(nextName)) return;
        if (enabled) book.events.add(event("CHURCH_RENAMED", book.name, "church",
                System.currentTimeMillis(), Map.of("before", book.name, "after", nextName)));
        book.name = nextName;
        persist(book);
    }

    private List<Map<String, Object>> render(IdeologyChronicle book) {
        return renderEvents(book.events, "ideology", "ideologyEvents", "ideologyDay");
    }

    private List<Map<String, Object>> render(ChurchChronicle book) {
        return renderEvents(book.events, "church", "churchEvents", "churchDay");
    }

    private List<Map<String, Object>> renderEvents(List<Map<String, Object>> events, String nameField,
            String eventRoot, String dayKey) {
        List<Map<String, Object>> result = new ArrayList<>();
        String lastDay = "";
        for (Map<String, Object> event : events) {
            String group = event.get("clock") + ":" + event.getOrDefault("world", "") + ":"
                    + event.getOrDefault("calendarSignature", "") + ":" + event.get("day");
            if (!group.equals(lastDay)) {
                long recordedAt = event.get("recordedAt") instanceof Number number
                        ? number.longValue() : System.currentTimeMillis();
                String date = event.containsKey("calendarDate") ? String.valueOf(event.get("calendarDate"))
                        : realDate(recordedAt);
                result.add(new LinkedHashMap<>(Map.of("_day",
                        templates.getString(dayKey, "[%date%]").replace("%date%", date))));
                lastDay = group;
            }
            Map<String, String> values = new LinkedHashMap<>();
            values.put(nameField, String.valueOf(event.get(nameField)));
            long recordedAt = event.get("recordedAt") instanceof Number number
                    ? number.longValue() : System.currentTimeMillis();
            String date = event.containsKey("calendarDate") ? String.valueOf(event.get("calendarDate"))
                    : realDate(recordedAt);
            String[] dateParts = date.split("[/.]");
            values.put("date", dateParts.length == 3
                    ? dateParts[0] + "年" + dateParts[1] + "月" + dateParts[2] + "日" : date);
            String type = String.valueOf(event.get("type"));
            if (event.get("values") instanceof Map<?, ?> data) {
                data.forEach((key, value) -> values.put(String.valueOf(key), displayValue(String.valueOf(value))));
            }
            String line = templates.getString(eventRoot + "." + type,
                    templates.getString(nameField + "Unknown", "%" + nameField + "%发生了事件：%event%。"));
            values.put("event", type);
            for (var entry : values.entrySet()) line = line.replace("%" + entry.getKey() + "%", entry.getValue());
            result.add(new LinkedHashMap<>(Map.of("_line",
                    com.thatguycy.worlddynamicsengine.util.TextColors.translateAlternateColorCodes('&', line))));
        }
        return result;
    }

    public List<String> renderIdeology(IdeologyChronicle book) {
        return render(book).stream().map(line -> String.valueOf(
                line.containsKey("_day") ? line.get("_day") : line.get("_line"))).toList();
    }

    public List<String> renderChurch(ChurchChronicle book) {
        return render(book).stream().map(line -> String.valueOf(
                line.containsKey("_day") ? line.get("_day") : line.get("_line"))).toList();
    }

    public List<IdeologyChronicle> ideologies() {
        return ideologyBooks.values().stream()
                .sorted(Comparator.comparingLong(book -> book.openedAt)).toList();
    }

    public List<ChurchChronicle> churches() {
        return churchBooks.values().stream()
                .sorted(Comparator.comparingLong(book -> book.openedAt)).toList();
    }

    public IdeologyChronicle findIdeology(String token) {
        if (token == null) return null;
        IdeologyChronicle exact = ideologyBooks.get(token);
        if (exact != null) return exact;
        IdeologyChronicle active = activeIdeologyByName(token);
        if (active != null) return active;
        return ideologyBooks.values().stream()
                .filter(book -> ideologyDisplayName(book).equalsIgnoreCase(token))
                .findFirst().orElse(null);
    }

    public ChurchChronicle findChurch(String token) {
        if (token == null) return null;
        ChurchChronicle exact = churchBooks.get(token);
        if (exact != null) return exact;
        List<ChurchChronicle> matches = churchBooks.values().stream()
                .filter(book -> book.name.equalsIgnoreCase(token)
                        || churchDisplayName(book).equalsIgnoreCase(token))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    public String ideologyDisplayName(IdeologyChronicle book) {
        return book.archived ? archiveDisplay(book.name, book.foundedAt, book.openedAt, book.endedAt) : book.name;
    }

    public String churchDisplayName(ChurchChronicle book) {
        String value = book.name + " - " + book.ideologyName;
        return book.archived ? archiveDisplay(value, book.foundedAt, book.openedAt, book.endedAt) : value;
    }

    public String ideologyTitle(IdeologyChronicle book) {
        return templates.getString("ideologyBook.title", "%ideology%的意识形态史书")
                .replace("%ideology%", ideologyDisplayName(book));
    }

    public String churchTitle(ChurchChronicle book) {
        return templates.getString("churchBook.title", "%church%所属%ideology%的教会史书")
                .replace("%church%", churchDisplayName(book)).replace("%ideology%", book.ideologyName);
    }

    public String ideologyAuthor() {
        return templates.getString("ideologyBook.author", "意识形态史官");
    }

    public String churchAuthor() {
        return templates.getString("churchBook.author", "教会史官");
    }

    public String message(boolean church, String key, Map<String, Object> values) {
        String path = (church ? "churchMessages." : "ideologyMessages.") + key;
        String message = templates.getString(path, key);
        for (var entry : values.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        }
        return com.thatguycy.worlddynamicsengine.util.TextColors.translateAlternateColorCodes('&', message);
    }

    public Path exportIdeologyText(IdeologyChronicle book) throws IOException {
        return exportText(book.id, "history/ideologies", ideologyTitle(book), renderIdeology(book));
    }

    public Path exportChurchText(ChurchChronicle book) throws IOException {
        return exportText(book.id, "history/churches", churchTitle(book), renderChurch(book));
    }

    private Path exportText(String id, String root, String title, List<String> lines) throws IOException {
        Path folder = new File(plugin.getDataFolder(), root + "/" + id).toPath();
        Files.createDirectories(folder);
        Path target = folder.resolve("chronicle.txt");
        atomicWrite(target, title + System.lineSeparator() + String.join(System.lineSeparator(), lines));
        return target;
    }

    private void persist(IdeologyChronicle book) {
        try {
            plugin.getDataStorageManager().saveEntity(IDEOLOGY_CATEGORY, book.id, book);
            project(book);
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Ideology history persistence failed for " + book.id + ": " + ex.getMessage());
        }
    }

    private void persist(ChurchChronicle book) {
        try {
            plugin.getDataStorageManager().saveEntity(CHURCH_CATEGORY, book.id, book);
            project(book);
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Church history persistence failed for " + book.id + ": " + ex.getMessage());
        }
    }

    private void project(IdeologyChronicle book) {
        projectEvents(book.id, "history/ideologies", book.events);
    }

    private void project(ChurchChronicle book) {
        projectEvents(book.id, "history/churches", book.events);
    }

    private void projectEvents(String id, String root, List<Map<String, Object>> events) {
        try {
            Path folder = new File(plugin.getDataFolder(), root + "/" + UUID.fromString(id)).toPath();
            Files.createDirectories(folder);
            for (Map<String, Object> event : events) {
                Path target = folder.resolve(UUID.fromString(String.valueOf(event.get("id"))) + ".json");
                if (!Files.exists(target)) atomicWrite(target, gson.toJson(event));
            }
        } catch (IOException | IllegalArgumentException ex) {
            plugin.getLogger().severe("Ideology history JSON projection failed for " + id + ": " + ex.getMessage());
        }
    }

    private static void atomicWrite(Path target, String content) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), "ideology-history-", ".tmp");
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

    private void persistChurchChapter(ChurchChronicle book) {
        persist(book);
    }

    private void openIdeology(WDEreligion ideology, String type, Map<String, AffiliationCounts> counts) {
        IdeologyChronicle book = new IdeologyChronicle(ideology);
        book.snapshot.putAll(snapshot(ideology, counts));
        Map<String, Object> values = new LinkedHashMap<>();
        long at = System.currentTimeMillis();
        if ("IDEOLOGY_CREATED".equals(type)) {
            at = ideology.getCreatedAt();
            values.put("founder", ideology.getFounderName());
            if (ideology.getParentIdeologyUuid() != null) {
                values.put("parent", ideologyNameByUuid(ideology.getParentIdeologyUuid().toString()));
            }
            if (ideology.getParentChurchUuid() != null) {
                values.put("church", churchNameByUuid(ideology.getParentChurchUuid().toString()));
            }
        }
        book.events.add(event(type, book.name, "ideology", at, values));
        ideologyBooks.put(book.id, book);
        persist(book);
    }

    private void openChurch(WDEChurch church, String type, boolean chroniclerStart) {
        ChurchChronicle book = new ChurchChronicle(church);
        List<Map<String, Object>> source = church.getHistory();
        book.snapshot.putAll(churchSnapshot(church, source.size()));
        Map<String, Object> values = chroniclerStart ? Map.of()
                : Map.of("ideology", church.getSourceIdeologyName(), "head", church.getHeadName());
        long at = chroniclerStart ? System.currentTimeMillis() : church.getCreatedAt();
        book.events.add(event(type, book.name, "church", at, values));
        if (!church.isActive()) {
            book.archived = true;
            book.endedAt = Math.max(book.openedAt, church.getClosedAt());
        }
        churchBooks.put(book.id, book);
        persist(book);
    }

    private void archiveIdeology(IdeologyChronicle book, String eventType) {
        if (book.archived) return;
        if (enabled) book.events.add(event(eventType, book.name, "ideology",
                System.currentTimeMillis(), Map.of()));
        book.endedAt = System.currentTimeMillis();
        book.archived = true;
        persist(book);
    }

    private void archiveChurch(ChurchChronicle book, String eventType, long endedAt) {
        if (book.archived) return;
        long archiveAt = endedAt > 0L ? endedAt : System.currentTimeMillis();
        if (eventType != null && enabled) {
            book.events.add(event(eventType, book.name, "church", archiveAt, Map.of()));
        }
        book.endedAt = archiveAt;
        book.archived = true;
        persist(book);
    }

    private void appendChurchEvent(ChurchChronicle book, String type, long at, Map<String, Object> values) {
        book.events.add(event(type, book.name, "church", at, values));
        persistChurchChapter(book);
    }

    private Map<String, Object> event(String type, String name, String kind, long at,
            Map<String, Object> values) {
        long timestamp = at > 0L ? at : System.currentTimeMillis();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", UUID.randomUUID().toString());
        event.put("type", type);
        event.put("recordedAt", timestamp);
        event.put(kind, name);
        event.put("clock", "REAL_TIME");
        event.put("day", Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay());
        if ("REALISTIC_SEASONS".equalsIgnoreCase(plugin.getIdeologyConfig().getString("history.clock", "REAL_TIME"))) {
            String world = plugin.getIdeologyConfig().getString("history.world", "world");
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

    private String ideologyEventType(String field) {
        return switch (field) {
            case "governance" -> "IDEOLOGY_GOVERNANCE_CHANGED";
            case "leader" -> "IDEOLOGY_LEADER_CHANGED";
            case "treasury" -> "IDEOLOGY_TREASURY_CHANGED";
            case "tithe" -> "IDEOLOGY_TITHE_CHANGED";
            case "memes" -> "IDEOLOGY_MEMES_CHANGED";
            case "doctrines" -> "IDEOLOGY_DOCTRINES_CHANGED";
            case "cardinals" -> "IDEOLOGY_CARDINALS_CHANGED";
            case "electionOpen" -> "IDEOLOGY_ELECTION_STATE_CHANGED";
            case "parentIdeology", "parentChurch" -> "IDEOLOGY_PARENTAGE_CHANGED";
            default -> "IDEOLOGY_PARENTAGE_CHANGED";
        };
    }

    private Map<String, AffiliationCounts> affiliationCounts(Collection<WDEreligion> ideologies) {
        Map<String, int[]> counts = new HashMap<>();
        for (WDEreligion ideology : ideologies) counts.put(normalize(ideology.getReligionName()), new int[3]);
        for (WDEresident resident : plugin.getResidentManager().getAllResidents()) {
            increment(counts, resident.getReligionName(), 0);
        }
        for (WDEtown town : plugin.getTownManager().getAllTowns()) increment(counts, town.getReligionName(), 1);
        for (WDEnation nation : plugin.getNationManager().getAllNations()) increment(counts, nation.getReligionName(), 2);
        Map<String, AffiliationCounts> result = new HashMap<>();
        counts.forEach((key, value) -> result.put(key, new AffiliationCounts(value[0], value[1], value[2])));
        return result;
    }

    private void increment(Map<String, int[]> counts, String ideologyName, int index) {
        if (ideologyName == null || ideologyName.isBlank() || "none".equalsIgnoreCase(ideologyName)) return;
        int[] values = counts.get(normalize(ideologyName));
        if (values != null) values[index]++;
    }

    private Map<String, Object> snapshot(WDEreligion ideology, Map<String, AffiliationCounts> counts) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("governance", ideology.getGovernanceMode().name());
        data.put("leader", ideology.getPontiffName());
        data.put("treasury", ideology.getTreasuryBalance().toPlainString());
        data.put("tithe", ideology.getTitheType().name() + ":" + ideology.getTitheAmount().toPlainString());
        data.put("memes", ideology.getMemes().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
        data.put("doctrines", new java.util.TreeMap<>(ideology.getDoctrineAttitudes()));
        data.put("cardinals", ideology.getCardinals().values().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList());
        data.put("excommunicatedPlayers", ideology.getExcommunicatedPlayers().size());
        data.put("excommunicatedTowns", ideology.getExcommunicatedTowns().size());
        data.put("excommunicatedNations", ideology.getExcommunicatedNations().size());
        data.put("electionOpen", ideology.isElectionOpen());
        data.put("parentIdeology", ideology.getParentIdeologyUuid() == null ? ""
                : ideologyNameByUuid(ideology.getParentIdeologyUuid().toString()));
        data.put("parentChurch", ideology.getParentChurchUuid() == null ? ""
                : churchNameByUuid(ideology.getParentChurchUuid().toString()));
        AffiliationCounts affiliations = counts.getOrDefault(normalize(ideology.getReligionName()),
                AffiliationCounts.ZERO);
        data.put("playerAffiliations", affiliations.players);
        data.put("townAffiliations", affiliations.towns);
        data.put("nationAffiliations", affiliations.nations);
        return data;
    }

    private Map<String, Object> churchSnapshot(WDEChurch church, int eventCursor) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("state", church.getState().name());
        data.put("head", church.getHeadName());
        data.put("memberCount", church.getMembers().size());
        data.put("treasury", church.getTreasuryBalance().toPlainString());
        data.put("sanctioned", church.getSanctionExpiresAt() > System.currentTimeMillis());
        data.put("sanctionExpiresAt", church.getSanctionExpiresAt());
        data.put("historyCursor", eventCursor);
        return data;
    }

    private Map<String, Object> renderableChurchEvent(WDEChurch church, Map<String, Object> raw,
            int memberCount, BigDecimal treasuryBefore, BigDecimal treasuryAfter) {
        String rawType = String.valueOf(raw.getOrDefault("type", ""));
        long at = raw.get("at") instanceof Number number ? number.longValue() : System.currentTimeMillis();
        Map<String, Object> parameters = raw.get("parameters") instanceof Map<?, ?> map
                ? stringMap(map) : Map.of();
        Map<String, Object> values = new LinkedHashMap<>();
        String type;
        switch (rawType) {
            case "MEMBER_JOINED" -> {
                type = "CHURCH_MEMBER_JOINED";
                values.put("memberCount", memberCount);
            }
            case "MEMBER_LEFT" -> {
                type = "CHURCH_MEMBER_LEFT";
                values.put("memberCount", memberCount);
            }
            case "HEAD_TRANSFERRED" -> {
                type = "CHURCH_HEAD_CHANGED";
                values.put("before", parameters.getOrDefault("previousHeadName", ""));
                values.put("after", parameters.getOrDefault("successorName", ""));
            }
            case "TREASURY_CREDIT", "TREASURY_DEBIT" -> {
                type = rawType.equals("TREASURY_CREDIT")
                        ? "CHURCH_TREASURY_CREDIT" : "CHURCH_TREASURY_DEBIT";
                values.put("amount", parameters.getOrDefault("amount", "0"));
                values.put("transaction", parameters.getOrDefault("transactionUuid", ""));
                values.put("before", treasuryBefore.toPlainString());
                values.put("after", treasuryAfter.toPlainString());
            }
            case "SANCTION_IMPOSED" -> {
                type = "CHURCH_SANCTION_IMPOSED";
                values.put("expiresAt", realDateTime(parameters.get("expiresAt")));
            }
            case "SANCTION_APPEAL" -> type = "CHURCH_SANCTION_APPEALED";
            case "SANCTION_LIFTED" -> type = "CHURCH_SANCTION_LIFTED";
            case "CHURCH_MERGED" -> {
                type = rawType;
                values.put("operation", parameters.getOrDefault("operationUuid", ""));
                values.put("amount", parameters.getOrDefault("settledAmount", "0"));
            }
            case "CHURCH_SECEDED" -> {
                type = rawType;
                values.put("operation", parameters.getOrDefault("operationUuid", ""));
                values.put("amount", parameters.getOrDefault("settledAmount", "0"));
            }
            case "CHURCH_DISSOLVED", "IDEOLOGY_DISSOLVED" -> {
                type = rawType;
                if (parameters.containsKey("preservedTreasury")) {
                    values.put("amount", parameters.get("preservedTreasury"));
                }
            }
            default -> {
                return null;
            }
        }
        return event(type, church.getChurchName(), "church", at, values);
    }

    private IdeologyChronicle activeIdeology(String ideologyUuid) {
        return ideologyBooks.values().stream()
                .filter(book -> !book.archived && book.ideologyUuid.equalsIgnoreCase(ideologyUuid))
                .findFirst().orElse(null);
    }

    private IdeologyChronicle activeIdeologyByName(String name) {
        return ideologyBooks.values().stream()
                .filter(book -> !book.archived && book.name.equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    private ChurchChronicle activeChurch(String churchUuid) {
        return churchBooks.values().stream()
                .filter(book -> !book.archived && book.churchUuid.equalsIgnoreCase(churchUuid))
                .findFirst().orElse(null);
    }

    private ChurchChronicle anyChurch(String churchUuid) {
        return churchBooks.values().stream()
                .filter(book -> book.churchUuid.equalsIgnoreCase(churchUuid))
                .findFirst().orElse(null);
    }

    private static WDEreligion findIdeology(Collection<WDEreligion> ideologies, String uuid) {
        return ideologies.stream()
                .filter(ideology -> ideology.getIdeologyUuid().toString().equalsIgnoreCase(uuid))
                .findFirst().orElse(null);
    }

    private static WDEChurch findChurch(Collection<WDEChurch> churches, String uuid) {
        return churches.stream()
                .filter(church -> church.getChurchUuid().toString().equalsIgnoreCase(uuid))
                .findFirst().orElse(null);
    }

    private String ideologyNameByUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) return "";
        for (WDEreligion ideology : plugin.getReligionManager().getAllReligions()) {
            if (ideology.getIdeologyUuid().toString().equalsIgnoreCase(uuid)) return ideology.getReligionName();
        }
        for (IdeologyChronicle book : ideologyBooks.values()) {
            if (book.ideologyUuid.equalsIgnoreCase(uuid)) return book.name;
        }
        return uuid;
    }

    private String churchNameByUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) return "";
        for (WDEChurch church : plugin.getIdeologyChurchManager().getAllChurches()) {
            if (church.getChurchUuid().toString().equalsIgnoreCase(uuid)) return church.getChurchName();
        }
        for (ChurchChronicle book : churchBooks.values()) {
            if (book.churchUuid.equalsIgnoreCase(uuid)) return book.name;
        }
        return uuid;
    }

    private static Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null) result.put(String.valueOf(key), value);
        });
        return result;
    }

    private String displayValue(String value) {
        return templates.getString("ideologyLabels." + value,
                templates.getString("churchLabels." + value,
                        templates.getString("labels." + value, value)));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String text(UUID value) {
        return value == null ? "" : value.toString();
    }

    private static String realDate(long time) {
        return REAL_DATE.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
    }

    private static String realDateTime(Object value) {
        if (!(value instanceof Number number) || number.longValue() <= 0L) return "";
        return REAL_DATE_TIME.format(Instant.ofEpochMilli(number.longValue()).atZone(ZoneId.systemDefault()));
    }

    private static String archiveDisplay(String name, long foundedAt, long openedAt, long endedAt) {
        return name + "(" + realDate(foundedAt > 0 ? foundedAt : openedAt) + "-" + realDate(endedAt) + ")";
    }

    private static int readInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static BigDecimal decimal(Object value) {
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private record AffiliationCounts(int players, int towns, int nations) {
        private static final AffiliationCounts ZERO = new AffiliationCounts(0, 0, 0);
    }
}
