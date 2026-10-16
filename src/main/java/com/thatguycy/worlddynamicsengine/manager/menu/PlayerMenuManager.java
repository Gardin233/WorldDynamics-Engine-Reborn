package com.thatguycy.worlddynamicsengine.manager.menu;

import com.thatguycy.worlddynamicsengine.command.background.BackgroundCommand;
import com.thatguycy.worlddynamicsengine.command.ideology.ReligionCommand;
import com.thatguycy.worlddynamicsengine.manager.background.BackgroundManager;
import com.thatguycy.worlddynamicsengine.manager.economy.EconomyBridge;
import com.thatguycy.worlddynamicsengine.manager.ideology.IdeologyTreasuryManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.ReligionManager;
import com.thatguycy.worlddynamicsengine.manager.organization.OrgManager;
import com.thatguycy.worlddynamicsengine.model.background.RoleBackgroundDefinition;
import com.thatguycy.worlddynamicsengine.model.background.RoleBackgroundStage;
import com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineAttitudeDefinition;
import com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineDefinition;
import com.thatguycy.worlddynamicsengine.model.IdeologyMemeDefinition;
import com.thatguycy.worlddynamicsengine.model.organization.SovereignSubjectState;
import com.thatguycy.worlddynamicsengine.model.war.WarReason;
import com.thatguycy.worlddynamicsengine.model.WDEnation;
import com.thatguycy.worlddynamicsengine.model.WDEorg;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.Material;
import org.bukkit.Sound;

/**
 * 构建玩家管理菜单并维护分页、输入和确认状态；菜单操作仍通过业务规则检查资格。
 */
public class PlayerMenuManager implements Listener {
    private final WorldDynamicsEngine plugin;
    private final BackgroundManager backgrounds;
    private final ReligionManager religions;
    private final BackgroundCommand backgroundCommand;
    private final ReligionCommand religionCommand;
    private final Map<UUID, Long> nameInputs = new ConcurrentHashMap<>();
    private final Map<UUID, ManagementInput> managementInputs = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> violationNotices = new HashMap<>();
    private FileConfiguration messages;
    private boolean resetting;

