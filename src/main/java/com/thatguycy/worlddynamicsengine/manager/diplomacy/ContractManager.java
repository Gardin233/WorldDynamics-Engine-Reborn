package com.thatguycy.worlddynamicsengine.manager.diplomacy;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.manager.governance.NationManager;
import com.thatguycy.worlddynamicsengine.manager.governance.ResidentManager;
import com.thatguycy.worlddynamicsengine.manager.governance.TownManager;
import com.thatguycy.worlddynamicsengine.model.ContractRecord;
import com.thatguycy.worlddynamicsengine.model.WDEnation;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.WDEtown;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/** Stores non-enforceable contract proposals and their signatures and decisions. */
public final class ContractManager {
    private static final String STORAGE_CATEGORY = "contracts";
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final int SIGNER_DISPLAY_LIMIT = 28;
    private static final int MAX_BODY_PAGES = 97;

    private final WorldDynamicsEngine plugin;
    private final Map<String, ContractRecord> contracts = new LinkedHashMap<>();
    private final Map<UUID, Draft> drafts = new LinkedHashMap<>();

    public ContractManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        contracts.putAll(plugin.getDataStorageManager().loadStringKeyed(STORAGE_CATEGORY, ContractRecord.class));
    }

    public synchronized boolean beginDraft(Player player, String fromTypeRaw, String toTypeRaw, String targetName) {
        String fromType = normalizeType(fromTypeRaw);
        String toType = normalizeType(toTypeRaw);
        if (!validPair(fromType, toType)) {
            tell(player, "contract_invalid_pair");
            return false;
        }
        if (!Material.WRITABLE_BOOK.equals(player.getInventory().getItemInMainHand().getType())) {
            tell(player, "contract_need_book");
            return false;
        }

        Party source = resolveSource(player, fromType, toType);
        if (source == null) {
            tell(player, "contract_source_unavailable");
            return false;
        }
        if ("TOWN".equals(fromType)) {
            Town town = TownyAPI.getInstance().getTown(player);
            if (town == null || !hasTownAuthority(player, town)) {
                tell(player, "contract_source_unavailable");
                return false;
            }
        }
        if ("NATION".equals(fromType) && "NATION".equals(toType)
                && !hasNationAuthority(player, source.key, true)) {
            tell(player, "contract_nation_authority");
            return false;
        }
        Party target = resolveTarget(player, toType, targetName);
        if (target == null || sameParty(source, target)) {
            tell(player, "contract_target_unavailable");
            return false;
        }

        drafts.put(player.getUniqueId(), new Draft(source, target));
        player.openBook(player.getInventory().getItemInMainHand());
        tell(player, "contract_edit_prompt", source.display, target.display);
        return true;
    }

    public synchronized void onBookEdit(PlayerEditBookEvent event) {
        if (!event.isSigning()) return;
        Player player = event.getPlayer();
        Draft draft = drafts.remove(player.getUniqueId());
        if (draft == null) return;

        BookMeta edited = event.getNewBookMeta();
        List<String> pages = new ArrayList<>(edited.getPages());
        if (pages.isEmpty() || pages.stream().allMatch(String::isBlank)) {
            event.setCancelled(true);
            tell(player, "contract_empty_body");
            return;
        }
        if (pages.size() > MAX_BODY_PAGES) {
            event.setCancelled(true);
            tell(player, "contract_too_many_pages", MAX_BODY_PAGES);
            return;
        }

        String id;
        do {
            id = UUID.randomUUID().toString().substring(0, 8);
        } while (contracts.containsKey(id));
        long now = System.currentTimeMillis();
        ContractRecord contract = new ContractRecord(id,
                draft.from.type, draft.from.key, draft.from.display,
                draft.to.type, draft.to.key, draft.to.display,
                player.getUniqueId(), player.getName(), now, pages);
        try {
            contracts.put(id, contract);
            save();
        } catch (RuntimeException ex) {
            contracts.remove(id);
            event.setCancelled(true);
            plugin.getLogger().severe("Could not save contract " + id + ": " + ex.getMessage());
            tell(player, "contract_storage_failed");
            return;
        }

        edited.setTitle(plugin.formatLocaleMessage(player.getLocale(), "contract_book_title", id));
        edited.setAuthor(plugin.getName());
        edited.setPages(renderPages(contract));
        event.setNewBookMeta(edited);
        tell(player, "contract_created", id, draft.to.display);
        notifyRecipient(contract);
    }

    public synchronized List<ContractRecord> pendingFor(Player player) {
        return contracts.values().stream()
                .filter(contract -> "PENDING".equals(contract.status) && isRecipient(player, contract))
                .sorted(Comparator.comparingLong(contract -> contract.createdAt))
                .toList();
    }

    public synchronized List<ContractRecord> visibleTo(Player player) {
        return contracts.values().stream()
                .filter(contract -> canView(player, contract))
                .sorted(Comparator.comparingLong((ContractRecord contract) -> contract.createdAt).reversed())
                .toList();
    }

    public synchronized ContractRecord get(String id) {
        if (id == null) return null;
        return contracts.values().stream().filter(contract -> contract.id.equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    public synchronized ItemStack openForViewing(Player player, ContractRecord contract) {
        if (!canView(player, contract)) return null;
        contract.viewedBy.add(player.getUniqueId().toString());
        try {
            save();
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Could not save view record for contract " + contract.id + ": " + ex.getMessage());
            contract.viewedBy.remove(player.getUniqueId().toString());
            tell(player, "contract_storage_failed");
            return null;
        }
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(plugin.formatLocaleMessage(player.getLocale(), "contract_book_title", contract.id));
        meta.setAuthor(plugin.getName());
        meta.setPages(renderPages(contract));
        book.setItemMeta(meta);
        return book;
    }

    public synchronized String decide(Player player, String id, boolean accepted) {
        ContractRecord contract = get(id);
        if (contract == null) return "contract_not_found";
        if (!"PENDING".equals(contract.status)) return "contract_already_decided";
        if (!isRecipient(player, contract)) return "contract_not_recipient";
        if (!contract.viewedBy.contains(player.getUniqueId().toString())) return "contract_view_first";

        contract.status = accepted ? "ACCEPTED" : "REJECTED";
        contract.decisionUuid = player.getUniqueId().toString();
        contract.decisionName = player.getName();
        contract.decidedAt = System.currentTimeMillis();
        try {
            save();
        } catch (RuntimeException ex) {
            contract.status = "PENDING";
            contract.decisionUuid = "";
            contract.decisionName = "";
            contract.decidedAt = 0L;
            plugin.getLogger().severe("Could not save decision for contract " + contract.id + ": " + ex.getMessage());
            return "contract_storage_failed";
        }
        notifyInitiator(contract);
        return accepted ? "contract_accepted" : "contract_rejected";
    }

    public String formatDate(long timestamp) {
        return DATE_FORMAT.format(Instant.ofEpochMilli(timestamp));
    }

    public String displayStatus(String status) {
        return plugin.getLocaleMessage("contract_status_" + status.toLowerCase(Locale.ROOT));
    }

    public boolean isRecipient(Player player, ContractRecord contract) {
        return isPartyMember(player, contract.toType, contract.toKey);
    }

    public boolean canView(Player player, ContractRecord contract) {
        return contract.initiatorUuid.equals(player.getUniqueId().toString())
                || isPartyMember(player, contract.fromType, contract.fromKey)
                || isPartyMember(player, contract.toType, contract.toKey);
    }

    private List<String> renderPages(ContractRecord contract) {
        List<String> pages = new ArrayList<>(contract.bodyPages);
        pages.add("");
        pages.add(plugin.formatLocaleMessage("contract_signature_line",
                formatDate(contract.createdAt), signer(contract.initiatorName, contract.fromDisplay)));
        if ("ACCEPTED".equals(contract.status)) {
            pages.add(plugin.formatLocaleMessage("contract_signature_line",
                    formatDate(contract.decidedAt), signer(contract.decisionName, contract.toDisplay)));
        } else if ("REJECTED".equals(contract.status)) {
            pages.add(plugin.formatLocaleMessage("contract_rejection_line",
                    formatDate(contract.decidedAt), signer(contract.decisionName, contract.toDisplay)));
        } else {
            pages.add(plugin.formatLocaleMessage("contract_pending_line", contract.toDisplay));
        }
        if (pages.size() > 100) return new ArrayList<>(pages.subList(0, 100));
        return pages;
    }

    private String signer(String playerName, String partyName) {
        String value = playerName + "（" + partyName + "）";
        return truncate(value, SIGNER_DISPLAY_LIMIT);
    }

    private String truncate(String value, int limit) {
        int count = value.codePointCount(0, value.length());
        if (count <= limit) return value;
        int end = value.offsetByCodePoints(0, Math.max(0, limit - 3));
        return value.substring(0, end) + "...";
    }

    private Party resolveSource(Player player, String type, String targetType) {
        if ("PLAYER".equals(type)) return new Party(type, player.getUniqueId().toString(), player.getName());
        if ("TOWN".equals(type)) {
            Town town = TownyAPI.getInstance().getTown(player);
            if (town == null) return null;
            return new Party(type, town.getUUID().toString(), town.getName());
        }
        if ("NATION".equals(type) && "NATION".equals(targetType)) {
            Nation nation = nationOf(player);
            if (nation == null) return null;
            return new Party(type, nation.getUUID().toString(), nation.getName());
        }
        return null;
    }

    private Party resolveTarget(Player player, String type, String name) {
        if (name == null || name.isBlank()) return null;
        try {
            if ("PLAYER".equals(type)) {
                Resident resident = TownyUniverse.getInstance().getResident(name);
                if (resident == null || resident.getUUID().equals(player.getUniqueId())) return null;
                return new Party(type, resident.getUUID().toString(), resident.getName());
            }
            if ("TOWN".equals(type)) {
                Town town = TownyUniverse.getInstance().getTown(name);
                return town == null ? null : new Party(type, town.getUUID().toString(), town.getName());
            }
            if ("NATION".equals(type)) {
                Nation nation = TownyUniverse.getInstance().getNation(name);
                return nation == null ? null : new Party(type, nation.getUUID().toString(), nation.getName());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean isPartyMember(Player player, String type, String key) {
        return switch (type) {
            case "PLAYER" -> player.getUniqueId().toString().equals(key);
            case "TOWN" -> {
                Town town = TownyAPI.getInstance().getTown(player);
                yield town != null && town.getUUID().toString().equalsIgnoreCase(key)
                        && hasTownAuthority(player, town);
            }
            case "NATION" -> {
                Nation nation = nationOf(player);
                yield nation != null && nation.getUUID().toString().equalsIgnoreCase(key)
                        && hasNationAuthority(player, nation.getName(), false);
            }
            default -> false;
        };
    }

    private boolean hasTownAuthority(Player player, Town town) {
        try {
            Resident resident = TownyUniverse.getInstance().getResident(player.getName());
            if (resident != null && town.isMayor(resident)) return true;
        } catch (Exception ignored) {
        }
        TownManager townManager = plugin.getTownManager();
        WDEtown data = townManager == null ? null : townManager.getTown(town.getName());
        return data != null && data.getTownLeader() != null && data.getTownLeader().equalsIgnoreCase(player.getName());
    }

    private boolean hasNationAuthority(Player player, String nationName, boolean nationToNationInitiation) {
        NationManager nationManager = plugin.getNationManager();
        ResidentManager residentManager = plugin.getResidentManager();
        WDEnation data = nationManager == null ? null : nationManager.getNation(nationName);
        Nation townyNation;
        try {
            townyNation = TownyUniverse.getInstance().getNation(nationName);
        } catch (Exception ignored) {
            return false;
        }
        if (data == null || townyNation == null) return false;
        Nation playerNation = nationOf(player);
        if (playerNation == null || !playerNation.getName().equalsIgnoreCase(nationName)) return false;

        WDEresident residentData = residentManager == null ? null : residentManager.getResident(player.getUniqueId());
        boolean diplomat = residentData != null && residentData.hasFlag("diplomat");
        if (nationToNationInitiation && "DEMOCRACY".equalsIgnoreCase(data.getGovernmentType())) {
            return diplomat;
        }
        boolean leader = data.getGovernmentLeader() != null
                && data.getGovernmentLeader().equalsIgnoreCase(player.getName());
        try {
            Resident resident = TownyUniverse.getInstance().getResident(player.getName());
            leader |= resident != null && townyNation.isKing(resident);
        } catch (Exception ignored) {
        }
        return leader || diplomat;
    }

    private Nation nationOf(Player player) {
        Town town = TownyAPI.getInstance().getTown(player);
        return town == null ? null : town.getNationOrNull();
    }

    private void notifyRecipient(ContractRecord contract) {
        if ("PLAYER".equals(contract.toType)) {
            Player receiver = plugin.getServer().getPlayer(UUID.fromString(contract.toKey));
            if (receiver != null) tell(receiver, "contract_received", contract.initiatorName, contract.id);
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isRecipient(player, contract)) tell(player, "contract_received", contract.fromDisplay, contract.id);
        }
    }

    private void notifyInitiator(ContractRecord contract) {
        Player initiator = plugin.getServer().getPlayer(UUID.fromString(contract.initiatorUuid));
        if (initiator != null) {
            tell(initiator, "contract_decision_notice", contract.id, displayStatus(contract.status), contract.decisionName);
        }
    }

    private boolean validPair(String from, String to) {
        return ("PLAYER".equals(from) && List.of("PLAYER", "TOWN", "NATION").contains(to))
                || ("TOWN".equals(from) && List.of("TOWN", "NATION").contains(to))
                || ("NATION".equals(from) && "NATION".equals(to));
    }

    private boolean sameParty(Party first, Party second) {
        return first.type.equals(second.type) && first.key.equalsIgnoreCase(second.key);
    }

    private String normalizeType(String value) {
        if (value == null) return "";
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "player", "玩家" -> "PLAYER";
            case "town", "城镇", "城鎮" -> "TOWN";
            case "nation", "国家", "國家" -> "NATION";
            default -> "";
        };
    }

    private void save() {
        plugin.getDataStorageManager().saveStringKeyed(STORAGE_CATEGORY, contracts);
    }

    private void tell(Player player, String key, Object... args) {
        player.sendMessage(plugin.formatLocaleMessage(player.getLocale(), key, args));
    }

    private record Party(String type, String key, String display) {}
    private record Draft(Party from, Party to) {}
}