    public PlayerMenuManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        backgrounds = plugin.getBackgroundManager();
        religions = plugin.getReligionManager();
        backgroundCommand = new BackgroundCommand(plugin, backgrounds, plugin.getResidentManager());
        religionCommand = new ReligionCommand(plugin.getNationManager(), plugin.getTownManager(),
                plugin.getResidentManager(), religions, plugin.getEconomyBridge());
        reload();
    }

    public void reload() {
        closeMenus();
        File file = new File(plugin.getDataFolder(), "message.yml");
        if (!file.exists()) {
            plugin.saveResource("message.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
        try (var stream = plugin.getResource("message.yml")) {
            if (stream != null) {
                var defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
                messages.setDefaults(defaults);
                messages.options().copyDefaults(true);
                messages.save(file);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to load menu messages: " + ex.getMessage());
        }
    }

    public void closeMenus() {
        resetting = true;
        nameInputs.clear();
        managementInputs.clear();
        violationNotices.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                player.closeInventory();
            }
        }
        resetting = false;
    }

    private String text(String key, Object... args) {
        String value = messages.getString("ui." + key, key);
        return ChatColor.translateAlternateColorCodes('&', args.length == 0 ? value : String.format(value, args));
    }

    public void sendDoctrineViolation(Player player, String doctrineId) {
        String message = messages.getString("ui.doctrine_violation", "");
        if (message.isBlank()) return;
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0L, messages.getLong("ui.doctrine_violation_cooldown_seconds", 30L));
        Map<String, Long> notices = violationNotices.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
        Long last = notices.get(doctrineId);
        if (last != null && (now - last) / 1000L < cooldown) return;
        notices.put(doctrineId, now);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message
                .replace("%player_name%", player.getName())
                .replace("%doctrine_name%", religions.getDoctrineDisplayName(doctrineId))));
    }

    private Material ideologyIcon(String category, String id, Material fallback) {
        String value = switch (category) {
            case "doctrines" -> {
                var doctrine = religions.getDoctrine(id);
                yield doctrine == null ? "" : doctrine.getIcon();
            }
            case "memes" -> {
                var meme = religions.getMeme(id);
                yield meme == null ? "" : meme.getIcon();
            }
            default -> "";
        };
        if (value == null || value.isBlank()) {
            value = fallback.name();
        }
        Material material = Material.matchMaterial(value);
        return material == null || !material.isItem() || material.isAir() ? fallback : material;
    }

    private WDEresident resident(Player player) {
        return plugin.getResidentManager().getOrCreateResident(player.getUniqueId(), player.getName());
    }

    public void openHome(Player player) {
        Menu menu = menu(player, text("home"));
        button(menu, 20, Material.WRITABLE_BOOK, text("background"), List.of(text("background_hint")),
                () -> openBackground(player));
        button(menu, 24, Material.ENCHANTED_BOOK, text("ideology"), List.of(text("ideology_hint")),
                () -> openIdeology(player));
        button(menu, 40, Material.WRITTEN_BOOK, text("peace.title"), List.of(text("peace.hint")),
                () -> openPeaceTreaty(player, null));
        button(menu, 30, Material.BLUE_BANNER, text("management.nation"), List.of(), () -> openNationManagement(player, null));
        button(menu, 32, Material.GRASS_BLOCK, text("management.town"), List.of(), () -> openTownManagement(player, null));
        button(menu, 38, Material.GOLD_BLOCK, text("orgmenu.title"), List.of(), () -> openOrganizationBrowser(player, null, 0));
        button(menu, 36, Material.IRON_SWORD, text("war.title"), List.of(), () -> openWarMenu(player));
        var town = playerTown(player);
        var townData = town == null ? null : plugin.getTownManager().getTown(town.getName());
        boolean conscript = townData != null && townData.isConscript(player.getName());
        boolean mayor = town != null && town.getMayor() != null && town.getMayor().getUUID().equals(player.getUniqueId());
        if (town != null && town.getNationOrNull() != null && (conscript || mayor)) {
            rallyButton(menu, 42, player, true);
        }
        if (conscript) rallyButton(menu, 44, player, false);
        button(menu, 49, Material.BARRIER, text("close"), List.of(), player::closeInventory);
        player.openInventory(menu.inventory);
    }

    private void rallyButton(Menu menu, int slot, Player player, boolean national) {
        var config = national ? plugin.getNationConfig() : plugin.getTownConfig();
        if (!config.getBoolean("rally.enabled", true) || national && !plugin.armyEnabled) return;
        String scope = national ? "nation" : "town";
        String cost = config.getString("rally.cost", national ? "250.00" : "100.00");
        String currency = plugin.getDefaultEconomyCurrency();
        String title = text("rally." + scope);
        List<String> lore = List.of(text("rally.cost", cost, currency), text("rally.hint"));
        button(menu, slot, Material.ENDER_PEARL, title, lore, () ->
                confirm(player, title, lore, () -> openHome(player), () -> {
                    var latest = national ? plugin.getNationConfig() : plugin.getTownConfig();
                    if (!cost.equals(latest.getString("rally.cost", national ? "250.00" : "100.00"))
                            || !currency.equals(plugin.getDefaultEconomyCurrency())) {
                        player.sendMessage(text("stale"));
                        openHome(player);
                        return;
                    }
                    player.closeInventory();
                    player.performCommand("wde rally " + scope);
                }));
    }

    public void openWarMenu(Player player) {
        openWarMenu(player, 0);
    }

    private void openWarMenu(Player player, int page) {
        var home = playerTown(player);
        if (home == null) { player.sendMessage(text("management.no_town")); return; }
        String nation = home.getNationOrNull() == null ? "" : home.getNationOrNull().getName();
        var wars = plugin.getOrgManager().getWarFocusOptions(nation, home.getName());
        String currentFocus = plugin.getWarScoreboardManager().getFocusedWarKey(player);
        Menu menu = menu(player, text("war.title"));
        int start = paginate(menu, wars.size(), page, p -> openWarMenu(player, p));
        for (int i = start; i < Math.min(start + 45, wars.size()); i++) {
            var war = wars.get(i);
            List<String> lore = new ArrayList<>();
            lore.add(text("war.reason", plugin.getLocaleMessage(war.reason().messageKey())));
            lore.add(war.independenceWar() ? "&7独立战争" : war.townWar() ? "&7城镇战争" : "&7常规战争");
            lore.add(war.focusKey().equalsIgnoreCase(currentFocus) ? "&a当前专注战争" : "&e点击查看详情并切换专注");
            button(menu, i - start, war.townWar() ? Material.IRON_SWORD : war.independenceWar() ? Material.IRON_BARS : Material.MAP,
                    war.title(), lore, () -> openWarDetail(player, war.focusKey(), page));
        }
        button(menu, 47, Material.RED_BANNER, text("war.declare_nation"), List.of(), () ->
                managementChoices(player, text("war.declare_nation"), plugin.getNationManager().getAllNations().stream()
                        .map(WDEnation::getNationName).filter(n -> !n.equalsIgnoreCase(nation)).toList(), 0,
                        target -> chooseWarReason(player, null, "nation", target), () -> openWarMenu(player)));
        if (plugin.isRegularTownWarParticipationAllowed()) {
            button(menu, 48, Material.ORANGE_BANNER, text("war.declare_town"), List.of(), () ->
                    managementChoices(player, text("war.declare_town"), plugin.getTownManager().getAllTowns().stream()
                            .map(WDEtown::getTownName).filter(t -> !t.equalsIgnoreCase(home.getName())).toList(), 0,
                            target -> chooseWarReason(player, null, "town", target), () -> openWarMenu(player)));
        }
        button(menu, 51, Material.WRITTEN_BOOK, text("peace.title"), List.of(), () -> openPeaceTreaty(player, null));
        back(menu, () -> openHome(player));
        player.openInventory(menu.inventory);
    }

    private void openWarDetail(Player player, String focusKey, int returnPage) {
        var home = playerTown(player);
        if (home == null) {
            player.sendMessage(text("management.no_town"));
            return;
        }
        String nation = home.getNationOrNull() == null ? "" : home.getNationOrNull().getName();
        var details = plugin.getOrgManager().getWarFocusDetails(focusKey, nation, home.getName());
        if (details == null) {
            player.sendMessage(text("stale"));
            openWarMenu(player, returnPage);
            return;
        }

        Menu menu = menu(player, text("war.title"));
        List<String> info = new ArrayList<>();
        info.add(text("war.reason", plugin.getLocaleMessage(details.reason().messageKey())));
        info.add(details.defenderPerspective() ? "&7你的当前视角：防守方" : "&7你的当前视角：进攻方");
        info.add("&a我方总分：" + decimal(details.friendlyScore()));
        info.add("&c敌方总分：" + decimal(details.enemyScore()));
        info.add("&7我方占领：" + decimal(sum(details.friendlyOccupation())) + "，我方击杀：" + decimal(sum(details.friendlyKills())));
        info.add("&7敌方占领：" + decimal(sum(details.enemyOccupation())) + "，敌方击杀：" + decimal(sum(details.enemyKills())));
        if (details.independenceWar()) {
            info.add("&7已结算周期：" + details.elapsedCycles());
        }
        button(menu, 13, details.townWar() ? Material.IRON_SWORD : details.independenceWar() ? Material.IRON_BARS : Material.MAP,
                details.title(), info, null);

        boolean focused = focusKey.equalsIgnoreCase(plugin.getWarScoreboardManager().getFocusedWarKey(player));
        button(menu, 20, focused ? Material.BEACON : Material.COMPASS,
                focused ? "&a当前专注战争" : "&e设为专注战争",
                List.of("&7侧边计分板将显示这场战争的详细分数和分账。"),
                focused ? null : () -> {
                    if (plugin.getWarScoreboardManager().setFocusedWar(player, focusKey, true)) {
                        success(player);
                    } else {
                        player.sendMessage(text("stale"));
                    }
                    openWarDetail(player, focusKey, returnPage);
                });

        if (!details.townWar()) {
            button(menu, 24, Material.WRITTEN_BOOK, text("peace.title"),
                    List.of("&7以当前战争上下文进入和平条约界面。"),
                    () -> openPeaceTreaty(player, null));
        } else {
            button(menu, 24, Material.PAPER, "&e当前为城镇战争",
                    List.of("&7城镇战争支持专注展示和镇长投降，不接入国家和约菜单。"), null);
        }

        if (details.townWar()) {
            String selfTown = "town:" + home.getName();
            String other = details.friendlyParticipants().stream()
                    .filter(entry -> !entry.equalsIgnoreCase(selfTown))
                    .findFirst()
                    .or(() -> details.enemyParticipants().stream().findFirst())
                    .orElse("");
            if (other.startsWith("town:")) {
                String targetTown = other.substring(5);
                button(menu, 30, Material.RED_BANNER, text("war.surrender"),
                        List.of("&7结束这场城镇战争并进入停战期。"),
                        () -> managementSubmit(player, () -> home.getMayor() != null
                                        && home.getMayor().getUUID().equals(player.getUniqueId()),
                                text("war.surrender"), () -> openWarDetail(player, focusKey, returnPage),
                                "war", "surrender", "town", targetTown));
            }
        }

        back(menu, () -> openWarMenu(player, returnPage));
        player.openInventory(menu.inventory);
    }

    private void chooseWarReason(Player player, WDEorg organization, String scope, String target) {
        List<String> reasons = Arrays.stream(com.thatguycy.worlddynamicsengine.model.war.WarReason.values())
                .filter(r -> plugin.getOrgManager().validWarReason(r, target))
                .map(Enum::name).toList();
        Runnable refresh = organization == null ? () -> openWarMenu(player)
                : () -> openOrganization(player, organization.getOrganizationName());
        managementChoices(player, text("war.choose_reason"), reasons, 0, reason ->
                confirm(player, text("war.reason", managementValue(reason)), List.of(text("war.target", target)), refresh, () -> {
                    player.closeInventory();
                    player.performCommand(organization == null ? "wde war declare " + scope + " " + target + " " + reason
                            : "wde org declarewar " + organization.getOrganizationName() + " " + target + " " + reason);
                    refresh.run();
                }), refresh);
    }

    private com.palmergames.bukkit.towny.object.Town playerTown(Player player) {
        return com.palmergames.bukkit.towny.TownyAPI.getInstance().getTown(player);
    }

    private boolean memberOfNation(Player player, String name) {
        var town = playerTown(player);
        return town != null && town.getNationOrNull() != null && town.getNationOrNull().getName().equalsIgnoreCase(name);
    }

    private boolean townAuthority(Player player, String name, boolean direct) {
        var town = playerTown(player);
        WDEtown record = plugin.getTownManager().getTown(name);
        if (record == null || town == null) return false;
        boolean local = town.getName().equalsIgnoreCase(name)
                && (player.getName().equalsIgnoreCase(record.getTownLeader())
                    || town.getMayor() != null && player.getUniqueId().equals(town.getMayor().getUUID()));
        return local || !direct && record.getNationName().equalsIgnoreCase(kingNation(player));
    }

    private boolean nationRole(Player player, String name, String role) {
        WDEnation nation = plugin.getNationManager().getNation(name);
        if (nation == null || !memberOfNation(player, name)) return false;
        return switch (role) {
            case "king" -> name.equalsIgnoreCase(kingNation(player));
            case "government" -> player.getName().equalsIgnoreCase(nation.getGovernmentLeader());
            case "army" -> player.getName().equalsIgnoreCase(nation.getArmyCommander());
            case "authority" -> nationRole(player, name, "king") || nationRole(player, name, "government") || nationRole(player, name, "army");
            default -> false;
        };
    }

    private List<String> townResidents(String name) {
        var town = com.palmergames.bukkit.towny.TownyUniverse.getInstance().getTown(name);
        return town == null ? List.of() : town.getResidents().stream().map(r -> r.getName()).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private List<String> nationResidents(String name) {
        var nation = plugin.getNationManager().getTownyNation(name);
        return nation == null ? List.of() : nation.getTowns().stream().flatMap(t -> t.getResidents().stream())
                .map(r -> r.getName()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private void managementChoices(Player player, String title, List<String> values, int page,
                                   Consumer<String> select, Runnable backAction) {
        Menu menu = menu(player, title);
        int start = paginate(menu, values.size(), page, p -> managementChoices(player, title, values, p, select, backAction));
        for (int i = start; i < Math.min(start + 45, values.size()); i++) {
            String value = values.get(i);
            button(menu, i - start, Material.PAPER, managementValue(value), List.of(text("preview")), () -> select.accept(value));
        }
        back(menu, backAction);
        player.openInventory(menu.inventory);
    }

    private String managementValue(String value) {
        var reason = com.thatguycy.worlddynamicsengine.model.war.WarReason.parse(value);
        if (reason != null) return plugin.getLocaleMessage(reason.messageKey());
        return switch (value) {
            case "TRADE_ALLIANCE", "MILITARY_ALLIANCE", "SOVEREIGN_EMPIRE", "IDEOLOGICAL_ALLIANCE" ->
                    text("orgmenu.types." + value.toLowerCase(Locale.ROOT));
            case "DICTATORSHIP", "DEMOCRACY", "NOBLE_COUNCIL", "REPUBLIC", "FIXED", "WEALTH_PERCENTAGE",
                    "fixed", "wealth_percentage", "yes", "no", "none", "None" ->
                    text("management.values." + value.toLowerCase(Locale.ROOT));
            default -> value;
        };
    }

    private void managementSubmit(Player player, BooleanSupplier allowed, String title, Runnable refresh, String... args) {
        if (!allowed.getAsBoolean()) { player.sendMessage(text("management.denied")); refresh.run(); return; }
        confirm(player, title, List.of(text("management.confirm_hint")), refresh, () -> {
            if (!allowed.getAsBoolean()) {
                player.sendMessage(text("management.denied")); refresh.run(); return;
            }
            if ("town".equals(args[0])) {
                new com.thatguycy.worlddynamicsengine.command.governance.TownCommand(plugin.getTownManager())
                        .onCommand(player, plugin.getCommand("wde"), "wde", args);
            } else if ("nation".equals(args[0])) {
                new com.thatguycy.worlddynamicsengine.command.governance.NationCommand(plugin.getNationManager(), plugin.getResidentManager())
                        .onCommand(player, plugin.getCommand("wde"), "wde", args);
            } else if ("org".equals(args[0])) {
                new com.thatguycy.worlddynamicsengine.command.organization.OrgCommand(plugin.getNationManager(), plugin.getTownManager(),
                        plugin.getOrgManager(), plugin.getEconomyBridge()).onCommand(player, plugin.getCommand("wde"), "wde", args);
            } else if ("war".equals(args[0])) {
                new com.thatguycy.worlddynamicsengine.command.war.WarCommand(plugin)
                        .onCommand(player, plugin.getCommand("wde"), "wde", args);
            } else {
                religionCommand.onCommand(player, plugin.getCommand("wde"), "wde", args);
            }
            refresh.run();
        });
    }

    public void openTownManagement(Player player, String requested) {
        if (!plugin.govEnabled) { player.sendMessage(text("unavailable")); return; }
        var own = playerTown(player);
        String name = requested == null ? (own == null ? null : own.getName()) : requested;
        WDEtown town = name == null ? null : plugin.getTownManager().getTown(name);
        if (town == null) { player.sendMessage(text("management.no_town")); return; }
        Menu menu = menu(player, text("management.town_title", name));
        Runnable refresh = () -> openTownManagement(player, name);
        BooleanSupplier govern = () -> plugin.govEnabled && townAuthority(player, name, false);
        BooleanSupplier direct = () -> plugin.govEnabled && townAuthority(player, name, true);
        List<String> info = new ArrayList<>(List.of(text("management.government", managementValue(town.getGovernmentType())),
                text("management.leader", town.getTownLeader()), text("management.faith", town.getReligionName()),
                text("management.parent", town.getNationName()),
                text("management.tax", managementValue(town.getResidentTaxType()), town.getResidentTaxAmount()),
                text("management.conscripts", town.getConscripts().size(), plugin.getTownManager().getTownConscriptLimit(town))));
        if ("REPUBLIC".equalsIgnoreCase(town.getGovernmentType())) info.add(text("management.election_days", town.getElectionIntervalDays()));
        button(menu, 4, Material.WRITTEN_BOOK, name, info, null);
        button(menu, 8, Material.COMPASS, text("management.browse_towns"), List.of(), () -> managementChoices(player,
                text("management.browse_towns"), plugin.getTownManager().getAllTowns().stream().map(WDEtown::getTownName).sorted().toList(),
                0, value -> openTownManagement(player, value), refresh));
        if (govern.getAsBoolean()) {
            button(menu, 20, Material.COMMAND_BLOCK, text("management.setgov"), List.of(), () -> managementChoices(player,
                    text("management.setgov"), List.of("DICTATORSHIP", "REPUBLIC"), 0,
                    value -> managementSubmit(player, govern, managementValue(value), refresh, "town", name, "setgovtype", value), refresh));
            if ("DICTATORSHIP".equalsIgnoreCase(town.getGovernmentType())) {
                button(menu, 21, Material.PLAYER_HEAD, text("management.setleader"), List.of(), () -> managementChoices(player,
                        text("management.setleader"), townResidents(name), 0,
                        value -> managementSubmit(player, govern, value, refresh, "town", name, "setleader", value), refresh));
            } else if ("REPUBLIC".equalsIgnoreCase(town.getGovernmentType())) {
                button(menu, 21, Material.CLOCK, text("management.setdays"), List.of(), () -> managementNumber(player, refresh, value -> {
                    if (!value.matches("[1-9][0-9]{0,8}")) return false;
                    managementSubmit(player, govern, text("management.election_days", value), refresh, "town", name, "setelectiondays", value);
                    return true;
                }));
            }
            if (plugin.religionEnabled) button(menu, 24, Material.ENCHANTED_BOOK, text("management.setfaith"), List.of(text("management.cost_hint")),
                    () -> managementChoices(player, text("management.setfaith"), faithChoices(), 0,
                            value -> managementSubmit(player, () -> govern.getAsBoolean() && plugin.religionEnabled, managementValue(value), refresh,
                                    "ideology", "settown", name, value), refresh));
        }
        if (direct.getAsBoolean()) button(menu, 30, Material.IRON_SWORD, text("management.conscript_manage"), List.of(), () ->
                managementChoices(player, text("management.conscript_manage"), townResidents(name), 0,
                        value -> openConscriptManagement(player, name, value), refresh));
        if ("REPUBLIC".equalsIgnoreCase(town.getGovernmentType()) && own != null && own.getName().equalsIgnoreCase(name)
                && (town.hasActiveElection() || System.currentTimeMillis() >= town.getLastElectionAt()
                        + Math.max(1L, town.getElectionIntervalDays()) * 86400000L)) {
            button(menu, 22, Material.PLAYER_HEAD, text("management.nominate"), List.of(), () ->
                    managementChoices(player, text("management.nominate"), townResidents(name), 0,
                            value -> managementSubmit(player, () -> plugin.govEnabled && playerTown(player) != null
                                    && name.equalsIgnoreCase(playerTown(player).getName()), value, refresh,
                                    "town", name, "nominateleader", value), refresh));
        }
        if ("REPUBLIC".equalsIgnoreCase(town.getGovernmentType()) && town.hasActiveElection()
                && own != null && own.getName().equalsIgnoreCase(name)) {
            button(menu, 32, Material.PAPER, text("management.vote"), List.of(), () -> managementChoices(player, text("management.vote"),
                    town.getTownLeaderCandidates(), 0, value -> managementSubmit(player, () -> plugin.govEnabled
                            && playerTown(player) != null && name.equalsIgnoreCase(playerTown(player).getName()),
                            value, refresh, "town", name, "voteleader", value), refresh));
        }
        back(menu, () -> openHome(player));
        player.openInventory(menu.inventory);
    }

    private List<String> faithChoices() {
        List<String> choices = new ArrayList<>(List.of("none"));
        choices.addAll(religions.getAllReligionNames());
        return choices;
    }

    private void openConscriptManagement(Player player, String townName, String residentName) {
        WDEtown town = plugin.getTownManager().getTown(townName);
        if (town == null) { player.sendMessage(text("stale")); return; }
        Menu menu = menu(player, text("management.conscript_manage"));
        Runnable refresh = () -> openTownManagement(player, townName);
        BooleanSupplier allowed = () -> plugin.govEnabled && townAuthority(player, townName, true);
        boolean enlisted = town.isConscript(residentName);
        button(menu, 22, Material.PLAYER_HEAD, residentName, List.of(text(enlisted ? "selected" : "pending")), null);
        button(menu, 30, Material.IRON_SWORD, text(enlisted ? "management.remove_conscript" : "management.add_conscript"), List.of(),
                () -> managementSubmit(player, allowed, residentName, refresh, "town", townName, "setconscript", residentName, enlisted ? "disabled" : "enabled"));
        if (enlisted) button(menu, 32, Material.GOLD_INGOT, text("management.taxfree"), List.of(),
                () -> managementSubmit(player, allowed, residentName, refresh, "town", townName, "setconscriptprivilege",
                        residentName, "taxfree", town.isConscriptTaxExempt(residentName) ? "disabled" : "enabled"));
        back(menu, refresh);
        player.openInventory(menu.inventory);
    }

    public void openNationManagement(Player player, String requested) {
        var own = playerTown(player);
        String name = requested == null ? (own == null || own.getNationOrNull() == null ? null : own.getNationOrNull().getName()) : requested;
        WDEnation nation = name == null ? null : plugin.getNationManager().getNation(name);
        if (!plugin.govEnabled) { player.sendMessage(text("unavailable")); return; }
        if (nation == null) { player.sendMessage(text("management.no_nation")); return; }
        Menu menu = menu(player, text("management.nation_title", name));
        Runnable refresh = () -> openNationManagement(player, name);
        BooleanSupplier king = () -> plugin.govEnabled && nationRole(player, name, "king");
        BooleanSupplier government = () -> plugin.govEnabled && nationRole(player, name, "government");
        BooleanSupplier army = () -> plugin.armyEnabled && nationRole(player, name, "army");
        BooleanSupplier authority = () -> plugin.govEnabled && nationRole(player, name, "authority");
        button(menu, 4, Material.WRITTEN_BOOK, name, List.of(text("management.government", managementValue(nation.getGovernmentType())),
                text("management.leader", nation.getGovernmentLeader()), text("management.army_leader", nation.getArmyCommander()),
                text("management.faith", nation.getReligionName()), text("management.member_count", nation.getGovernmentMembers().size(),
                        nation.getArmyMembers().size(), nation.getTownNames().size())), null);
        button(menu, 8, Material.COMPASS, text("management.browse_nations"), List.of(), () -> managementChoices(player,
                text("management.browse_nations"), plugin.getNationManager().getAllNations().stream().map(WDEnation::getNationName).sorted().toList(),
                0, value -> openNationManagement(player, value), refresh));
        button(menu, 16, Material.GRASS_BLOCK, text("management.towns"), List.of(), () -> managementChoices(player,
                text("management.towns"), nation.getTownNames(), 0, value -> openTownManagement(player, value), refresh));
        if (king.getAsBoolean()) {
            button(menu, 19, Material.COMMAND_BLOCK, text("management.setgov"), List.of(), () -> managementChoices(player,
                    text("management.setgov"), List.of("DICTATORSHIP", "DEMOCRACY", "NOBLE_COUNCIL"), 0,
                    value -> managementSubmit(player, king, managementValue(value), refresh, "nation", name, "setgovtype", value), refresh));
            if (!"DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType())) managementPlayerAction(menu, 20, player, name,
                    "setgovleader", "management.setleader", nationResidents(name), king, refresh);
            if (plugin.armyEnabled) managementPlayerAction(menu, 21, player, name, "appointarmycommander", "management.appoint_commander",
                    nationResidents(name), () -> king.getAsBoolean() && plugin.armyEnabled, refresh);
        }
        if (government.getAsBoolean()) {
            managementPlayerAction(menu, 22, player, name, "addgovmember", "management.add_government", nationResidents(name), government, refresh);
            managementPlayerAction(menu, 23, player, name, "kickgovmember", "management.remove_government", nation.getGovernmentMembers(), government, refresh);
            if ("DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType())) button(menu, 28, Material.CLOCK,
                    text("management.start_election"), List.of(), () -> managementSubmit(player, government,
                            text("management.start_election"), refresh, "nation", name, "startelection"));
        }
        if (army.getAsBoolean()) {
            managementPlayerAction(menu, 29, player, name, "enlistarmymember", "management.enlist", nationResidents(name), army, refresh);
            managementPlayerAction(menu, 30, player, name, "dischargearmymember", "management.discharge", nation.getArmyMembers(), army, refresh);
            managementPlayerAction(menu, 31, player, name, "tpconscript", "management.tpconscript",
                    nation.getTownNames().stream().map(plugin.getTownManager()::getTown).filter(Objects::nonNull)
                            .flatMap(t -> t.getConscripts().keySet().stream()).sorted().toList(), army, refresh);
            button(menu, 32, Material.ENDER_PEARL, text("management.tpchair"), List.of(), () ->
                    managementSubmit(player, army, text("management.tpchair"), refresh, "nation", name, "tpchaircommander"));
        }
        if (authority.getAsBoolean()) {
            managementPlayerAction(menu, 37, player, name, "adddiplomat", "management.add_diplomat", nationResidents(name), authority, refresh);
            managementPlayerAction(menu, 38, player, name, "removediplomat", "management.remove_diplomat", nationResidents(name), authority, refresh);
            button(menu, 39, Material.GOLD_INGOT, text("management.settax"), List.of(), () -> managementChoices(player,
                    text("management.towns"), nation.getTownNames(), 0, town -> managementChoices(player,
                            text("management.settax"), List.of("fixed", "wealth_percentage"), 0, type -> managementNumber(player, refresh, amount -> {
                                try { if (new java.math.BigDecimal(amount).signum() < 0 || !Double.isFinite(Double.parseDouble(amount))) return false; }
                                catch (NumberFormatException ex) { return false; }
                                managementSubmit(player, authority, text("management.tax", managementValue(type), amount), refresh,
                                        "nation", name, "settowntax", town, type, amount);
                                return true;
                            }), refresh), refresh));
        }
        if (plugin.religionEnabled && (king.getAsBoolean() || government.getAsBoolean())) {
            button(menu, 24, Material.ENCHANTED_BOOK, text("management.setfaith"), List.of(text("management.cost_hint")), () ->
                    managementChoices(player, text("management.setfaith"), faithChoices(), 0, value ->
                            managementSubmit(player, () -> plugin.religionEnabled && (king.getAsBoolean() || government.getAsBoolean()),
                                    managementValue(value), refresh, "ideology", "setnation", name, value), refresh));
        }
        if (!"DICTATORSHIP".equalsIgnoreCase(nation.getGovernmentType()) && memberOfNation(player, name)) {
            if (nation.hasActiveElection()) managementPlayerAction(menu, 41, player, name, "voteleader", "management.vote",
                    "DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType()) && !nation.getGovernmentLeaderCandidates().isEmpty()
                            ? nation.getGovernmentLeaderCandidates() : List.of("yes", "no"),
                    () -> plugin.govEnabled && memberOfNation(player, name), refresh);
            if (plugin.armyEnabled && nation.hasActiveArmyCommanderVote()) managementPlayerAction(menu, 42, player, name,
                    "votearmycommander", "management.vote_army",
                    "DEMOCRACY".equalsIgnoreCase(nation.getGovernmentType()) && !nation.getArmyCommanderCandidates().isEmpty()
                            ? nation.getArmyCommanderCandidates() : List.of("yes", "no"),
                    () -> plugin.armyEnabled && memberOfNation(player, name), refresh);
        }
        back(menu, () -> openHome(player));
        player.openInventory(menu.inventory);
    }

    private void managementPlayerAction(Menu menu, int slot, Player player, String nation, String action, String title,
                                        List<String> choices, BooleanSupplier allowed, Runnable refresh) {
        button(menu, slot, Material.PLAYER_HEAD, text(title), List.of(), () -> managementChoices(player, text(title),
                List.copyOf(choices), 0, value -> managementSubmit(player, allowed, value, refresh, "nation", nation, action, value), refresh));
    }

    /**
     * 保存一次菜单聊天输入的有效期、取消回调、提交校验与当前状态校验。
     */
    private record ManagementInput(long deadline, Runnable cancel, java.util.function.Predicate<String> submit,
                                   String prompt, String invalid) {}

    private void managementNumber(Player player, Runnable cancel, java.util.function.Predicate<String> submit) {
        managementInput(player, cancel, submit, "management.input_number", "management.invalid_number");
    }

    private void managementInput(Player player, Runnable cancel, java.util.function.Predicate<String> submit, String prompt, String invalid) {
        nameInputs.remove(player.getUniqueId());
        ManagementInput input = new ManagementInput(System.currentTimeMillis() + 60_000L, cancel, submit, prompt, invalid);
        managementInputs.put(player.getUniqueId(), input);
        player.closeInventory();
        player.sendMessage(text(prompt));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (managementInputs.remove(player.getUniqueId(), input) && player.isOnline()) {
                player.sendMessage(text("input_expired")); cancel.run();
            }
        }, 1200L);
    }

    private String orgType(WDEorg org) {
        return text("orgmenu.types." + org.getOrganizationType().toLowerCase(Locale.ROOT));
    }

    private String orgActor(Player player) {
        var town = playerTown(player);
        String nation = town == null || town.getNationOrNull() == null ? null : town.getNationOrNull().getName();
        return nation != null && nationRole(player, nation, "authority") ? nation : null;
    }

    private boolean orgPermission(Player player, WDEorg org, String role) {
        if (!plugin.orgEnabled || plugin.getOrgManager().getOrganization(org.getOrganizationName()) != org) return false;
        String actor = orgActor(player);
        return switch (role) {
            case "member" -> actor != null && org.hasMemberNation(actor);
            case "chairman" -> actor != null && actor.equalsIgnoreCase(org.getChairmanNationName());
            case "leader" -> player.getUniqueId().equals(org.getLeaderUUID()) || orgPermission(player, org, "chairman");
            case "applicant" -> actor != null && !org.hasMemberNation(actor);
            case "viewer" -> true;
            default -> false;
        };
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private double sum(Map<String, Double> values) {
        double total = 0D;
        for (Double value : values.values()) {
            if (value != null && Double.isFinite(value)) {
                total += value;
            }
        }
        return total;
    }

    public void openOrganizationBrowser(Player player, String type, int page) {
        if (!plugin.orgEnabled) { player.sendMessage(text("unavailable")); return; }
        List<WDEorg> orgs = plugin.getOrgManager().getAllOrganizations().stream()
                .filter(org -> type == null || type.equalsIgnoreCase(org.getOrganizationType())).toList();
        Menu menu = menu(player, text("orgmenu.title"));
        int start = paginate(menu, orgs.size(), page, p -> openOrganizationBrowser(player, type, p));
        for (int i = start; i < Math.min(start + 45, orgs.size()); i++) {
            WDEorg org = orgs.get(i);
            button(menu, i - start, org.isSovereignEmpire() ? Material.NETHERITE_BLOCK : Material.GOLD_BLOCK,
                    org.getOrganizationName(), List.of(orgType(org), text("orgmenu.chairman", org.getChairmanNationName()),
                            text("orgmenu.members_count", org.getMemberNationNames().size())), () -> openOrganization(player, org.getOrganizationName()));
        }
        button(menu, 47, Material.HOPPER, text("orgmenu.filter"), List.of(), () -> managementChoices(player,
                text("orgmenu.filter"), List.of("TRADE_ALLIANCE", "MILITARY_ALLIANCE", "SOVEREIGN_EMPIRE", "IDEOLOGICAL_ALLIANCE"),
                0, value -> openOrganizationBrowser(player, value, 0), () -> openOrganizationBrowser(player, null, 0)));
        if (orgActor(player) != null) button(menu, 48, Material.ANVIL, text("orgmenu.create"),
                List.of(text("orgmenu.creation_cost", formatOrgCharge(plugin.getOrgFormationCostCharge()))), () ->
                managementChoices(player, text("orgmenu.create"), List.of("TRADE_ALLIANCE", "MILITARY_ALLIANCE", "SOVEREIGN_EMPIRE", "IDEOLOGICAL_ALLIANCE"),
                        0, value -> managementInput(player, () -> openOrganizationBrowser(player, null, 0), name -> {
                            if (!name.matches("[\\p{L}\\p{N}_-]{2,24}")) return false;
                            managementSubmit(player, () -> plugin.orgEnabled && orgActor(player) != null,
                                    text("orgmenu.create_summary", name, text("orgmenu.types." + value.toLowerCase(Locale.ROOT)),
                                            formatOrgCharge(plugin.getOrgFormationCostCharge())),
                                    () -> openOrganizationBrowser(player, null, 0), "org", "create", name, value);
                            return true;
                        }, "orgmenu.input_name", "orgmenu.invalid_name"), () -> openOrganizationBrowser(player, null, 0)));
        back(menu, () -> openHome(player));
        player.openInventory(menu.inventory);
    }

    public void openOrganization(Player player, String name) {
        WDEorg org = plugin.getOrgManager().getOrganization(name);
        if (!plugin.orgEnabled || org == null) { player.sendMessage(text("orgmenu.missing")); return; }
        OrgManager manager = plugin.getOrgManager();
        Menu menu = menu(player, text("orgmenu.detail", orgType(org), name));
        Runnable refresh = () -> openOrganization(player, name);
        List<String> info = new ArrayList<>(List.of(text("orgmenu.chairman", org.getChairmanNationName()),
                text("orgmenu.founder", org.getNationName()), text("orgmenu.members_count", org.getMemberNationNames().size()),
                text("orgmenu.balance", decimal(org.getBalance()), plugin.getDefaultEconomyCurrency()),
                text("orgmenu.maintenance", formatOrgCharge(plugin.getOrgMaintenanceCostCharge()),
                        plugin.getOrganizationMaintenanceIntervalHours())));
        button(menu, 4, Material.WRITTEN_BOOK, name, info, null);
        button(menu, 19, Material.PLAYER_HEAD, text(org.isSovereignEmpire() ? "orgmenu.subjects" : "orgmenu.members"), List.of(), () ->
                openOrganizationMembers(player, org, 0));
        button(menu, 21, Material.PAPER, text("orgmenu.applications"), List.of(text("orgmenu.applications_hint")), () ->
                openOrganizationApplications(player, org, 0));
        button(menu, 23, Material.GOLD_INGOT, text("orgmenu.deposit"), List.of(text("orgmenu.deposit_hint")), () ->
                organizationMoney(player, org, false));
        if (orgPermission(player, org, "leader")) {
            button(menu, 24, Material.GOLD_NUGGET, text("orgmenu.withdraw"), List.of(text("orgmenu.withdraw_hint")), () ->
                    organizationMoney(player, org, true));
            button(menu, 51, Material.TNT, text("orgmenu.dissolve"), List.of(text("orgmenu.dissolve_warning")), () ->
                    managementSubmit(player, () -> orgPermission(player, org, "leader"), text("orgmenu.dissolve_warning"),
                            () -> openOrganizationBrowser(player, null, 0), "org", "dissolve", name));
        }
        if (orgPermission(player, org, "applicant")) button(menu, 47, Material.LIME_DYE, text("orgmenu.apply"), List.of(), () ->
                managementSubmit(player, () -> orgPermission(player, org, "applicant"), text("orgmenu.apply"), refresh, "org", "applynation", name));
        if (orgPermission(player, org, "member") && (!org.isSovereignEmpire() || !orgPermission(player, org, "chairman"))) {
            button(menu, 48, Material.RED_DYE, text(org.isSovereignEmpire() ? "orgmenu.independence" : "orgmenu.leave"),
                    List.of(text(org.isSovereignEmpire() ? "orgmenu.independence_warning" : "orgmenu.leave_warning")), () ->
                            managementSubmit(player, () -> orgPermission(player, org, "member"),
                                    text(org.isSovereignEmpire() ? "orgmenu.independence_warning" : "orgmenu.leave_warning"), refresh, "org", "leave", name));
        }
        if (manager.isTradeAlliance(org)) {
            button(menu, 30, Material.BARRIER, text("orgmenu.sanctions"), List.of(text("orgmenu.sanction_rules")), () ->
                    managementChoices(player, text("orgmenu.sanctions"), new ArrayList<>(manager.getTradeSanctionTargets(org)), 0,
                            target -> openSanctionTarget(player, org, target), refresh));
            if (orgPermission(player, org, "member")) button(menu, 32, Material.RED_BANNER, text("orgmenu.new_sanction"), List.of(), () ->
                    managementChoices(player, text("orgmenu.new_sanction"), plugin.getNationManager().getAllNations().stream()
                            .map(WDEnation::getNationName).toList(), 0, target -> openSanctionTarget(player, org, target), refresh));
        } else if (manager.isMilitaryAlliance(org)) {
            button(menu, 30, Material.IRON_SWORD, text("orgmenu.war_proposals"), List.of(), () -> openOrganizationWarVotes(player, org, 0));
            button(menu, 32, Material.MAP, text("orgmenu.wars"), List.of(), () -> openOrganizationWars(player, org, 0));
            if (orgPermission(player, org, "member")) button(menu, 28, Material.RED_BANNER, text("orgmenu.declare"), List.of(text("orgmenu.declare_hint")), () ->
                    managementChoices(player, text("orgmenu.declare"), plugin.getNationManager().getAllNations().stream()
                            .map(WDEnation::getNationName).filter(n -> !org.hasMemberNation(n)).toList(), 0,
                            target -> chooseWarReason(player, org, "nation", target), refresh));
            organizationPeaceButtons(menu, player, org);
        } else if (manager.isIdeologicalAlliance(org)) {
            String ideology = manager.getIdeologicalAllianceIdeology(org);
            button(menu, 30, Material.ENCHANTED_BOOK, text("orgmenu.ideology", ideology),
                    List.of(text("orgmenu.faith_rules"),
                            text("orgmenu.faith_multipliers", plugin.getOrganizationConfig().getDouble("ideologicalAlliance.peaceTreaty.sameReligionClauseCostMultiplier", 1.5),
                                    plugin.getOrganizationConfig().getDouble("ideologicalAlliance.peaceTreaty.hereticClauseCostMultiplier", 0.75))),
                    () -> openReligionDetail(player, ideology));
            String actor = orgActor(player);
            double discount = actor != null && org.hasMemberNation(actor) ? manager.getIdeologicalAllianceConversionDiscountRate(actor, plugin.getNationManager()) : 0;
            button(menu, 32, Material.EMERALD, text("orgmenu.discount", decimal(discount * 100)),
                    List.of(text("orgmenu.discount_hint")), null);
        } else if (org.isSovereignEmpire()) {
            button(menu, 28, Material.NETHERITE_BLOCK, text("orgmenu.capability"),
                    List.of(text("orgmenu.capability_values", decimal(manager.getChairmanCapability(org, plugin.getNationManager())),
                            decimal(manager.getSubjectCapabilityTotal(org, plugin.getNationManager(), false)),
                            decimal(manager.getSubjectCapabilityTotal(org, plugin.getNationManager(), true))), text("orgmenu.capability_hint")), null);
            button(menu, 30, Material.GOLD_BLOCK, text("orgmenu.tribute"), List.of(
                    text("orgmenu.regular_tribute", formatOrgCharge(manager.getSovereignTributeCharge()),
                            plugin.getOrganizationConfig().getLong("sovereignEmpire.tribute.intervalHours", 24)),
                    text("orgmenu.tribute_enabled", enabledText(plugin.getOrganizationConfig().getBoolean("sovereignEmpire.tribute.enabled", true))),
                    text("orgmenu.garrison_hint")), null);
            button(menu, 32, Material.IRON_BARS, text("orgmenu.independence_wars"), List.of(), () -> openOrganizationWars(player, org, 0));
            organizationPeaceButtons(menu, player, org);
            String actor = orgActor(player);
            if (actor != null && manager.isSovereignSubjectNation(org, actor)) button(menu, 37, Material.PLAYER_HEAD,
                    text("orgmenu.my_subject"), List.of(), () -> openSovereignSubject(player, name, actor));
        }
        back(menu, () -> openOrganizationBrowser(player, null, 0));
        player.openInventory(menu.inventory);
    }

    private String enabledText(boolean enabled) {
        return text(enabled ? "orgmenu.enabled" : "orgmenu.disabled");
    }

    private String formatOrgCharge(EconomyBridge.Charge charge) {
        return plugin.getEconomyBridge() == null ? charge.amount().toPlainString() + " " + charge.currency()
                : plugin.getEconomyBridge().format(charge);
    }

    private void organizationPeaceButtons(Menu menu, Player player, WDEorg org) {
        if (kingNation(player) == null) return;
        button(menu, 39, Material.WRITABLE_BOOK, text("peace.title"), List.of(), () -> openPeaceTreaty(player, org.getOrganizationName()));
        button(menu, 41, Material.CHEST, text("peace.inbox"), List.of(), () -> openPeaceInbox(player, org.getOrganizationName()));
    }

    private void organizationMoney(Player player, WDEorg org, boolean withdraw) {
        managementNumber(player, () -> openOrganization(player, org.getOrganizationName()), amount -> {
            try { if (new java.math.BigDecimal(amount).signum() <= 0 || !Double.isFinite(Double.parseDouble(amount))) return false; }
            catch (NumberFormatException ex) { return false; }
            managementSubmit(player, () -> orgPermission(player, org, withdraw ? "leader" : "viewer"),
                    text(withdraw ? "orgmenu.withdraw_summary" : "orgmenu.deposit_summary", amount, plugin.getDefaultEconomyCurrency()),
                    () -> openOrganization(player, org.getOrganizationName()), "org", withdraw ? "withdraw" : "deposit", org.getOrganizationName(), amount);
            return true;
        });
    }

    private void openOrganizationMembers(Player player, WDEorg org, int page) {
        if (!orgPermission(player, org, "viewer")) { player.sendMessage(text("stale")); return; }
        List<String> members = new ArrayList<>(org.getMemberNationNames());
        Menu menu = menu(player, text(org.isSovereignEmpire() ? "orgmenu.subjects" : "orgmenu.members"));
        int start = paginate(menu, members.size(), page, p -> openOrganizationMembers(player, org, p));
        for (int i = start; i < Math.min(start + 45, members.size()); i++) {
            String member = members.get(i);
            List<String> lore = org.isSovereignEmpire() && plugin.getOrgManager().isSovereignSubjectNation(org, member)
                    ? subjectSummary(org, member)
                    : List.of(text("orgmenu.weight", decimal(plugin.getOrgManager().getNationWeight(org, member, plugin.getNationManager()))));
            button(menu, i - start, Material.PLAYER_HEAD, member, lore, () -> {
                if (org.isSovereignEmpire() && plugin.getOrgManager().isSovereignSubjectNation(org, member)) openSovereignSubject(player, org.getOrganizationName(), member);
                else openNationManagement(player, member);
            });
        }
        back(menu, () -> openOrganization(player, org.getOrganizationName()));
        player.openInventory(menu.inventory);
    }

    private void openOrganizationApplications(Player player, WDEorg org, int page) {
        if (!orgPermission(player, org, "viewer")) { player.sendMessage(text("stale")); return; }
        Menu menu = menu(player, text("orgmenu.applications"));
        List<String> applicants = new ArrayList<>(org.getPendingNationApplications());
        int start = paginate(menu, applicants.size(), page, p -> openOrganizationApplications(player, org, p));
        for (int i = start; i < Math.min(start + 45, applicants.size()); i++) {
            String applicant = applicants.get(i);
            Map<String, Boolean> votes = org.getPendingNationVotes().getOrDefault(applicant, Map.of());
            List<String> lore = new ArrayList<>(List.of(text(org.isSovereignEmpire() ? "orgmenu.decree_hint" : "orgmenu.vote_hint")));
            votes.forEach((nation, support) -> lore.add(text("orgmenu.vote_entry", nation, text(support ? "orgmenu.support" : "orgmenu.oppose"))));
            button(menu, i - start, Material.PAPER, applicant, lore, () -> {
                String role = org.isSovereignEmpire() ? "chairman" : "member";
                if (!orgPermission(player, org, role)) { player.sendMessage(text("management.denied")); return; }
                managementChoices(player, text("orgmenu.review"), List.of("yes", "no"), 0, decision ->
                        managementSubmit(player, () -> orgPermission(player, org, role) && org.hasPendingApplication(applicant),
                                text("orgmenu.review_summary", applicant, managementValue(decision)), () -> openOrganizationApplications(player, org, page),
                                "org", org.isSovereignEmpire() ? "reviewjoin" : "votejoin", org.getOrganizationName(), applicant,
                                org.isSovereignEmpire() ? ("yes".equals(decision) ? "accept" : "reject") : decision),
                        () -> openOrganizationApplications(player, org, page));
            });
        }
        back(menu, () -> openOrganization(player, org.getOrganizationName()));
        player.openInventory(menu.inventory);
    }

    private void openSanctionTarget(Player player, WDEorg org, String target) {
        boolean sanctioned = plugin.getOrgManager().getTradeSanctionTargets(org).stream().anyMatch(target::equalsIgnoreCase);
        Runnable refresh = () -> openOrganization(player, org.getOrganizationName());
        if (!orgPermission(player, org, "member")) { player.sendMessage(text("orgmenu.sanction_rules")); return; }
        managementSubmit(player, () -> orgPermission(player, org, "member") && plugin.getOrgManager().isTradeAlliance(org)
                        && sanctioned == plugin.getOrgManager().getTradeSanctionTargets(org).stream().anyMatch(target::equalsIgnoreCase),
                text(sanctioned ? "orgmenu.remove_sanction" : "orgmenu.set_sanction", target), refresh,
                "org", "sanction", org.getOrganizationName(), target, sanctioned ? "disabled" : "enabled");
    }

    private void openOrganizationWarVotes(Player player, WDEorg org, int page) {
        if (!orgPermission(player, org, "viewer")) { player.sendMessage(text("stale")); return; }
        Menu menu = menu(player, text("orgmenu.war_proposals"));
        var proposals = plugin.getOrgManager().getActiveMilitaryWarProposals(org);
        int start = paginate(menu, proposals.size(), page, p -> openOrganizationWarVotes(player, org, p));
        for (int i = start; i < Math.min(start + 45, proposals.size()); i++) {
            var proposal = proposals.get(i);
            List<String> lore = new ArrayList<>(List.of(text("orgmenu.proposer", proposal.getProposerNationName()),
                    text("war.reason", plugin.getLocaleMessage(proposal.getReason().messageKey()))));
            proposal.getVotes().forEach((nation, support) -> lore.add(text("orgmenu.vote_entry", nation, text(support ? "orgmenu.support" : "orgmenu.oppose"))));
            button(menu, i - start, Material.IRON_SWORD, proposal.getTargetNationName(), lore, () ->
                    managementChoices(player, text("orgmenu.war_proposals"), List.of("yes", "no"), 0, decision ->
                            managementSubmit(player, () -> orgPermission(player, org, "member")
                                            && plugin.getOrgManager().getActiveMilitaryWarProposals(org).stream().anyMatch(current ->
                                                    current.getTargetNationName().equals(proposal.getTargetNationName())
                                                            && current.getCreatedAt() == proposal.getCreatedAt()
                                                            && current.getProposerNationName().equals(proposal.getProposerNationName())),
                                    text("orgmenu.war_vote_summary", proposal.getTargetNationName(), managementValue(decision)),
                                    () -> openOrganizationWarVotes(player, org, page), "org", "votewar", org.getOrganizationName(), proposal.getTargetNationName(), decision),
                            () -> openOrganizationWarVotes(player, org, page)));
        }
        back(menu, () -> openOrganization(player, org.getOrganizationName()));
        player.openInventory(menu.inventory);
    }

    private void openOrganizationWars(Player player, WDEorg org, int page) {
        if (!orgPermission(player, org, "viewer")) { player.sendMessage(text("stale")); return; }
        Menu menu = menu(player, text(org.isSovereignEmpire() ? "orgmenu.independence_wars" : "orgmenu.wars"));
        if (org.isSovereignEmpire()) {
            var wars = plugin.getOrgManager().getActiveIndependenceWars(org);
            int start = paginate(menu, wars.size(), page, p -> openOrganizationWars(player, org, p));
            for (int i = start; i < Math.min(start + 45, wars.size()); i++) {
                var war = wars.get(i);
                button(menu, i - start, Material.IRON_BARS, war.getRebelNationName(), List.of(text("orgmenu.independence_score",
                        decimal(war.getRebelScore()), decimal(war.getEmpireScore()), war.getElapsedCycles())), () ->
                        openSovereignSubject(player, org.getOrganizationName(), war.getRebelNationName()));
            }
        } else {
            var wars = plugin.getOrgManager().getActiveMilitaryWars(org);
            int start = paginate(menu, wars.size(), page, p -> openOrganizationWars(player, org, p));
            for (int i = start; i < Math.min(start + 45, wars.size()); i++) {
                var war = wars.get(i);
                button(menu, i - start, Material.MAP, text("orgmenu.war_pair", war.getAttackerNationName(), war.getDefenderNationName()),
                        List.of(text("orgmenu.war_score", decimal(war.getAttackerScore()), decimal(war.getDefenderScore())),
                                text("war.reason", plugin.getLocaleMessage(war.getReason().messageKey()))),
                        () -> openPeaceTreaty(player, org.getOrganizationName()));
            }
        }
        back(menu, () -> openOrganization(player, org.getOrganizationName()));
        player.openInventory(menu.inventory);
    }

    private List<String> subjectSummary(WDEorg org, String nation) {
        OrgManager manager = plugin.getOrgManager();
        List<String> summary = new ArrayList<>(List.of(text("orgmenu.tier", plugin.formatLocaleMessage("org_subject_tier_"
                        + manager.getSubjectAutonomyTier(org, nation).name().toLowerCase(Locale.ROOT))),
                text("orgmenu.suppression", manager.getSubjectSuppression(org, nation)),
                text("orgmenu.garrison", enabledText(manager.isGarrisonSubject(org, nation))),
                text("orgmenu.duty", enabledText(manager.isSubjectWarParticipationRequired(org, nation))),
                text("orgmenu.neutral", enabledText(manager.isNationNeutral(nation)))));
        WDEnation record = plugin.getNationManager().getNation(nation);
        if (record != null) {
            summary.add(text("management.leader", record.getGovernmentLeader()));
            summary.add(text("management.army_leader", record.getArmyCommander()));
            summary.add(text("management.faith", record.getReligionName()));
        }
        return summary;
    }

    private void openSovereignSubject(Player player, String orgName, String nation) {
        WDEorg org = plugin.getOrgManager().getOrganization(orgName);
        OrgManager manager = plugin.getOrgManager();
        if (org == null || !orgPermission(player, org, "viewer") || !manager.isSovereignSubjectNation(org, nation)) {
            player.sendMessage(text("stale")); return;
        }
        Menu menu = menu(player, text("orgmenu.subject_detail", nation));
        Runnable refresh = () -> {
            WDEorg current = manager.getOrganization(orgName);
            if (current != null && manager.isSovereignSubjectNation(current, nation)) openSovereignSubject(player, orgName, nation);
            else if (current != null) openOrganization(player, orgName);
            else openOrganizationBrowser(player, null, 0);
        };
        BooleanSupplier subjectExists = () -> orgPermission(player, org, "viewer") && manager.isSovereignSubjectNation(org, nation);
        BooleanSupplier chairman = () -> subjectExists.getAsBoolean() && orgPermission(player, org, "chairman");
        BooleanSupplier peace = () -> subjectExists.getAsBoolean() && !manager.hasActiveIndependenceWar(org, nation);
        BooleanSupplier ownSubject = () -> subjectExists.getAsBoolean() && nation.equalsIgnoreCase(orgActor(player));
        boolean garrison = manager.isGarrisonSubject(org, nation);
        button(menu, 4, Material.PLAYER_HEAD, nation, subjectSummary(org, nation), null);
        button(menu, 19, Material.COMPARATOR, text("orgmenu.suppression_rules"), List.of(
                text("orgmenu.suppression_period", plugin.getOrganizationConfig().getLong("sovereignEmpire.subjectControl.intervalHours", 24)),
                text("orgmenu.suppression_changes", plugin.getOrganizationConfig().getInt("sovereignEmpire.subjectControl.suppressionDecrease", 10),
                        plugin.getOrganizationConfig().getInt("sovereignEmpire.subjectControl.suppressionIncrease", 10)), text("orgmenu.capability_hint")), null);
        button(menu, 20, Material.GOLD_INGOT, text("orgmenu.tribute"), List.of(
                text("orgmenu.subject_tribute", garrison ? text("orgmenu.exempt") : formatOrgCharge(manager.getSovereignTributeCharge())),
                text("orgmenu.tribute_enabled", enabledText(plugin.getOrganizationConfig().getBoolean("sovereignEmpire.tribute.enabled", true))),
                text("orgmenu.forced_tribute", formatOrgCharge(manager.getForcedTributeCharge(org, nation))),
                text("orgmenu.force_reductions", manager.getForcedLeaderChangeSuppressionReduction(), manager.getForcedTributeSuppressionReduction())), null);
        button(menu, 21, Material.PAPER, text("orgmenu.tax_caps"), List.of(
                text("orgmenu.tax_caps_values", capText(manager.getSubjectTownTaxCap(org, nation, "FIXED")),
                        capText(manager.getSubjectTownTaxCap(org, nation, "WEALTH_PERCENTAGE")))), null);
        button(menu, 22, Material.IRON_SWORD, text("orgmenu.military_rules"), List.of(text("orgmenu.garrison_hint"),
                text("orgmenu.inspection", plugin.getOrganizationConfig().getLong("sovereignEmpire.warParticipation.checkIntervalSeconds", 180),
                        plugin.getOrganizationConfig().getDouble("sovereignEmpire.warParticipation.commanderRadius", 200)), text("orgmenu.neutral_hint")), null);
        button(menu, 23, Material.GRASS_BLOCK, text("management.towns"), List.of(), () -> {
            WDEnation record = plugin.getNationManager().getNation(nation);
            if (record == null) { player.sendMessage(text("stale")); return; }
            managementChoices(player, text("management.towns"), record.getTownNames(), 0, value -> openTownManagement(player, value), refresh);
        });
        var war = manager.getActiveIndependenceWar(org, nation);
        List<String> warInfo = new ArrayList<>(war == null ? List.of(text("orgmenu.no_independence_war"))
                : List.of(text("orgmenu.independence_score", decimal(war.getRebelScore()), decimal(war.getEmpireScore()), war.getElapsedCycles())));
        warInfo.add(text("orgmenu.independence_cycle", plugin.getOrganizationConfig().getInt("sovereignEmpire.independenceWar.cycleSeconds", 300),
                plugin.getOrganizationConfig().getInt("sovereignEmpire.independenceWar.maxCycles", 12)));
        warInfo.add(text("orgmenu.independence_lead", plugin.getOrganizationConfig().getDouble("sovereignEmpire.independenceWar.successLead", 100)));
        warInfo.add(text("orgmenu.independence_treaty_hint"));
        button(menu, 24, Material.IRON_BARS, text("orgmenu.independence_status"), warInfo, null);
        if (chairman.getAsBoolean()) {
            subjectAction(menu, 28, player, org, nation, "demoteautonomy", "orgmenu.demote",
                    () -> chairman.getAsBoolean() && peace.getAsBoolean() && manager.canDemoteSubjectAutonomy(org, nation),
                    List.of(text("orgmenu.demote_rules")), refresh);
            subjectAction(menu, 29, player, org, nation, "setgarrison", garrison ? "orgmenu.unset_garrison" : "orgmenu.set_garrison",
                    () -> chairman.getAsBoolean() && garrison == manager.isGarrisonSubject(org, nation),
                    List.of(text("orgmenu.garrison_hint")), refresh, garrison ? "disabled" : "enabled");
            boolean duty = manager.isSubjectWarParticipationRequired(org, nation);
            subjectAction(menu, 30, player, org, nation, "mobilizewar", duty ? "orgmenu.unmobilize" : "orgmenu.mobilize",
                    () -> chairman.getAsBoolean() && peace.getAsBoolean() && duty == manager.isSubjectWarParticipationRequired(org, nation)
                            && (!duty ? !manager.isNationNeutral(nation) : !manager.isGarrisonSubject(org, nation)),
                    List.of(text("orgmenu.mobilize_rules")), refresh, duty ? "disabled" : "enabled");
            button(menu, 31, peace.getAsBoolean() ? Material.PLAYER_HEAD : Material.BARRIER, text("orgmenu.replace_leader"),
                    List.of(text("orgmenu.replace_warning")), peace.getAsBoolean() ? () -> managementChoices(player, text("orgmenu.replace_leader"),
                            nationResidents(nation), 0, value -> managementSubmit(player, () -> chairman.getAsBoolean() && peace.getAsBoolean(),
                                    text("orgmenu.replace_summary", nation, value), refresh, "org", "replaceleader", orgName, nation, value), refresh) : null);
            subjectAction(menu, 32, player, org, nation, "demandtribute", "orgmenu.demand_tribute",
                    () -> chairman.getAsBoolean() && peace.getAsBoolean(), List.of(text("orgmenu.forced_tribute", formatOrgCharge(manager.getForcedTributeCharge(org, nation)))),
                    refresh);
            subjectAction(menu, 33, player, org, nation, "grantindependence", "orgmenu.grant_independence",
                    () -> chairman.getAsBoolean() && manager.hasActiveIndependenceWar(org, nation),
                    List.of(text("orgmenu.grant_warning")), refresh);
        }
        if (ownSubject.getAsBoolean()) {
            boolean dominion = manager.getSubjectAutonomyTier(org, nation) == SovereignSubjectState.AutonomyTier.DOMINION;
            String promotionTitle = text(dominion ? "orgmenu.peaceful_independence" : "orgmenu.promote");
            button(menu, 37, manager.canPromoteSubjectAutonomy(org, nation) && peace.getAsBoolean() ? Material.EMERALD : Material.BARRIER,
                    promotionTitle, List.of(text(dominion ? "orgmenu.peaceful_independence_rules" : "orgmenu.promote_rules"),
                            text("orgmenu.upgrade_cost", formatOrgCharge(manager.getSovereignAutonomyUpgradeCharge()))),
                    () -> managementSubmit(player, () -> ownSubject.getAsBoolean() && peace.getAsBoolean()
                                    && dominion == (manager.getSubjectAutonomyTier(org, nation) == SovereignSubjectState.AutonomyTier.DOMINION)
                                    && manager.canPromoteSubjectAutonomy(org, nation),
                            promotionTitle, refresh, "org", "promoteautonomy", orgName));
            button(menu, 38, Material.RED_BANNER, text("orgmenu.independence"), List.of(text("orgmenu.independence_warning")), () ->
                    managementSubmit(player, () -> ownSubject.getAsBoolean() && peace.getAsBoolean(), text("orgmenu.independence_warning"),
                            refresh, "org", "leave", orgName));
        }
        organizationPeaceButtons(menu, player, org);
        back(menu, () -> openOrganizationMembers(player, org, 0));
        player.openInventory(menu.inventory);
    }

    private String capText(double value) {
        return value == Double.MAX_VALUE ? text("orgmenu.unlimited") : decimal(value);
    }

    private void subjectAction(Menu menu, int slot, Player player, WDEorg org, String nation, String action, String key,
                               BooleanSupplier allowed, List<String> lore, Runnable refresh, String... extra) {
        List<String> description = new ArrayList<>(lore);
        if (!allowed.getAsBoolean()) description.add(text("orgmenu.action_blocked"));
        String[] args = new String[4 + extra.length];
        args[0] = "org"; args[1] = action; args[2] = org.getOrganizationName(); args[3] = nation;
        System.arraycopy(extra, 0, args, 4, extra.length);
        button(menu, slot, allowed.getAsBoolean() ? Material.LEVER : Material.BARRIER, text(key), description,
                allowed.getAsBoolean() ? () -> managementSubmit(player, allowed, text("orgmenu.action_target", text(key), nation), refresh, args) : null);
    }

    public void openPeaceTreaty(Player player, String organization) {
        String actor = kingNation(player);
        if (actor == null || !plugin.orgEnabled) {
            player.sendMessage(text("peace.no_authority"));
            return;
        }
        List<OrgManager.PeaceTreatyMenuTarget> targets = plugin.getOrgManager()
                .getPeaceTreatyMenuTargets(actor, plugin.getNationManager()).stream()
                .filter(t -> organization == null || t.organization().equalsIgnoreCase(organization)).toList();
        if (targets.isEmpty()) {
            if (!plugin.getOrgManager().getIncomingPeaceTreaties(actor).isEmpty()) {
                openPeaceInbox(player, organization);
                return;
            }
            player.sendMessage(text("peace.no_war"));
            return;
        }
        openTreaty(player, new TreatyDraft(actor, organization, targets.getFirst()));
    }

    private String kingNation(Player player) {
        var town = com.palmergames.bukkit.towny.TownyAPI.getInstance().getTown(player);
        var nation = town == null ? null : town.getNationOrNull();
        return nation != null && nation.getKing() != null
                && player.getUniqueId().equals(nation.getKing().getUUID()) ? nation.getName() : null;
    }

    private boolean treatyAuthority(Player player, TreatyDraft draft) {
        if (plugin.orgEnabled && draft.actor.equals(kingNation(player))) return true;
        player.sendMessage(text("peace.no_authority"));
        player.closeInventory();
        return false;
    }

    private OrgManager.PeaceTreatyMenuPreview treatyPreview(TreatyDraft draft) {
        return plugin.getOrgManager().previewPeaceTreaty(
                plugin.getOrgManager().getOrganization(draft.target.organization()), draft.actor,
                draft.target.targetNation(), draft.type, new ArrayList<>(draft.clauses.values()), plugin.getNationManager());
    }

    private void openTreaty(Player player, TreatyDraft draft) {
        if (!treatyAuthority(player, draft)) return;
        Menu menu = menu(player, text("peace.title"));
        OrgManager.PeaceTreatyMenuPreview preview = treatyPreview(draft);
        button(menu, 4, Material.NETHER_STAR, text("peace.score", String.format(Locale.ROOT, "%.2f", preview.scoreDifference())),
                List.of(text("peace.cost", preview.points()), text("peace.target", draft.target.targetNation()),
                        text("peace.score_hint")), null);
        button(menu, 12, draft.type == OrgManager.PeaceTreatyProposalState.ProposalType.DEMAND ? Material.LIME_DYE : Material.GRAY_DYE,
                text("peace.demand"), List.of(text("peace.demand_hint")), () -> {
                    if (draft.type == OrgManager.PeaceTreatyProposalState.ProposalType.DEMAND) return;
                    draft.type = OrgManager.PeaceTreatyProposalState.ProposalType.DEMAND;
                    draft.clauses.clear();
                    openTreaty(player, draft);
                });
        button(menu, 14, draft.type == OrgManager.PeaceTreatyProposalState.ProposalType.SURRENDER ? Material.LIME_DYE : Material.GRAY_DYE,
                text("peace.give"), List.of(text("peace.give_hint")), () -> {
                    if (draft.type == OrgManager.PeaceTreatyProposalState.ProposalType.SURRENDER) return;
                    draft.type = OrgManager.PeaceTreatyProposalState.ProposalType.SURRENDER;
                    draft.clauses.clear();
                    openTreaty(player, draft);
                });
        button(menu, 0, Material.COMPASS, text("peace.wars"), List.of(), () -> openTreatyWars(player, draft, 0));
        button(menu, 8, Material.CHEST, text("peace.inbox"), List.of(), () -> openPeaceInbox(player, draft.organization));
        button(menu, 10, Material.RED_BANNER, text("peace.attacker_pages"), List.of(), null);
        button(menu, 16, Material.BLUE_BANNER, text("peace.defender_pages"), List.of(), null);
        drawTreatyNations(player, menu, draft, true);
        drawTreatyNations(player, menu, draft, false);
        var types = OrgManager.PeaceTreatyClauseState.ClauseType.values();
        int pages = Math.max(1, (types.length + 14) / 15);
        draft.clausePage = Math.max(0, Math.min(draft.clausePage, pages - 1));
        for (int i = draft.clausePage * 15; i < Math.min(types.length, draft.clausePage * 15 + 15); i++) {
            var type = types[i];
            int index = i % 15;
            button(menu, 20 + index / 5 * 9 + index % 5,
                    draft.clauses.containsKey(type) ? Material.ENCHANTED_BOOK : Material.BOOK,
                    clauseTitle(type), List.of(text(draft.clauses.containsKey(type) ? "selected" : "pending"), text("peace.edit")),
                    () -> openTreatyClause(player, draft, type, new LinkedHashMap<>(
                            draft.clauses.containsKey(type) ? clauseParameters(draft.clauses.get(type)) : Map.of())));
        }
        button(menu, 48, Material.ARROW, text("peace.clause_previous"), List.of(), () -> {
            draft.clausePage = Math.max(0, draft.clausePage - 1); openTreaty(player, draft);
        });
        button(menu, 50, Material.ARROW, text("peace.clause_next"), List.of(), () -> {
            draft.clausePage = Math.min(pages - 1, draft.clausePage + 1); openTreaty(player, draft);
        });
        button(menu, 49, Material.PAPER, text("page", draft.clausePage + 1, pages), List.of(text("peace.clause_pages")), null);
        button(menu, 47, Material.BARRIER, text("cancel"), List.of(), player::closeInventory);
        List<String> submitLore = new ArrayList<>(List.of(text("peace.submit_hint"), text("peace.cost", preview.points())));
        if (preview.error() != null) submitLore.add(plugin.formatLocaleMessage(preview.error()));
        button(menu, 51, Material.LIME_DYE, text("peace.agree"), submitLore, () -> {
            if (!treatyAuthority(player, draft)) return;
            var latest = treatyPreview(draft);
            if (latest.error() != null) {
                player.sendMessage(plugin.formatLocaleMessage(latest.error()));
                openTreaty(player, draft);
                return;
            }
            confirm(player, text("peace.target", draft.target.targetNation()),
                    List.of(text("peace.cost", latest.points()), text("peace.submit_hint")),
                    () -> openTreaty(player, draft), () -> submitTreaty(player, draft, latest));
        });
        player.openInventory(menu.inventory);
    }

    private void drawTreatyNations(Player player, Menu menu, TreatyDraft draft, boolean attackers) {
        List<String> nations = attackers ? draft.target.attackers() : draft.target.defenders();
        int pages = Math.max(1, (nations.size() + 5) / 6);
        int page = Math.max(0, Math.min(attackers ? draft.attackerPage : draft.defenderPage, pages - 1));
        if (attackers) draft.attackerPage = page; else draft.defenderPage = page;
        for (int i = page * 6; i < Math.min(nations.size(), page * 6 + 6); i++) {
            String name = nations.get(i);
            int index = i % 6;
            int slot = 18 + index / 2 * 9 + index % 2 + (attackers ? 0 : 7);
            button(menu, slot, Material.PLAYER_HEAD, text(attackers ? "peace.attacker" : "peace.defender", name),
                    List.of(text(name.equals(draft.target.targetNation()) ? "selected" : "peace.select_target")), () -> {
                        var target = plugin.getOrgManager().getPeaceTreatyMenuTargets(draft.actor, plugin.getNationManager())
                                .stream().filter(t -> t.organization().equals(draft.target.organization())
                                        && t.targetNation().equals(name)
                                        && t.attackers().equals(draft.target.attackers())
                                        && t.defenders().equals(draft.target.defenders())).findFirst().orElse(null);
                        if (target == null) {
                            player.sendMessage(text("peace.invalid_target"));
                            return;
                        }
                        if (target.targetNation().equals(draft.target.targetNation())) return;
                        draft.target = target;
                        draft.clauses.clear();
                        openTreaty(player, draft);
                    });
            var nation = plugin.getNationManager().getTownyNation(name);
            ItemStack head = menu.inventory.getItem(slot);
            if (nation != null && nation.getKing() != null && head != null && head.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(nation.getKing().getUUID()));
                head.setItemMeta(meta);
            }
        }
        button(menu, attackers ? 45 : 52, Material.ARROW, text("previous"),
                List.of(text(attackers ? "peace.attacker_pages" : "peace.defender_pages"), text("page", page + 1, pages)), () -> {
                    if (attackers) draft.attackerPage = Math.max(0, page - 1);
                    else draft.defenderPage = Math.max(0, page - 1);
                    openTreaty(player, draft);
                });
        button(menu, attackers ? 46 : 53, Material.ARROW, text("next"), List.of(), () -> {
            if (attackers) draft.attackerPage = Math.min(pages - 1, page + 1);
            else draft.defenderPage = Math.min(pages - 1, page + 1);
            openTreaty(player, draft);
        });
    }

    private void openTreatyWars(Player player, TreatyDraft draft, int page) {
        if (!treatyAuthority(player, draft)) return;
        var targets = plugin.getOrgManager().getPeaceTreatyMenuTargets(draft.actor, plugin.getNationManager()).stream()
                .filter(t -> draft.organization == null || draft.organization.equalsIgnoreCase(t.organization())).toList();
        Menu menu = menu(player, text("peace.wars"));
        int start = paginate(menu, targets.size(), page, p -> openTreatyWars(player, draft, p));
        for (int i = start; i < Math.min(start + 45, targets.size()); i++) {
            var target = targets.get(i);
            button(menu, i - start, Material.MAP, target.targetNation(), List.of(target.organization()), () -> {
                draft.target = target; draft.clauses.clear();
                draft.attackerPage = 0; draft.defenderPage = 0;
                openTreaty(player, draft);
            });
        }
        back(menu, () -> openTreaty(player, draft));
        player.openInventory(menu.inventory);
    }

    private String clauseTitle(OrgManager.PeaceTreatyClauseState.ClauseType type) {
        return text("peace.clauses." + type.name().toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> clauseParameters(OrgManager.PeaceTreatyClauseState clause) {
        return new LinkedHashMap<>((Map<String, Object>) clause.toMap().get("parameters"));
    }

    private void openTreatyClause(Player player, TreatyDraft draft, OrgManager.PeaceTreatyClauseState.ClauseType type,
                                  Map<String, Object> params) {
        if (!treatyAuthority(player, draft)) return;
        Menu menu = menu(player, clauseTitle(type));
        button(menu, 4, Material.WRITTEN_BOOK, clauseTitle(type), List.of(text("peace.effects." + type.name().toLowerCase(Locale.ROOT))), null);
        if (type == OrgManager.PeaceTreatyClauseState.ClauseType.REPARATIONS) {
            int max = Math.max(1, plugin.getWarConfig().getInt("peaceTreaty.clauses.reparations.maxPercent", 20));
            int percent = Math.max(1, Math.min(max, new OrgManager.PeaceTreatyClauseState(type, params).getInt("percent")));
            params.put("percent", percent);
            button(menu, 22, Material.GOLD_INGOT, text("peace.percent", percent), List.of(), null);
            button(menu, 20, Material.RED_DYE, text("peace.minus"), List.of(), () -> {
                params.put("percent", Math.max(1, percent - 1)); openTreatyClause(player, draft, type, params);
            });
            button(menu, 24, Material.LIME_DYE, text("peace.plus"), List.of(), () -> {
                params.put("percent", Math.min(max, percent + 1)); openTreatyClause(player, draft, type, params);
            });
        } else if (type == OrgManager.PeaceTreatyClauseState.ClauseType.TRANSFER_TOWN) {
            String source = draft.type == OrgManager.PeaceTreatyProposalState.ProposalType.DEMAND
                    ? draft.target.targetNation() : draft.actor;
            button(menu, 20, Material.GRASS_BLOCK, text("peace.town", params.getOrDefault("town", "-")), List.of(), () ->
                    chooseTreatyValue(player, draft, type, params, "town", plugin.getTownManager().getAllTowns().stream()
                            .filter(t -> source.equalsIgnoreCase(t.getNationName())).map(WDEtown::getTownName).sorted().toList(), 0));
            List<String> beneficiaries = draft.target.attackers().contains(draft.actor)
                    == (draft.type == OrgManager.PeaceTreatyProposalState.ProposalType.DEMAND)
                    ? draft.target.attackers() : draft.target.defenders();
            button(menu, 24, Material.PLAYER_HEAD, text("peace.destination", params.getOrDefault("destinationNation", "-")), List.of(), () ->
                    chooseTreatyValue(player, draft, type, params, "destinationNation", beneficiaries, 0));
        } else if (type == OrgManager.PeaceTreatyClauseState.ClauseType.LIBERATE_SUBJECT) {
            button(menu, 22, Material.IRON_BARS, text("peace.subject", params.getOrDefault("subjectNation", "-")), List.of(), () ->
                    chooseTreatyValue(player, draft, type, params, "subjectNation", plugin.getNationManager().getAllNations()
                            .stream().map(WDEnation::getNationName).sorted().toList(), 0));
        }
        button(menu, 30, Material.LIME_DYE, text("peace.include"), List.of(), () -> {
            if (!treatyAuthority(player, draft)) return;
            if ((type == OrgManager.PeaceTreatyClauseState.ClauseType.TRANSFER_TOWN
                    && (!params.containsKey("town") || !params.containsKey("destinationNation")))
                    || (type == OrgManager.PeaceTreatyClauseState.ClauseType.LIBERATE_SUBJECT && !params.containsKey("subjectNation"))) {
                player.sendMessage(text("peace.incomplete")); return;
            }
            draft.clauses.put(type, new OrgManager.PeaceTreatyClauseState(type, params)); openTreaty(player, draft);
        });
        button(menu, 32, Material.RED_DYE, text("peace.remove"), List.of(), () -> {
            draft.clauses.remove(type); openTreaty(player, draft);
        });
        back(menu, () -> openTreaty(player, draft));
        player.openInventory(menu.inventory);
    }

    private void chooseTreatyValue(Player player, TreatyDraft draft, OrgManager.PeaceTreatyClauseState.ClauseType type,
                                   Map<String, Object> params, String key, List<String> choices, int page) {
        if (!treatyAuthority(player, draft)) return;
        Menu menu = menu(player, clauseTitle(type));
        int start = paginate(menu, choices.size(), page, p -> chooseTreatyValue(player, draft, type, params, key, choices, p));
        for (int i = start; i < Math.min(start + 45, choices.size()); i++) {
            String value = choices.get(i);
            button(menu, i - start, Material.PAPER, value, List.of(), () -> {
                params.put(key, value); openTreatyClause(player, draft, type, params);
            });
        }
        back(menu, () -> openTreatyClause(player, draft, type, params));
        player.openInventory(menu.inventory);
    }

    private void submitTreaty(Player player, TreatyDraft draft, OrgManager.PeaceTreatyMenuPreview quote) {
        if (!treatyAuthority(player, draft)) return;
        var latest = treatyPreview(draft);
        if (!latest.equals(quote)) {
            player.sendMessage(text("stale")); openTreaty(player, draft); return;
        }
        var result = plugin.getOrgManager().proposePeaceTreaty(
                plugin.getOrgManager().getOrganization(draft.target.organization()), draft.actor,
                draft.target.targetNation(), draft.type, new ArrayList<>(draft.clauses.values()), plugin.getNationManager());
        if (!result.isSuccess()) {
            player.sendMessage(plugin.formatLocaleMessage(result.getMessageKey()));
            openTreaty(player, draft); return;
        }
        player.closeInventory();
        success(player);
        player.sendMessage(text(result.getStatus() == OrgManager.PeaceTreatyProposalResult.Status.FORCED_ACCEPTED
                ? "peace.forced" : "peace.submitted"));
        var targetNation = plugin.getNationManager().getTownyNation(draft.target.targetNation());
        if (result.getStatus() != OrgManager.PeaceTreatyProposalResult.Status.FORCED_ACCEPTED
                && targetNation != null && targetNation.getKing() != null) {
            Player king = Bukkit.getPlayer(targetNation.getKing().getUUID());
            if (king != null) king.sendMessage(text("peace.received", draft.actor, draft.target.organization(), draft.actor));
        }
    }

    public void openPeaceInbox(Player player, String organization) {
        String actor = kingNation(player);
        if (actor == null || !plugin.orgEnabled) {
            player.sendMessage(text("peace.no_authority"));
            return;
        }
        openPeaceInboxPage(player, actor, organization, 0);
    }

    private void openPeaceInboxPage(Player player, String actor, String organization, int page) {
        if (!actor.equals(kingNation(player)) || !plugin.orgEnabled) {
            player.sendMessage(text("peace.no_authority")); player.closeInventory(); return;
        }
        var incoming = plugin.getOrgManager().getIncomingPeaceTreaties(actor).stream()
                .filter(t -> organization == null || organization.equalsIgnoreCase(t.organization())).toList();
        Menu menu = menu(player, text("peace.inbox"));
        int start = paginate(menu, incoming.size(), page, p -> openPeaceInboxPage(player, actor, organization, p));
        for (int i = start; i < Math.min(start + 45, incoming.size()); i++) {
            var treaty = incoming.get(i);
            button(menu, i - start, Material.WRITTEN_BOOK, treaty.proposal().getProposerNationName(),
                    List.of(text("peace.cost", treaty.proposal().getTotalPoints())), () -> openReceivedTreaty(player, actor, treaty, 0));
        }
        back(menu, () -> openHome(player));
        player.openInventory(menu.inventory);
    }

    private void openReceivedTreaty(Player player, String actor, OrgManager.PeaceTreatyMenuIncoming incoming, int requestedPage) {
        if (!actor.equals(kingNation(player)) || !plugin.orgEnabled) {
            player.sendMessage(text("peace.no_authority")); player.closeInventory(); return;
        }
        var proposal = incoming.proposal();
        Menu menu = menu(player, text("peace.inbox"));
        button(menu, 4, Material.NETHER_STAR, text("peace.cost", proposal.getTotalPoints()),
                List.of(text("peace.target", proposal.getProposerNationName()), text("peace.readonly")), null);
        button(menu, 13, Material.PAPER, text(proposal.getProposalType() == OrgManager.PeaceTreatyProposalState.ProposalType.DEMAND
                ? "peace.incoming_demand" : "peace.incoming_give"), List.of(), null);
        var clauses = proposal.getClauses();
        int pages = Math.max(1, (clauses.size() + 14) / 15);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        for (int i = page * 15; i < Math.min(clauses.size(), page * 15 + 15); i++) {
            var clause = clauses.get(i);
            int index = i % 15;
            List<String> lore = new ArrayList<>(List.of(text("peace.effects." + clause.getType().name().toLowerCase(Locale.ROOT))));
            if (clause.getType() == OrgManager.PeaceTreatyClauseState.ClauseType.REPARATIONS) lore.add(text("peace.percent", clause.getInt("percent")));
            if (!clause.getString("town").isBlank()) lore.add(text("peace.town", clause.getString("town")));
            if (!clause.getString("destinationNation").isBlank()) lore.add(text("peace.destination", clause.getString("destinationNation")));
            if (!clause.getString("subjectNation").isBlank()) lore.add(text("peace.subject", clause.getString("subjectNation")));
            button(menu, 20 + index / 5 * 9 + index % 5, Material.ENCHANTED_BOOK, clauseTitle(clause.getType()), lore, null);
        }
        button(menu, 45, Material.ARROW, text("previous"), List.of(), () -> openReceivedTreaty(player, actor, incoming, page - 1));
        button(menu, 53, Material.ARROW, text("next"), List.of(), () -> openReceivedTreaty(player, actor, incoming, page + 1));
        button(menu, 49, Material.PAPER, text("page", page + 1, pages), List.of(), null);
        button(menu, 47, Material.RED_DYE, text("peace.reject"), List.of(), () -> reviewReceivedTreaty(player, actor, incoming, false));
        button(menu, 51, Material.LIME_DYE, text("peace.agree"), List.of(), () ->
                confirm(player, text("peace.agree"), List.of(text("peace.review_warning")),
                        () -> openReceivedTreaty(player, actor, incoming, page), () -> reviewReceivedTreaty(player, actor, incoming, true)));
        button(menu, 0, Material.ARROW, text("back"), List.of(), () -> openPeaceInbox(player, incoming.organization()));
        player.openInventory(menu.inventory);
    }

    private void reviewReceivedTreaty(Player player, String actor, OrgManager.PeaceTreatyMenuIncoming incoming, boolean accept) {
        if (!actor.equals(kingNation(player)) || !plugin.orgEnabled) {
            player.sendMessage(text("peace.no_authority")); player.closeInventory(); return;
        }
        // Never accept a replacement proposal using an older inventory's confirmation.
        boolean current = plugin.getOrgManager().getIncomingPeaceTreaties(actor).stream()
                .anyMatch(t -> t.organization().equals(incoming.organization())
                        && t.proposal().getWarKey().equals(incoming.proposal().getWarKey())
                        && t.proposal().toMap().equals(incoming.proposal().toMap()));
        if (!current) {
            player.sendMessage(text("stale")); openPeaceInbox(player, incoming.organization()); return;
        }
        var result = plugin.getOrgManager().reviewPeaceTreaty(
                plugin.getOrgManager().getOrganization(incoming.organization()), actor,
                incoming.proposal().getProposerNationName(), accept, plugin.getNationManager());
        if (result.getStatus() == OrgManager.PeaceTreatyReviewResult.Status.INVALID) {
            player.sendMessage(plugin.formatLocaleMessage(result.getMessageKey()));
        } else {
            player.sendMessage(plugin.formatLocaleMessage(accept ? "org_peace_treaty_accepted" : "org_peace_treaty_rejected",
                    incoming.proposal().getProposerNationName()));
            success(player);
        }
        openPeaceInbox(player, incoming.organization());
    }

    /**
     * 保存单个玩家尚未提交的和约草稿，包括对象、方向、条款及独立分页状态。
     */
    private static final class TreatyDraft {
        private final String actor;
        private final String organization;
        private OrgManager.PeaceTreatyMenuTarget target;
        private OrgManager.PeaceTreatyProposalState.ProposalType type = OrgManager.PeaceTreatyProposalState.ProposalType.DEMAND;
        private final Map<OrgManager.PeaceTreatyClauseState.ClauseType, OrgManager.PeaceTreatyClauseState> clauses = new LinkedHashMap<>();
        private int attackerPage;
        private int defenderPage;
        private int clausePage;

        private TreatyDraft(String actor, String organization, OrgManager.PeaceTreatyMenuTarget target) {
            this.actor = actor; this.organization = organization; this.target = target;
        }
    }

    public void sendPlayerOnly(org.bukkit.command.CommandSender sender) {
        sender.sendMessage(text("player_only"));
    }

    public void openBackground(Player player) {
        if (!backgrounds.isEnabled()) {
            player.sendMessage(text("unavailable"));
            return;
        }
        Menu menu = menu(player, text("background"));
        for (RoleBackgroundStage stage : RoleBackgroundStage.values()) {
            String id = backgrounds.getSelectedBackgroundId(resident(player), stage);
            RoleBackgroundDefinition selected = backgrounds.getDefinition(stage, id);
            List<String> lore = new ArrayList<>();
            lore.add(id.isBlank() ? text("pending") : text("selected") + " " + (selected == null ? id : selected.getTitle()));
            lore.add(text("irreversible"));
            boolean locked = stage == RoleBackgroundStage.ADULTHOOD
                    && !backgrounds.hasSelectedStage(resident(player), RoleBackgroundStage.CHILDHOOD);
            if (locked) lore.add(text("locked"));
            button(menu, stage == RoleBackgroundStage.CHILDHOOD ? 20 : 24,
                    locked ? Material.GRAY_DYE : Material.BOOK, stageName(stage), lore, () -> {
                        if (!locked) openBackgroundList(player, stage, 0);
                    });
        }
        back(menu, () -> openHome(player));
        player.openInventory(menu.inventory);
    }

    private String stageName(RoleBackgroundStage stage) {
        return text(stage == RoleBackgroundStage.CHILDHOOD ? "childhood" : "adulthood");
    }

    private List<String> backgroundLore(Player player, RoleBackgroundDefinition definition) {
        List<String> lore = new ArrayList<>(backgrounds.resolveLore(player, definition));
        for (var entry : definition.getSkillLevelBonuses().entrySet()) {
            String skill = entry.getKey().replace(':', '/');
            skill = skill.substring(skill.lastIndexOf('/') + 1);
            lore.add(text("bonus", messages.getString("ui.skills." + skill, skill), entry.getValue()));
        }
        return lore;
    }

    private void openBackgroundList(Player player, RoleBackgroundStage stage, int page) {
        if (!backgrounds.isEnabled()) return;
        List<RoleBackgroundDefinition> entries = backgrounds.getDefinitions(stage);
        Menu menu = menu(player, text("background") + " - " + stageName(stage));
        int start = paginate(menu, entries.size(), page, p -> openBackgroundList(player, stage, p));
        for (int i = start; i < Math.min(start + 45, entries.size()); i++) {
            RoleBackgroundDefinition definition = entries.get(i);
            List<String> lore = backgroundLore(player, definition);
            lore.add(text("preview"));
            button(menu, i - start, Material.BOOK, definition.getTitle(), lore,
                    () -> openBackgroundDetail(player, stage, definition.getId(), page));
        }
        back(menu, () -> openBackground(player));
        player.openInventory(menu.inventory);
    }

    private void openBackgroundDetail(Player player, RoleBackgroundStage stage, String id, int page) {
        RoleBackgroundDefinition definition = backgrounds.getDefinition(stage, id);
        if (definition == null || !backgrounds.isEnabled()) return;
        Menu menu = menu(player, text("background"));
        button(menu, 22, Material.WRITTEN_BOOK, definition.getTitle(), backgroundLore(player, definition), null);
        if (!backgrounds.hasSelectedStage(resident(player), stage)
                && backgrounds.getNextPendingStage(resident(player)) == stage) {
            button(menu, 30, Material.LIME_DYE, text("choose"), List.of(text("irreversible")),
                    () -> confirm(player, definition.getTitle(), List.of(text("irreversible")),
                            () -> openBackgroundDetail(player, stage, id, page), () -> {
                                RoleBackgroundDefinition latest = backgrounds.getDefinition(stage, id);
                                if (latest != definition || !backgrounds.isEnabled()) {
                                    player.sendMessage(text("stale"));
                                } else {
                                    executeBackground(player, stage, id);
                                }
                                RoleBackgroundStage next = backgrounds.getNextPendingStage(resident(player));
                                if (next != null && backgrounds.isEnabled()) openBackgroundList(player, next, 0);
                                else openBackground(player);
                            }));
        } else {
            button(menu, 30, Material.GRAY_DYE, text("selected"), List.of(text("irreversible")), null);
        }
        back(menu, () -> openBackgroundList(player, stage, page));
        player.openInventory(menu.inventory);
    }

    private void executeBackground(Player player, RoleBackgroundStage stage, String id) {
        String before = backgrounds.getSelectedBackgroundId(resident(player), stage);
        backgroundCommand.onCommand(player, plugin.getCommand("wde"), "wde",
                new String[]{"background", "choose", stage.getConfigKey(), id});
        if (!before.equals(backgrounds.getSelectedBackgroundId(resident(player), stage))) success(player);
    }

    public void openIdeology(Player player) {
        if (!plugin.religionEnabled) {
            player.sendMessage(text("unavailable"));
            return;
        }
        Menu menu = menu(player, text("ideology"));
        button(menu, 20, Material.ENCHANTED_BOOK, text("join"),
                List.of(text("current", currentFaith(player)), text("ideology_hint")),
                () -> openReligionList(player, 0, false));
        if (religions.isReligionLeader(player.getUniqueId())) {
            button(menu, 22, Material.BARRIER, text("create"),
                    List.of(text("creation_leader_blocked")), null);
        } else {
            button(menu, 22, Material.WRITABLE_BOOK, text("create"),
                    List.of(text("creation_cost", plugin.getEconomyBridge().format(plugin.getReligionCreationCostCharge()))),
                    () -> beginNameInput(player));
        }
        button(menu, 24, Material.BOOKSHELF, text("manage"), List.of(text("readonly")),
                () -> openReligionList(player, 0, true));
        button(menu, 47, Material.NAME_TAG, text("current", currentFaith(player)),
                List.of(text("single_faith")), null);
        back(menu, () -> openHome(player));
        player.openInventory(menu.inventory);
    }

    private String currentFaith(Player player) {
        String name = resident(player).getReligionName();
        return name == null || name.isBlank() || name.equalsIgnoreCase("none") ? text("none") : name;
    }

    private void openReligionList(Player player, int page, boolean ownOnly) {
        if (!plugin.religionEnabled) return;
        List<String> names = new ArrayList<>(religions.getAllReligionNames());
        if (ownOnly) names.removeIf(name -> !isLeader(player, religions.getReligion(name)));
        names.sort(String.CASE_INSENSITIVE_ORDER);
        Menu menu = menu(player, text(ownOnly ? "manage" : "ideology"));
        int start = paginate(menu, names.size(), page, p -> openReligionList(player, p, ownOnly));
        for (int i = start; i < Math.min(start + 45, names.size()); i++) {
            String name = names.get(i);
            WDEreligion religion = religions.getReligion(name);
            button(menu, i - start, Material.ENCHANTED_BOOK, name,
                    List.of(text("leader", religion.getPontiffName()), text("preview")),
                    () -> openReligionDetail(player, name));
        }
        back(menu, () -> openIdeology(player));
        player.openInventory(menu.inventory);
    }

    private void openReligionDetail(Player player, String name) {
        WDEreligion religion = religions.getReligion(name);
        if (religion == null || !plugin.religionEnabled) return;
        Menu menu = menu(player, text("detail", name));
        button(menu, 13, Material.ENCHANTED_BOOK, name,
                List.of(text("founder", religion.getFounderName()), text("leader", religion.getPontiffName())), null);
        button(menu, 20, Material.BOOK, text("doctrines", name), List.of(text("readonly")),
                () -> openDoctrines(player, name, 0));
        button(menu, 24, Material.AMETHYST_SHARD, text("memes", name), List.of(text("readonly")),
                () -> openMemes(player, name, 0,
                        new LinkedHashSet<>(religions.getConfiguredMemes(religion.getMemes()))));
        button(menu, 40, Material.GOLD_BLOCK, text("treasury.title", name),
                List.of(text("treasury.balance", plugin.getEconomyBridge().format(religion.getTreasuryBalance())),
                        text("treasury.sink")), () -> openIdeologyTreasury(player, name));
        if (!name.equalsIgnoreCase(resident(player).getReligionName())) {
            button(menu, 31, Material.LIME_DYE, text("join"), List.of(text("conversion_cost", conversionCost(player))),
                    () -> {
                        String quote = conversionCost(player);
                        confirm(player, name, List.of(text("conversion_cost", quote)), () -> openReligionDetail(player, name), () -> {
                            if (!quote.equals(conversionCost(player))) {
                                player.sendMessage(text("stale"));
                            } else {
                                String before = resident(player).getReligionName();
                                executeReligion(player, "setplayer", name);
                                if (!Objects.equals(before, resident(player).getReligionName())) success(player);
                            }
                            openReligionDetail(player, name);
                        });
                    });
        } else {
            button(menu, 31, Material.GRAY_DYE, text("selected"), List.of(), null);
        }
        back(menu, () -> openIdeology(player));
        player.openInventory(menu.inventory);
    }

    public void openIdeologyTreasury(Player player, String name) {
        WDEreligion religion = religions.getReligion(name);
        if (religion == null || !plugin.religionEnabled) return;
        Menu menu = menu(player, text("treasury.title", name));
        boolean enabled = plugin.getIdeologyTreasuryManager().treasuryEnabled();
        button(menu, 13, Material.GOLD_BLOCK, text("treasury.balance",
                        plugin.getEconomyBridge().format(religion.getTreasuryBalance())),
                List.of(text("treasury.sink"), text("treasury.daily"),
                        text("treasury.policy", managementValue(religion.getTitheType().name()),
                                religion.getTitheType() == WDEreligion.TitheType.WEALTH_PERCENTAGE
                                        ? religion.getTitheAmount().toPlainString() + "%"
                                        : plugin.getEconomyBridge().format(religion.getTitheAmount())),
                        text(enabled && plugin.getIdeologyConfig().getBoolean("economy.treasury.titheEnabled", true)
                                ? "treasury.tax_active" : "treasury.tax_paused")), null);
        button(menu, 20, enabled ? Material.GOLD_INGOT : Material.BARRIER, text("treasury.donate"),
                List.of(text(enabled ? "treasury.sink" : "treasury.unavailable")),
                enabled ? () -> inputDonation(player, name) : null);
        if (isLeader(player, religion)) {
            button(menu, 24, Material.PAPER, text("treasury.fixed"), List.of(text("treasury.zero")),
                    () -> inputTithe(player, name, WDEreligion.TitheType.FIXED));
            button(menu, 30, Material.SUNFLOWER, text("treasury.percentage"), List.of(text("treasury.wealth"), text("treasury.zero")),
                    () -> inputTithe(player, name, WDEreligion.TitheType.WEALTH_PERCENTAGE));
        }
        back(menu, () -> openReligionDetail(player, name));
        player.openInventory(menu.inventory);
    }

    private void inputDonation(Player player, String name) {
        Runnable refresh = () -> openIdeologyTreasury(player, name);
        managementInput(player, refresh, value -> {
            BigDecimal amount;
            try { amount = new BigDecimal(value); } catch (NumberFormatException ex) { return false; }
            if (!IdeologyTreasuryManager.validDonationAmount(amount)) return false;
            String currency = plugin.getDefaultEconomyCurrency();
            confirm(player, text("treasury.donation_confirm", plugin.getEconomyBridge().format(amount)),
                    List.of(text("treasury.sink")), refresh, () -> {
                        if (!currency.equals(plugin.getDefaultEconomyCurrency())) player.sendMessage(text("stale"));
                        else executeReligion(player, "donate", name, amount.toPlainString());
                        refresh.run();
                    });
            return true;
        }, "treasury.input_donation", "treasury.invalid_donation");
    }

    private void inputTithe(Player player, String name, WDEreligion.TitheType type) {
        Runnable refresh = () -> openIdeologyTreasury(player, name);
        if (!canEdit(player, religions.getReligion(name))) { refresh.run(); return; }
        managementInput(player, refresh, value -> {
            BigDecimal amount;
            try {
                amount = new BigDecimal(value);
                // Validate without mutating the live policy before confirmation.
                WDEreligion.validateTithePolicy(type, amount);
            } catch (IllegalArgumentException ex) { return false; }
            confirm(player, text("treasury.policy", managementValue(type.name()),
                            type == WDEreligion.TitheType.WEALTH_PERCENTAGE ? amount.toPlainString() + "%"
                                    : plugin.getEconomyBridge().format(amount)),
                    List.of(text("treasury.daily"), text("treasury.zero")), refresh, () -> {
                        if (canEdit(player, religions.getReligion(name))) {
                            executeReligion(player, "settithe", name, type.name(), amount.toPlainString());
                        }
                        refresh.run();
                    });
            return true;
        }, type == WDEreligion.TitheType.FIXED ? "treasury.input_fixed" : "treasury.input_percentage",
                "treasury.invalid_tithe");
    }

    private String conversionCost(Player player) {
        return resident(player).isReligionChosen()
                ? plugin.getEconomyBridge().format(religionCommand.getPlayerConversionCharge(player)) : text("free");
    }

    private boolean isLeader(Player player, WDEreligion religion) {
        return religion != null && player.getUniqueId().equals(religion.getPontiffUuid());
    }

    private boolean canEdit(Player player, WDEreligion religion) {
        if (!plugin.religionEnabled || !isLeader(player, religion)) {
            player.sendMessage(text("no_permission"));
            return false;
        }
        return true;
    }

    private void openDoctrines(Player player, String name, int page) {
        WDEreligion religion = religions.getReligion(name);
        if (religion == null || !plugin.religionEnabled) return;
        List<IdeologyDoctrineDefinition> entries = new ArrayList<>(religions.getRegisteredDoctrines());
        Menu menu = menu(player, text("doctrines", name));
        int start = paginate(menu, entries.size(), page, p -> openDoctrines(player, name, p));
        for (int i = start; i < Math.min(start + 45, entries.size()); i++) {
            IdeologyDoctrineDefinition definition = entries.get(i);
            String attitude = religion.getDoctrineAttitude(definition.getId());
            if (attitude == null) attitude = religions.resolveDoctrineDefaultAttitude(religion, definition);
            List<String> lore = new ArrayList<>(List.of(definition.getLore(), text("doctrine_current",
                    religions.getDoctrineAttitudeDisplayName(definition.getId(), attitude)), text("preview")));
            String doctrineCooldown = religions.describeDoctrineModificationCooldown(plugin.userLang, religion, definition.getId());
            if (!doctrineCooldown.isBlank()) lore.add(text("modification_cooldown", doctrineCooldown));
            button(menu, i - start, ideologyIcon("doctrines", definition.getId(), Material.PAPER), religions.getDoctrineDisplayName(definition.getId()),
                    lore,
                    () -> openAttitudes(player, name, definition.getId(), 0, page));
        }
        back(menu, () -> openReligionDetail(player, name));
        player.openInventory(menu.inventory);
    }

    private void openAttitudes(Player player, String name, String doctrineId, int page, int parentPage) {
        WDEreligion religion = religions.getReligion(name);
        IdeologyDoctrineDefinition doctrine = religions.getDoctrine(doctrineId);
        if (religion == null || doctrine == null || !plugin.religionEnabled) return;
        List<IdeologyDoctrineAttitudeDefinition> entries = new ArrayList<>(doctrine.getAttitudes().values());
        Menu menu = menu(player, text("attitudes", religions.getDoctrineDisplayName(doctrineId)));
        int start = paginate(menu, entries.size(), page, p -> openAttitudes(player, name, doctrineId, p, parentPage));
        for (int i = start; i < Math.min(start + 45, entries.size()); i++) {
            IdeologyDoctrineAttitudeDefinition attitude = entries.get(i);
            boolean allowed = religions.canUseDoctrineAttitude(religion, doctrineId, attitude.getId());
            List<String> lore = new ArrayList<>(List.of(attitude.getLore(), text("readonly")));
            if (!allowed) lore.add(text("blocked"));
            if (attitude.getId().equals(religion.getDoctrineAttitude(doctrineId))) lore.add(text("selected"));
            String doctrineCooldown = religions.describeDoctrineModificationCooldown(plugin.userLang, religion, doctrineId);
            if (!doctrineCooldown.isBlank()) lore.add(text("modification_cooldown", doctrineCooldown));
            button(menu, i - start, allowed ? ideologyIcon("doctrines", doctrineId, Material.PAPER) : Material.BARRIER,
                    religions.getDoctrineAttitudeDisplayName(doctrineId, attitude.getId()), lore, () -> {
                if (!canEdit(player, religions.getReligion(name))) return;
                if (!allowed) {
                    player.sendMessage(text("blocked"));
                    return;
                }
                if (religions.isDoctrineModificationBlocked(religions.getReligion(name), doctrineId)) {
                    player.sendMessage(text("modification_cooldown", religions.describeDoctrineModificationCooldown(plugin.userLang, religions.getReligion(name), doctrineId)));
                    return;
                }
                confirm(player,
                        religions.getDoctrineDisplayName(doctrineId) + " / "
                                + religions.getDoctrineAttitudeDisplayName(doctrineId, attitude.getId()),
                        List.of(attitude.getLore()),
                        () -> openAttitudes(player, name, doctrineId, page, parentPage), () -> {
                            WDEreligion latest = religions.getReligion(name);
                            if (canEdit(player, latest)) {
                                if (religions.getDoctrine(doctrineId) != doctrine
                                        || !religions.canUseDoctrineAttitude(latest, doctrineId, attitude.getId())) {
                                    player.sendMessage(text("stale"));
                                } else if (religions.isDoctrineModificationBlocked(latest, doctrineId)) {
                                    player.sendMessage(text("modification_cooldown",
                                            religions.describeDoctrineModificationCooldown(plugin.userLang, latest, doctrineId)));
                                } else {
                                    religions.setDoctrineAttitude(latest, doctrineId, attitude.getId());
                                    religions.markDoctrineModified(latest, doctrineId);
                                    religions.saveReligion(latest);
                                    player.sendMessage(text("doctrine_saved"));
                                    success(player);
                                }
                            }
                            openAttitudes(player, name, doctrineId, page, parentPage);
                        });
            });
        }
        back(menu, () -> openDoctrines(player, name, parentPage));
        player.openInventory(menu.inventory);
    }

    private void openMemes(Player player, String name, int page, LinkedHashSet<String> selected) {
        WDEreligion religion = religions.getReligion(name);
        if (religion == null || !plugin.religionEnabled) return;
        boolean editable = isLeader(player, religion);
        List<IdeologyMemeDefinition> entries = new ArrayList<>(religions.getRegisteredMemes());
        Menu menu = menu(player, text("memes", name));
        int start = paginate(menu, entries.size(), page, p -> openMemes(player, name, p, selected));
        for (int i = start; i < Math.min(start + 45, entries.size()); i++) {
            IdeologyMemeDefinition meme = entries.get(i);
            boolean checked = selected.contains(meme.getId());
            button(menu, i - start, ideologyIcon("memes", meme.getId(), Material.AMETHYST_SHARD),
                    religions.getMemeDisplayName(meme.getId()),
                    List.of(meme.getDescription(), editable ? text(checked ? "meme_selected" : "meme_unselected") : text("readonly")),
                    () -> {
                        if (!canEdit(player, religions.getReligion(name))) return;
                        if (checked) selected.remove(meme.getId());
                        else if (selected.size() >= 4) player.sendMessage(text("meme_limit"));
                        else if (religions.findExclusiveMemeConflict(meme.getId(), selected) != null) player.sendMessage(text("meme_conflict"));
                        else selected.add(meme.getId());
                        openMemes(player, name, page, selected);
                    });
        }
        if (editable) {
            List<String> saveLore = new ArrayList<>(List.of(text("meme_warning")));
            String memeCooldown = religions.describeMemeModificationCooldown(plugin.userLang, religion);
            if (!memeCooldown.isBlank()) saveLore.add(text("modification_cooldown", memeCooldown));
            button(menu, 47, Material.LIME_DYE, text("save_memes", selected.size()), saveLore, () -> {
            if (selected.isEmpty() || selected.size() > 4) {
                player.sendMessage(text("meme_limit"));
                return;
            }
            if (religions.isMemeModificationBlocked(religion)) {
                player.sendMessage(text("modification_cooldown", religions.describeMemeModificationCooldown(plugin.userLang, religion)));
                return;
            }
            List<String> summary = new ArrayList<>(selected.stream().map(religions::getMemeDisplayName).toList());
            summary.add(text("meme_warning"));
            confirm(player, text("memes", name), summary, () -> openMemes(player, name, page, selected), () -> {
                WDEreligion latest = religions.getReligion(name);
                if (canEdit(player, latest)) {
                    if (religions.isMemeModificationBlocked(latest)) {
                        player.sendMessage(text("modification_cooldown", religions.describeMemeModificationCooldown(plugin.userLang, latest)));
                    } else {
                        List<String> args = new ArrayList<>(List.of("setmemes", name));
                        args.addAll(selected);
                        executeReligion(player, args.toArray(String[]::new));
                        if (new HashSet<>(religions.getReligion(name).getMemes()).equals(selected)) success(player);
                    }
                }
                openReligionDetail(player, name);
            });
            });
        }
        back(menu, () -> openReligionDetail(player, name));
        player.openInventory(menu.inventory);
    }

    private void beginNameInput(Player player) {
        if (!plugin.religionEnabled) return;
        if (religions.isReligionLeader(player.getUniqueId())) {
            player.sendMessage(text("creation_leader_blocked"));
            openIdeology(player);
            return;
        }
        managementInputs.remove(player.getUniqueId());
        long deadline = System.currentTimeMillis() + 60_000L;
        nameInputs.put(player.getUniqueId(), deadline);
        player.closeInventory();
        player.sendMessage(text("input"));
        player.sendMessage(text("input_rules"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (nameInputs.remove(player.getUniqueId(), deadline) && player.isOnline()) {
                player.sendMessage(text("input_expired"));
                openIdeology(player);
            }
        }, 1200L);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        ManagementInput management = managementInputs.remove(event.getPlayer().getUniqueId());
        if (management != null) {
            event.setCancelled(true);
            String value = event.getMessage().trim();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = event.getPlayer();
                if (!player.isOnline() || resetting || !player.hasPermission("wde.player")) return;
                if (System.currentTimeMillis() > management.deadline() || value.equalsIgnoreCase("cancel")) {
                    player.sendMessage(text("management.input_cancelled"));
                    management.cancel().run();
                } else if (!management.submit().test(value)) {
                    player.sendMessage(text(management.invalid()));
                    managementInput(player, management.cancel(), management.submit(), management.prompt(), management.invalid());
                }
            });
            return;
        }
        Long deadline = nameInputs.remove(event.getPlayer().getUniqueId());
        if (deadline == null) return;
        event.setCancelled(true);
        String name = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline() || !plugin.religionEnabled || !player.hasPermission("wde.player")) return;
            if (System.currentTimeMillis() > deadline) {
                player.sendMessage(text("input_expired"));
                openIdeology(player);
                return;
            }
            if (name.equalsIgnoreCase("cancel")) {
                player.sendMessage(text("input_cancelled"));
                openIdeology(player);
                return;
            }
            if (religions.isReligionLeader(player.getUniqueId())) {
                player.sendMessage(text("creation_leader_blocked"));
                openIdeology(player);
                return;
            }
            if (!name.matches("[\\p{L}\\p{N}_-]{2,24}")) {
                player.sendMessage(text("input_rules"));
                beginNameInput(player);
                return;
            }
            String quote = plugin.getEconomyBridge().format(plugin.getReligionCreationCostCharge());
            confirm(player, name, List.of(text("creation_cost", quote)), () -> openIdeology(player), () -> {
                if (!quote.equals(plugin.getEconomyBridge().format(plugin.getReligionCreationCostCharge()))) {
                    player.sendMessage(text("stale"));
                } else {
                    boolean existed = religions.religionExists(name);
                    executeReligion(player, "create", name);
                    if (!existed && religions.religionExists(name)) {
                        success(player);
                        openReligionDetail(player, name);
                        return;
                    }
                }
                openIdeology(player);
            });
        });
    }

    private void executeReligion(Player player, String... args) {
        String[] full = new String[args.length + 1];
        full[0] = "ideology";
        System.arraycopy(args, 0, full, 1, args.length);
        religionCommand.onCommand(player, plugin.getCommand("wde"), "wde", full);
    }

    private void confirm(Player player, String title, List<String> lore, Runnable cancel, Runnable apply) {
        Menu menu = menu(player, text("confirm"));
        button(menu, 22, Material.WRITTEN_BOOK, title, lore, null);
        button(menu, 30, Material.LIME_DYE, text("confirm_button"), List.of(), apply);
        button(menu, 32, Material.RED_DYE, text("cancel"), List.of(), cancel);
        player.openInventory(menu.inventory);
    }

    private void success(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.3f);
    }

    private Menu menu(Player player, String title) {
        return new Menu(player.getUniqueId(), title);
    }

    private void button(Menu menu, int slot, Material material, String title, List<String> lore, Runnable action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', title));
        List<String> lines = new ArrayList<>();
        for (String line : lore) {
            for (String paragraph : line.split("\\R")) {
                // Wrap long descriptions so custom stories remain readable in a tooltip.
                String colored = ChatColor.translateAlternateColorCodes('&', paragraph);
                int offset = 0;
                String carry = "";
                while (offset < colored.length()) {
                    int end = Math.min(offset + 32, colored.length());
                    if (end < colored.length() && (colored.charAt(end - 1) == ChatColor.COLOR_CHAR
                            || Character.isHighSurrogate(colored.charAt(end - 1)))) end--;
                    String part = colored.substring(offset, end);
                    lines.add(ChatColor.GRAY + carry + part);
                    carry = ChatColor.getLastColors(carry + part);
                    offset = end;
                }
            }
        }
        meta.setLore(lines);
        item.setItemMeta(meta);
        menu.inventory.setItem(slot, item);
        if (action != null) menu.actions.put(slot, action);
    }

    private int paginate(Menu menu, int size, int requested, IntConsumer openPage) {
        int pages = Math.max(1, (size + 44) / 45);
        int page = Math.max(0, Math.min(requested, pages - 1));
        if (size == 0) button(menu, 22, Material.GRAY_DYE, text("empty"), List.of(), null);
        if (page > 0) button(menu, 45, Material.ARROW, text("previous"), List.of(), () -> openPage.accept(page - 1));
        button(menu, 50, Material.PAPER, text("page", page + 1, pages), List.of(), null);
        if (page + 1 < pages) button(menu, 53, Material.ARROW, text("next"), List.of(), () -> openPage.accept(page + 1));
        return page * 45;
    }

    private void back(Menu menu, Runnable action) {
        button(menu, 49, Material.ARROW, text("back"), List.of(), action);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !menu.owner.equals(player.getUniqueId())
                || menu.busy || event.getRawSlot() < 0 || event.getRawSlot() >= menu.inventory.getSize()
                || !(event.isLeftClick() || event.isRightClick())) return;
        Runnable action = menu.actions.get(event.getRawSlot());
        if (action == null) return;
        menu.busy = true;
        // Inventory transitions must occur after the click event; each view submits at most once per tick.
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!player.hasPermission("wde.player")) {
                    player.sendMessage(plugin.getLocaleMessage("command_no_permission"));
                    player.closeInventory();
                    return;
                }
                if (player.isOnline() && player.getOpenInventory().getTopInventory() == menu.inventory) action.run();
            } finally {
                menu.busy = false;
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (resetting || !(event.getInventory().getHolder() instanceof Menu)
                || !(event.getPlayer() instanceof Player player)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !nameInputs.containsKey(player.getUniqueId())
                    && !managementInputs.containsKey(player.getUniqueId())
                    && backgrounds.shouldLockMovement(player)
                    && player.getOpenInventory().getTopInventory().getType() == InventoryType.CRAFTING) {
                openBackground(player);
            }
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        nameInputs.remove(event.getPlayer().getUniqueId());
        managementInputs.remove(event.getPlayer().getUniqueId());
        violationNotices.remove(event.getPlayer().getUniqueId());
    }

    /**
     * 绑定箱子界面与槽位操作，使用独立持有者识别本插件菜单。
     */
    private static final class Menu implements InventoryHolder {
        private final UUID owner;
        private final Inventory inventory;
        private final Map<Integer, Runnable> actions = new HashMap<>();
        private boolean busy;

        private Menu(UUID owner, String title) {
            this.owner = owner;
            inventory = Bukkit.createInventory(this, 54, title);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
