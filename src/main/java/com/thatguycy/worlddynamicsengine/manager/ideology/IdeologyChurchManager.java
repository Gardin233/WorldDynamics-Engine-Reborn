package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.manager.economy.EconomyBridge;
import com.thatguycy.worlddynamicsengine.manager.governance.ResidentManager;
import com.thatguycy.worlddynamicsengine.manager.storage.DataStorageManager;
import com.thatguycy.worlddynamicsengine.model.IdeologyCouncilProposal;
import com.thatguycy.worlddynamicsengine.model.WDEChurch;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import com.thatguycy.worlddynamicsengine.util.TextColors;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** 持久化管理共议制教会、神圣大会议案、制裁与治理改革。 */
public final class IdeologyChurchManager {
    private static final String CHURCHES_CATEGORY = "ideology_churches";
    private static final String PROPOSALS_CATEGORY = "ideology_council_proposals";
    private static final long DAY_MILLIS = TimeUnit.DAYS.toMillis(1);

    private final WorldDynamicsEngine plugin;
    private final DataStorageManager storage;
    private final ReligionManager religions;
    private final ResidentManager residents;
    private final Map<UUID, WDEChurch> churches = new LinkedHashMap<>();
    private final Map<UUID, IdeologyCouncilProposal> proposals = new LinkedHashMap<>();

    public IdeologyChurchManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        storage = plugin.getDataStorageManager();
        religions = plugin.getReligionManager();
        residents = plugin.getResidentManager();
        loadAll();
    }

    public synchronized void loadAll() {
        churches.clear();
        for (Map.Entry<String, WDEChurch> entry : storage.loadStringKeyed(CHURCHES_CATEGORY, WDEChurch.class).entrySet()) {
            try {
                churches.put(UUID.fromString(entry.getKey()), entry.getValue());
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipped church with invalid UUID key: " + entry.getKey());
            }
        }
        proposals.clear();
        for (Map.Entry<String, IdeologyCouncilProposal> entry
                : storage.loadStringKeyed(PROPOSALS_CATEGORY, IdeologyCouncilProposal.class).entrySet()) {
            try {
                proposals.put(UUID.fromString(entry.getKey()), entry.getValue());
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipped council proposal with invalid UUID key: " + entry.getKey());
            }
        }
    }

    public synchronized void saveAll() {
        storage.saveStringKeyed(CHURCHES_CATEGORY, stringKeyedChurches());
        storage.saveStringKeyed(PROPOSALS_CATEGORY, stringKeyedProposals());
    }

    public void startTasks() {
        new BukkitRunnable() {
            @Override
            public void run() {
                processDueProposals();
            }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60);
    }

    public synchronized boolean isChurchHead(UUID playerUuid) {
        return playerUuid != null && churches.values().stream()
                .filter(church -> church.isActive() && playerUuid.equals(church.getHeadUuid()))
                .anyMatch(this::isEligibleChurchHead);
    }

    public synchronized WDEChurch getChurch(UUID churchUuid) {
        return churchUuid == null ? null : churches.get(churchUuid);
    }

    public synchronized IdeologyCouncilProposal getProposal(UUID proposalUuid) {
        return proposalUuid == null ? null : proposals.get(proposalUuid);
    }

    public synchronized List<WDEChurch> getChurches(String ideologyName) {
        WDEreligion ideology = religions.getReligion(ideologyName);
        if (ideology == null) return List.of();
        return churches.values().stream()
                .filter(church -> church.getSourceIdeologyUuid().equals(ideology.getIdeologyUuid()))
                .sorted(Comparator.comparing(WDEChurch::getChurchName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public synchronized List<IdeologyCouncilProposal> getProposals(String ideologyName, boolean pendingOnly) {
        WDEreligion ideology = religions.getReligion(ideologyName);
        if (ideology == null) return List.of();
        return proposals.values().stream()
                .filter(proposal -> proposal.getSourceIdeologyUuid().equals(ideology.getIdeologyUuid()))
                .filter(proposal -> !pendingOnly || proposal.isPending()
                        || proposal.getState() == IdeologyCouncilProposal.State.APPROVED)
                .sorted(Comparator.comparingLong(IdeologyCouncilProposal::getCreatedAt).reversed())
                .toList();
    }

    public synchronized WDEChurch getActiveChurchForResident(WDEresident resident, WDEreligion ideology) {
        if (resident == null || ideology == null || resident.getChurchUuid() == null) return null;
        WDEChurch church = churches.get(resident.getChurchUuid());
        if (church == null || !church.isActive()
                || !church.getSourceIdeologyUuid().equals(ideology.getIdeologyUuid())
                || !church.isMember(resident.getUserUUID())
                || !ideology.getReligionName().equalsIgnoreCase(resident.getReligionName())) {
            return null;
        }
        return church;
    }

    public synchronized void onResidentIdeologyChanged(UUID residentUuid) {
        if (residentUuid == null) return;
        WDEresident resident = residents.getResident(residentUuid);
        for (WDEChurch church : new ArrayList<>(churches.values())) {
            if (!church.isActive() || !church.isMember(residentUuid)) continue;
            boolean stillMember = resident != null
                    && church.getChurchUuid().equals(resident.getChurchUuid())
                    && church.getSourceIdeologyName().equalsIgnoreCase(resident.getReligionName());
            if (stillMember) continue;
            if (church.getHeadUuid().equals(residentUuid)) {
                plugin.getLogger().severe("Church head " + residentUuid
                        + " lost source ideology membership outside the church governance flow; "
                        + "the church is retained for administrator recovery.");
                continue;
            }
            WDEChurch updated = copy(church);
            updated.removeMember(residentUuid, System.currentTimeMillis());
            persistChurch(updated);
        }
    }

    public synchronized void enterSynodal(String ideologyName, UUID actorUuid, String actorName) {
        WDEreligion current = requireIdeology(ideologyName, WDEreligion.GovernanceMode.LIFETIME,
                WDEreligion.GovernanceMode.CARDINAL);
        if (actorUuid == null || !actorUuid.equals(current.getPontiffUuid())) {
            throw new IllegalArgumentException("Only the current ideology leader may establish synodal governance.");
        }
        if (activeChurches(current.getIdeologyUuid()).size() > 0) {
            throw new IllegalStateException("This ideology already has churches.");
        }
        WDEresident existing = residents.getResident(actorUuid);
        WDEresident founder = existing == null ? new WDEresident(actorUuid, actorName) : copy(existing);
        if (!current.getReligionName().equalsIgnoreCase(founder.getReligionName())) {
            throw new IllegalArgumentException("The former ideology leader must still be a follower.");
        }
        long now = System.currentTimeMillis();
        String name = current.getReligionName() + "_first";
        UUID churchUuid = UUID.randomUUID();
        WDEChurch firstChurch = new WDEChurch(churchUuid, current.getIdeologyUuid(),
                current.getReligionName(), name, actorUuid, actorName, now);
        WDEreligion updated = copy(current);
        BigDecimal inheritedTreasury = updated.getTreasuryBalance();
        if (inheritedTreasury.signum() > 0) {
            updated.debitGovernanceFee(inheritedTreasury);
            firstChurch.creditTreasury(inheritedTreasury, UUID.randomUUID(), actorUuid.toString(), now);
        }
        for (Map.Entry<String, String> cardinal : updated.getCardinals().entrySet()) {
            UUID cardinalUuid = parseUuid(cardinal.getKey());
            if (cardinalUuid == null) {
                updated.removeCardinalRecord(cardinal.getKey());
            } else {
                updated.revokeCardinal(cardinalUuid);
                updated.recordCardinalChange(cardinalUuid, cardinal.getValue(), false, now,
                        "SYNODAL_REFORM");
            }
        }
        updated.setGovernanceMode(WDEreligion.GovernanceMode.SYNODAL);
        updated.recordGovernanceChange(current.getGovernanceMode(),
                WDEreligion.GovernanceMode.SYNODAL, now);
        updated.closeElection();
        updated.setNextElection(Map.of());
        founder.setChurchUuid(churchUuid, now);
        commit(
                List.of(
                        write("religions", updated.getReligionName().toLowerCase(Locale.ROOT), updated),
                        write(CHURCHES_CATEGORY, churchUuid.toString(), firstChurch),
                        write("residents", actorUuid.toString(), founder)),
                List.of(updated), List.of(firstChurch), List.of(), List.of(founder));
    }

    public synchronized void createChurch(Player player, String ideologyName, String churchName) {
        WDEreligion ideology = requireSynodal(ideologyName);
        WDEresident member = requireFollower(ideology, player);
        if (member.getChurchUuid() != null) {
            throw new IllegalArgumentException("Leave the current church before founding another.");
        }
        requireMembershipCooldown(member);
        validateChurchName(churchName);
        if (activeChurches(ideology.getIdeologyUuid()).size() >= config().getInt("churches.maximumPerIdeology", 32)) {
            throw new IllegalArgumentException("The ideology has reached its church limit.");
        }
        if (churches.values().stream().anyMatch(church -> church.isActive()
                && church.getSourceIdeologyUuid().equals(ideology.getIdeologyUuid())
                && church.getChurchName().equalsIgnoreCase(churchName))) {
            throw new IllegalArgumentException("A church with that name already exists.");
        }
        EconomyBridge economy = requireEconomy();
        EconomyBridge.Charge fee = churchCreationCharge(economy);
        if (!economy.has(player, fee) || !economy.withdraw(player, fee).success()) {
            throw new IllegalArgumentException("Insufficient funds to found a church.");
        }
        long now = System.currentTimeMillis();
        UUID churchUuid = UUID.randomUUID();
        WDEChurch church = new WDEChurch(churchUuid, ideology.getIdeologyUuid(),
                ideology.getReligionName(), churchName, player.getUniqueId(), player.getName(), now);
        WDEresident updatedMember = copy(member);
        updatedMember.setChurchUuid(churchUuid, now);
        try {
            commit(List.of(
                            write(CHURCHES_CATEGORY, churchUuid.toString(), church),
                            write("residents", updatedMember.getUserUUID().toString(), updatedMember)),
                    List.of(), List.of(church), List.of(), List.of(updatedMember));
        } catch (RuntimeException ex) {
            refund(economy, player, fee);
            throw ex;
        }
    }

    public synchronized void joinChurch(Player player, String ideologyName, String churchToken) {
        WDEreligion ideology = requireSynodal(ideologyName);
        WDEresident resident = requireFollower(ideology, player);
        WDEChurch target = findChurch(ideology, churchToken);
        if (target == null || !target.isActive()) throw new IllegalArgumentException("Active church not found.");
        if (target.isMember(player.getUniqueId())) throw new IllegalArgumentException("You already belong to that church.");
        requireMembershipCooldown(resident);

        List<WDEChurch> changedChurches = new ArrayList<>();
        WDEChurch previous = resident.getChurchUuid() == null ? null : churches.get(resident.getChurchUuid());
        if (previous != null && previous.isActive()) {
            if (previous.getHeadUuid().equals(player.getUniqueId())) {
                throw new IllegalArgumentException("The church head must abdicate before changing churches.");
            }
            WDEChurch oldUpdated = copy(previous);
            oldUpdated.removeMember(player.getUniqueId(), System.currentTimeMillis());
            changedChurches.add(oldUpdated);
        }
        long now = System.currentTimeMillis();
        WDEChurch targetUpdated = copy(target);
        targetUpdated.addMember(player.getUniqueId(), player.getName(), now);
        changedChurches.add(targetUpdated);
        WDEresident updatedResident = copy(resident);
        updatedResident.setChurchUuid(target.getChurchUuid(), now);
        commit(buildChurchResidentWrites(changedChurches, List.of(updatedResident)),
                List.of(), changedChurches, List.of(), List.of(updatedResident));
    }

    public synchronized void leaveChurch(Player player, String ideologyName) {
        WDEreligion ideology = requireSynodal(ideologyName);
        WDEresident resident = requireFollower(ideology, player);
        WDEChurch current = getActiveChurchForResident(resident, ideology);
        if (current == null) throw new IllegalArgumentException("You do not belong to an active church.");
        if (current.getHeadUuid().equals(player.getUniqueId())) {
            throw new IllegalArgumentException("The church head must abdicate before leaving.");
        }
        requireMembershipCooldown(resident);
        long now = System.currentTimeMillis();
        WDEChurch updatedChurch = copy(current);
        updatedChurch.removeMember(player.getUniqueId(), now);
        WDEresident updatedResident = copy(resident);
        updatedResident.setChurchUuid(null, now);
        commit(buildChurchResidentWrites(List.of(updatedChurch), List.of(updatedResident)),
                List.of(), List.of(updatedChurch), List.of(), List.of(updatedResident));
    }

    public synchronized void abdicateChurch(Player player, String ideologyName, String churchToken, String targetToken) {
        WDEreligion ideology = requireSynodal(ideologyName);
        WDEChurch current = findChurch(ideology, churchToken);
        if (current == null || !current.isActive()
                || !current.getHeadUuid().equals(player.getUniqueId())) {
            throw new IllegalArgumentException("Only this active church's eligible head may abdicate.");
        }
        WDEresident successor = resolveFollower(current, targetToken, ideology);
        if (successor == null || !current.isMember(successor.getUserUUID())) {
            throw new IllegalArgumentException("The successor must be a member of this church.");
        }
        if (successor.getUserUUID().equals(player.getUniqueId())) {
            throw new IllegalArgumentException("You are already this church's head.");
        }
        long now = System.currentTimeMillis();
        requireGovernanceActionCooldown(current, now);
        WDEChurch updated = copy(current);
        updated.transferHead(successor.getUserUUID(), successor.getUsername(), now);
        commit(List.of(write(CHURCHES_CATEGORY, updated.getChurchUuid().toString(), updated)),
                List.of(), List.of(updated), List.of(), List.of());
    }

    public synchronized void disbandChurch(Player player, String ideologyName, String churchToken) {
        WDEreligion ideology = requireSynodal(ideologyName);
        WDEChurch current = findChurch(ideology, churchToken);
        if (current == null || !current.isActive()
                || !current.getHeadUuid().equals(player.getUniqueId())) {
            throw new IllegalArgumentException("Only this active church's eligible head may dissolve it.");
        }
        if (activeChurches(ideology.getIdeologyUuid()).size() <= 1) {
            throw new IllegalArgumentException("The last active church cannot be dissolved while the ideology is synodal.");
        }
        if (current.getTreasuryBalance().signum() != 0) {
            throw new IllegalArgumentException("Withdraw the church treasury before dissolution.");
        }
        long now = System.currentTimeMillis();
        requireGovernanceActionCooldown(current, now);
        WDEChurch updated = copy(current);
        updated.markGovernanceAction(now);
        updated.dissolve(now);
        List<WDEresident> detached = new ArrayList<>();
        for (String uuidString : current.getMembers().keySet()) {
            UUID uuid = parseUuid(uuidString);
            WDEresident resident = uuid == null ? null : residents.getResident(uuid);
            if (resident != null && current.getChurchUuid().equals(resident.getChurchUuid())) {
                WDEresident changed = copy(resident);
                changed.setChurchUuid(null, now);
                detached.add(changed);
            }
        }
        commit(buildChurchResidentWrites(List.of(updated), detached),
                List.of(), List.of(updated), List.of(), detached);
    }

    public synchronized void splitChurch(Player player, String ideologyName, String churchToken, String newIdeologyName) {
        WDEreligion source = requireSynodal(ideologyName);
        WDEChurch church = findChurch(source, churchToken);
        if (church == null || !church.isActive() || !player.getUniqueId().equals(church.getHeadUuid())) {
            throw new IllegalArgumentException("Only an active church head may lead a schism.");
        }
        if (activeChurches(source.getIdeologyUuid()).size() <= 1) {
            throw new IllegalArgumentException("The last active church cannot secede.");
        }
        if (!newIdeologyName.matches("[\\p{L}\\p{N}_-]{2,24}") || religions.religionExists(newIdeologyName)) {
            throw new IllegalArgumentException("The new ideology name is invalid or already in use.");
        }
        if (religions.getLedIdeology(player.getUniqueId()) != null) {
            throw new IllegalArgumentException("The church head already leads another ideology.");
        }

        List<WDEresident> members = currentChurchMembers(church, source);
        if (members.stream().noneMatch(member -> member.getUserUUID().equals(player.getUniqueId()))) {
            throw new IllegalArgumentException("The church head must remain an ideology follower.");
        }
        long now = System.currentTimeMillis();
        UUID operationUuid = UUID.randomUUID();
        WDEreligion heresy = new WDEreligion(newIdeologyName, player.getUniqueId(), player.getName(), now);
        heresy.setGovernanceMode(WDEreligion.GovernanceMode.LIFETIME);
        heresy.setParentage(source.getIdeologyUuid(), church.getChurchUuid());
        heresy.setTithePolicy(source.getTitheType(), source.getTitheAmount());
        heresy.setMemes(source.getMemes());
        copyDoctrineSnapshot(source, heresy);
        WDEChurch seceded = copy(church);
        BigDecimal secededTreasury = seceded.getTreasuryBalance();
        if (secededTreasury.signum() > 0) heresy.creditTreasury(secededTreasury);
        seceded.markSchism(operationUuid, secededTreasury, now);

        List<WDEresident> transferred = new ArrayList<>();
        for (WDEresident member : members) {
            WDEresident changed = copy(member);
            changed.setReligionName(newIdeologyName);
            changed.setReligionChosen(true);
            changed.setChurchUuid(null, now);
            transferred.add(changed);
        }
        List<DataStorageManager.EntityWrite> writes = new ArrayList<>();
        writes.add(write("religions", heresy.getReligionName().toLowerCase(Locale.ROOT), heresy));
        writes.add(write(CHURCHES_CATEGORY, seceded.getChurchUuid().toString(), seceded));
        for (WDEresident resident : transferred) writes.add(write("residents", resident.getUserUUID().toString(), resident));
        commit(writes, List.of(heresy), List.of(seceded), List.of(), transferred);
        broadcast("religion_broadcast_created", heresy.getReligionName(), player.getName());
    }

    public synchronized UUID submitProposal(Player player, String ideologyName,
            IdeologyCouncilProposal.Type type, String targetChurchToken,
            Map<String, Object> payload, String reason) {
        WDEreligion ideology = requireSynodal(ideologyName);
        boolean appeal = type == IdeologyCouncilProposal.Type.APPEAL_SANCTION;
        WDEChurch proposerChurch = requireHead(ideology, player.getUniqueId(), true);
        validateProposal(ideology, proposerChurch, type, targetChurchToken, payload, reason);
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0L, config().getLong("council.proposalCooldownHours", 12L))
                * TimeUnit.HOURS.toMillis(1);
        if (now - proposerChurch.getLastProposalAt() < cooldown) {
            throw new IllegalArgumentException("This church is still under proposal cooldown.");
        }
        long durationDays = Math.max(1, config().getInt("council.proposalDurationDays", 7));
        UUID proposalUuid = UUID.randomUUID();
        IdeologyCouncilProposal proposal = new IdeologyCouncilProposal(
                proposalUuid, ideology.getIdeologyUuid(), ideology.getReligionName(),
                proposerChurch.getChurchUuid(), player.getUniqueId(), player.getName(), type,
                parseTargetChurch(ideology, targetChurchToken), payload, reason,
                now, Math.addExact(now, Math.multiplyExact(durationDays, DAY_MILLIS)));
        WDEChurch updatedChurch = copy(proposerChurch);
        if (appeal) updatedChurch.appealSanction(reason, now);
        updatedChurch.markProposalCreated(now);
        commit(List.of(
                        write(CHURCHES_CATEGORY, updatedChurch.getChurchUuid().toString(), updatedChurch),
                        write(PROPOSALS_CATEGORY, proposalUuid.toString(), proposal)),
                List.of(), List.of(updatedChurch), List.of(proposal), List.of());
        return proposalUuid;
    }

    public synchronized void vote(Player player, UUID proposalUuid, String choice) {
        IdeologyCouncilProposal current = proposals.get(proposalUuid);
        if (current == null || !current.isPending()) throw new IllegalArgumentException("Pending proposal not found.");
        long now = System.currentTimeMillis();
        if (current.getDeadlineAt() <= now) {
            throw new IllegalArgumentException("The council proposal voting deadline has passed.");
        }
        WDEreligion ideology = religions.getReligion(current.getSourceIdeologyName());
        if (ideology == null || !ideology.getIdeologyUuid().equals(current.getSourceIdeologyUuid())
                || ideology.getGovernanceMode() != WDEreligion.GovernanceMode.SYNODAL) {
            throw new IllegalArgumentException("The proposal's source ideology is not in synodal governance.");
        }
        WDEresident resident = residents.getResident(player.getUniqueId());
        WDEChurch voterChurch = getActiveChurchForResident(resident, ideology);
        if (voterChurch == null || !voterChurch.getHeadUuid().equals(player.getUniqueId())
                || voterChurch.isSanctioned(now)) {
            throw new IllegalArgumentException("Only unsanctioned active church heads may vote.");
        }
        IdeologyCouncilProposal updated = copy(current);
        updated.castVote(voterChurch.getChurchUuid(), choice.toUpperCase(Locale.ROOT));
        storage.saveEntity(PROPOSALS_CATEGORY, updated.getProposalUuid().toString(), updated);
        proposals.put(updated.getProposalUuid(), updated);
    }

    public synchronized BigDecimal deposit(Player player, String ideologyName, String churchToken, BigDecimal amount) {
        WDEreligion ideology = requireSynodal(ideologyName);
        WDEChurch current = findChurch(ideology, churchToken);
        if (current == null || !current.isActive() || !current.isMember(player.getUniqueId())) {
            throw new IllegalArgumentException("Only an active church member may donate to its treasury.");
        }
        validateAmount(amount);
        EconomyBridge economy = requireEconomy();
        EconomyBridge.Charge charge = economy.charge(amount, economy.getDefaultCurrency());
        if (!economy.has(player, charge) || !economy.withdraw(player, charge).success()) {
            throw new IllegalArgumentException("Insufficient personal funds.");
        }
        WDEChurch updated = copy(current);
        updated.creditTreasury(amount, UUID.randomUUID(), player.getUniqueId().toString(),
                System.currentTimeMillis());
        try {
            persistChurch(updated);
            return updated.getTreasuryBalance();
        } catch (RuntimeException ex) {
            refund(economy, player, charge);
            throw ex;
        }
    }

    public synchronized BigDecimal withdraw(Player player, String ideologyName, String churchToken, BigDecimal amount) {
        WDEreligion ideology = requireSynodal(ideologyName);
        WDEChurch current = findChurch(ideology, churchToken);
        if (current == null || !current.isActive() || !current.getHeadUuid().equals(player.getUniqueId())) {
            throw new IllegalArgumentException("Only this church's head may withdraw its treasury.");
        }
        validateAmount(amount);
        EconomyBridge economy = requireEconomy();
        UUID transactionUuid = UUID.randomUUID();
        WDEChurch updated = copy(current);
        updated.debitTreasury(amount, transactionUuid, player.getUniqueId().toString(), System.currentTimeMillis());
        persistChurch(updated);
        EconomyBridge.Charge payout = economy.charge(amount, economy.getDefaultCurrency());
        EconomyBridge.TransactionResult result = economy.depositExact(
                player.getUniqueId(), player.getWorld().getName(), payout);
        if (!result.success()) {
            WDEChurch rollback = copy(updated);
            rollback.creditTreasury(amount, transactionUuid, player.getUniqueId().toString(),
                    System.currentTimeMillis());
            try {
                persistChurch(rollback);
            } catch (RuntimeException compensationFailure) {
                plugin.getLogger().severe("CRITICAL: Church treasury payout rollback failed; manual reconciliation "
                        + "is required. transaction=" + transactionUuid + ", church=" + current.getChurchUuid()
                        + ", amount=" + amount.toPlainString() + ", player=" + player.getUniqueId());
                throw new IllegalStateException("Wallet payout and treasury rollback failed; reconcile transaction "
                        + transactionUuid + ".", compensationFailure);
            }
            throw new IllegalStateException("Wallet payout failed; the church treasury was restored. transaction="
                    + transactionUuid);
        }
        return updated.getTreasuryBalance();
    }

    public synchronized BigDecimal creditTithe(WDEChurch church, BigDecimal amount, UUID residentUuid) {
        if (church == null || !church.isActive() || amount == null || amount.signum() <= 0) return BigDecimal.ZERO;
        WDEChurch current = churches.get(church.getChurchUuid());
        if (current == null || !current.isActive()) throw new IllegalStateException("Church is no longer active.");
        WDEChurch updated = copy(current);
        updated.creditTreasury(amount, UUID.randomUUID(), residentUuid.toString(), System.currentTimeMillis());
        persistChurch(updated);
        return updated.getTreasuryBalance();
    }

    private void validateProposal(WDEreligion ideology, WDEChurch proposer, IdeologyCouncilProposal.Type type,
            String targetToken, Map<String, Object> payload, String reason) {
        if (type == null || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Every council proposal requires a type and written reason.");
        }
        Map<String, Object> values = payload == null ? Map.of() : payload;
        switch (type) {
            case SET_MEMES -> validateMemePayload(ideology, values);
            case SET_DOCTRINE -> validateDoctrinePayload(ideology, values);
            case SET_TITHE -> {
                try {
                    WDEreligion.validateTithePolicy(
                            WDEreligion.TitheType.valueOf(String.valueOf(values.get("titheType")).toUpperCase(Locale.ROOT)),
                            new BigDecimal(String.valueOf(values.get("titheAmount"))));
                } catch (RuntimeException ex) {
                    throw new IllegalArgumentException("Invalid council tithe proposal.");
                }
            }
            case SANCTION_CHURCH -> {
                WDEChurch target = requireOtherActiveChurch(ideology, targetToken, proposer);
                int days = readPayloadInt(values.get("durationDays"), -1);
                if (days < 1 || days > config().getInt("council.sanctionMaximumDays", 90)
                        || target.isSanctioned(System.currentTimeMillis())) {
                    throw new IllegalArgumentException("Invalid sanction duration or target state.");
                }
            }
            case LIFT_SANCTION -> {
                WDEChurch target = findChurch(ideology, targetToken);
                if (target == null || !target.isActive() || !target.isSanctioned(System.currentTimeMillis())) {
                    throw new IllegalArgumentException("The target church has no active sanction to lift.");
                }
            }
            case APPEAL_SANCTION -> {
                if (!proposer.isSanctioned(System.currentTimeMillis())) {
                    throw new IllegalArgumentException("Only a sanctioned church may appeal.");
                }
            }
            case REFORM_UNION -> {
                String modeName = String.valueOf(values.get("governanceMode")).toUpperCase(Locale.ROOT);
                if (!modeName.equals("LIFETIME") && !modeName.equals("CARDINAL")) {
                    throw new IllegalArgumentException("A union reform must target LIFETIME or CARDINAL.");
                }
            }
        }
    }

    private void validateMemePayload(WDEreligion ideology, Map<String, Object> payload) {
        Object raw = payload.get("memes");
        if (!(raw instanceof Collection<?> collection) || collection.isEmpty() || collection.size() > 4) {
            throw new IllegalArgumentException("A meme proposal requires one to four registered memes.");
        }
        Set<String> selected = new LinkedHashSet<>();
        for (Object value : collection) {
            String meme = religions.normalizeMeme(String.valueOf(value));
            if (meme == null || !selected.add(meme)) {
                throw new IllegalArgumentException("Meme proposals must use distinct registered memes.");
            }
            String conflict = religions.findExclusiveMemeConflict(meme, selected);
            if (conflict != null && !conflict.equals(meme)) {
                throw new IllegalArgumentException("The meme proposal contains mutually exclusive memes.");
            }
        }
    }

    private void validateDoctrinePayload(WDEreligion ideology, Map<String, Object> payload) {
        String doctrineId = String.valueOf(payload.get("doctrineId"));
        String attitudeId = String.valueOf(payload.get("attitudeId"));
        if (religions.getDoctrine(doctrineId) == null
                || !religions.canUseDoctrineAttitude(ideology, doctrineId, attitudeId)) {
            throw new IllegalArgumentException("The proposed doctrine or attitude is unavailable.");
        }
    }

    private void processDueProposals() {
        long now = System.currentTimeMillis();
        for (IdeologyCouncilProposal proposal : new ArrayList<>(proposals.values())) {
            try {
                if (proposal.isPending() && proposal.getDeadlineAt() <= now) {
                    resolveProposal(proposal, now);
                }
                IdeologyCouncilProposal latest = proposals.get(proposal.getProposalUuid());
                if (latest != null && latest.getState() == IdeologyCouncilProposal.State.APPROVED) {
                    executeApprovedProposal(latest, now);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("Failed to process ideology council proposal "
                        + proposal.getProposalUuid() + ": " + ex.getMessage());
            }
        }
    }

    private synchronized void resolveProposal(IdeologyCouncilProposal proposal, long now) {
        WDEreligion ideology = religions.getReligion(proposal.getSourceIdeologyName());
        if (ideology == null || !ideology.getIdeologyUuid().equals(proposal.getSourceIdeologyUuid())
                || ideology.getGovernanceMode() != WDEreligion.GovernanceMode.SYNODAL) {
            markProposalFailed(proposal, now, "SOURCE_IDEOLOGY_UNAVAILABLE");
            return;
        }
        Map<UUID, Integer> eligibleWeights = new LinkedHashMap<>();
        for (WDEChurch church : activeChurches(ideology.getIdeologyUuid())) {
            if (!church.isSanctioned(now) && isEligibleChurchHead(church, ideology)) {
                int weight = churchWeight(church, ideology);
                if (weight > 0) eligibleWeights.put(church.getChurchUuid(), weight);
            }
        }
        int totalWeight = eligibleWeights.values().stream().mapToInt(Integer::intValue).sum();
        int castWeight = 0;
        int yesWeight = 0;
        int noWeight = 0;
        for (Map.Entry<String, String> vote : proposal.getVotes().entrySet()) {
            UUID churchUuid = parseUuid(vote.getKey());
            Integer weight = churchUuid == null ? null : eligibleWeights.get(churchUuid);
            if (weight == null) continue;
            castWeight += weight;
            if ("YES".equals(vote.getValue())) yesWeight += weight;
            else if ("NO".equals(vote.getValue())) noWeight += weight;
        }
        double quorumRatio = clampRatio(config().getDouble("council.quorumRatio", 0.5D));
        double approvalRatio = clampRatio(config().getDouble("council.approvalRatio", 0.5D));
        int quorumWeight = (int) Math.ceil(totalWeight * quorumRatio);
        boolean passed = totalWeight > 0 && castWeight >= quorumWeight
                && yesWeight > totalWeight * approvalRatio;
        IdeologyCouncilProposal updated = copy(proposal);
        if (passed) updated.approve(totalWeight, yesWeight, noWeight, now);
        else updated.resolve(IdeologyCouncilProposal.State.REJECTED, totalWeight, yesWeight,
                noWeight, now, castWeight < quorumWeight ? "QUORUM_NOT_MET" : "MOTION_NOT_PASSED");
        storage.saveEntity(PROPOSALS_CATEGORY, updated.getProposalUuid().toString(), updated);
        proposals.put(updated.getProposalUuid(), updated);
    }

    private synchronized void executeApprovedProposal(IdeologyCouncilProposal proposal, long now) {
        WDEreligion source = religions.getReligion(proposal.getSourceIdeologyName());
        if (source == null || !source.getIdeologyUuid().equals(proposal.getSourceIdeologyUuid())
                || source.getGovernanceMode() != WDEreligion.GovernanceMode.SYNODAL) {
            markProposalFailed(proposal, now, "SOURCE_IDEOLOGY_UNAVAILABLE");
            return;
        }
        WDEChurch proposerChurch = churches.get(proposal.getProposerChurchUuid());
        WDEresident proposer = residents.getResident(proposal.getProposerUuid());
        if (proposerChurch == null || !proposerChurch.isActive()
                || !proposerChurch.getHeadUuid().equals(proposal.getProposerUuid())
                || !proposerChurch.isMember(proposal.getProposerUuid())
                || proposer == null || proposer.getChurchUuid() == null
                || !proposerChurch.getChurchUuid().equals(proposer.getChurchUuid())
                || !source.getReligionName().equalsIgnoreCase(proposer.getReligionName())) {
            markProposalFailed(proposal, now, "PROPOSER_NO_LONGER_HEAD_OR_MEMBER");
            return;
        }
        try {
            switch (proposal.getType()) {
                case SET_MEMES, SET_DOCTRINE, SET_TITHE -> executeDefinitionProposal(source, proposal, now);
                case SANCTION_CHURCH, LIFT_SANCTION, APPEAL_SANCTION ->
                        executeSanctionProposal(source, proposal, now);
                case REFORM_UNION -> executeUnionReform(source, proposal, now);
            }
        } catch (IllegalArgumentException ex) {
            markProposalFailed(proposal, now, sanitizeOutcome(ex.getMessage()));
        }
    }

    private void executeDefinitionProposal(WDEreligion source, IdeologyCouncilProposal proposal, long now) {
        WDEreligion updatedSource = copy(source);
        Map<String, Object> payload = proposal.getPayload();
        switch (proposal.getType()) {
            case SET_MEMES -> {
                List<String> selected = stringList(payload.get("memes"));
                validateMemePayload(source, Map.of("memes", selected));
                updatedSource.setMemes(selected.stream().map(religions::normalizeMeme).toList());
                religions.initializeDoctrineAttitudes(updatedSource);
            }
            case SET_DOCTRINE -> {
                validateDoctrinePayload(source, payload);
                String result = religions.setDoctrineAttitude(updatedSource,
                        String.valueOf(payload.get("doctrineId")), String.valueOf(payload.get("attitudeId")));
                if (result == null) throw new IllegalArgumentException("DOCTRINE_NO_LONGER_AVAILABLE");
            }
            case SET_TITHE -> {
                WDEreligion.TitheType type = WDEreligion.TitheType.valueOf(
                        String.valueOf(payload.get("titheType")).toUpperCase(Locale.ROOT));
                updatedSource.setTithePolicy(type, new BigDecimal(String.valueOf(payload.get("titheAmount"))));
            }
            default -> throw new IllegalArgumentException("INVALID_PROPOSAL_TYPE");
        }
        IdeologyCouncilProposal completed = copy(proposal);
        completed.complete(now, "SOURCE_DEFINITION_UPDATED");
        commit(List.of(
                        write("religions", updatedSource.getReligionName().toLowerCase(Locale.ROOT), updatedSource),
                        write(PROPOSALS_CATEGORY, completed.getProposalUuid().toString(), completed)),
                List.of(updatedSource), List.of(), List.of(completed), List.of());
        if (proposal.getType() == IdeologyCouncilProposal.Type.SET_MEMES) {
            String names = String.join("、", updatedSource.getMemes().stream()
                    .map(religions::getMemeDisplayName).toList());
            broadcast("religion_broadcast_memes_updated", updatedSource.getReligionName(), names);
        }
    }

    private void executeSanctionProposal(WDEreligion source, IdeologyCouncilProposal proposal, long now) {
        UUID targetUuid = proposal.getType() == IdeologyCouncilProposal.Type.APPEAL_SANCTION
                ? proposal.getProposerChurchUuid() : proposal.getTargetChurchUuid();
        WDEChurch current = churches.get(targetUuid);
        if (current == null || !current.isActive()
                || !current.getSourceIdeologyUuid().equals(source.getIdeologyUuid())) {
            throw new IllegalArgumentException("TARGET_CHURCH_UNAVAILABLE");
        }
        WDEChurch updated = copy(current);
        Map<String, Object> payload = proposal.getPayload();
        switch (proposal.getType()) {
            case SANCTION_CHURCH -> {
                int days = readPayloadInt(payload.get("durationDays"), -1);
                if (days < 1 || days > config().getInt("council.sanctionMaximumDays", 90)
                        || updated.isSanctioned(now)) throw new IllegalArgumentException("SANCTION_TARGET_CHANGED");
                updated.imposeSanction(proposal.getReason(),
                        Math.addExact(now, Math.multiplyExact((long) days, DAY_MILLIS)), now,
                        proposal.getProposalUuid());
            }
            case LIFT_SANCTION, APPEAL_SANCTION -> {
                if (!updated.isSanctioned(now)) throw new IllegalArgumentException("SANCTION_ALREADY_EXPIRED");
                updated.liftSanction(proposal.getReason(), now, proposal.getProposalUuid());
            }
            default -> throw new IllegalArgumentException("INVALID_PROPOSAL_TYPE");
        }
        IdeologyCouncilProposal completed = copy(proposal);
        completed.complete(now, "SANCTION_STATE_UPDATED");
        commit(List.of(
                        write(CHURCHES_CATEGORY, updated.getChurchUuid().toString(), updated),
                        write(PROPOSALS_CATEGORY, completed.getProposalUuid().toString(), completed)),
                List.of(), List.of(updated), List.of(completed), List.of());
    }

    private void executeUnionReform(WDEreligion source, IdeologyCouncilProposal proposal, long now) {
        WDEreligion.GovernanceMode targetMode;
        try {
            targetMode = WDEreligion.GovernanceMode.valueOf(
                    String.valueOf(proposal.getPayload().get("governanceMode")).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("INVALID_TARGET_GOVERNANCE");
        }
        if (targetMode != WDEreligion.GovernanceMode.LIFETIME
                && targetMode != WDEreligion.GovernanceMode.CARDINAL) {
            throw new IllegalArgumentException("INVALID_TARGET_GOVERNANCE");
        }
        List<WDEChurch> active = activeChurches(source.getIdeologyUuid());
        WDEresident proposer = residents.getResident(proposal.getProposerUuid());
        if (active.isEmpty() || proposer == null
                || !source.getReligionName().equalsIgnoreCase(proposer.getReligionName())) {
            throw new IllegalArgumentException("REFORM_PROPOSER_NO_LONGER_ELIGIBLE");
        }
        Set<UUID> heads = new LinkedHashSet<>();
        for (WDEChurch church : active) {
            if (!isEligibleChurchHead(church, source) || !heads.add(church.getHeadUuid())) {
                throw new IllegalArgumentException("CHURCH_HEAD_NO_LONGER_ELIGIBLE");
            }
        }
        if (targetMode == WDEreligion.GovernanceMode.CARDINAL
                && heads.size() > religions.getCardinalSeatLimit(source)) {
            throw new IllegalArgumentException("CARDINAL_SEATS_INSUFFICIENT_FOR_CHURCH_HEADS");
        }
        WDEreligion updatedSource = copy(source);
        WDEreligion.GovernanceMode previousMode = updatedSource.getGovernanceMode();
        updatedSource.setGovernanceMode(targetMode);
        updatedSource.recordGovernanceChange(previousMode, targetMode, now);
        updatedSource.transferLeadership(proposal.getProposerUuid(), proposal.getProposerName(),
                "COUNCIL_UNION_REFORM", now);
        BigDecimal totalTreasury = BigDecimal.ZERO;
        List<WDEChurch> merged = new ArrayList<>();
        for (WDEChurch church : active) {
            WDEChurch updatedChurch = copy(church);
            BigDecimal balance = updatedChurch.getTreasuryBalance();
            totalTreasury = totalTreasury.add(balance);
            updatedChurch.markMerged(proposal.getProposalUuid(), balance, now);
            merged.add(updatedChurch);
            if (targetMode == WDEreligion.GovernanceMode.CARDINAL) {
                updatedSource.appointCardinal(church.getHeadUuid(), church.getHeadName());
                updatedSource.recordCardinalChange(church.getHeadUuid(), church.getHeadName(),
                        true, now, "COUNCIL_UNION_REFORM");
            }
        }
        if (totalTreasury.signum() > 0) updatedSource.creditTreasury(totalTreasury);
        IdeologyCouncilProposal completed = copy(proposal);
        completed.complete(now, "ALL_CHURCH_TREASURIES_MERGED");
        List<WDEresident> detached = new ArrayList<>();
        Set<UUID> mergedChurchIds = new LinkedHashSet<>();
        for (WDEChurch church : active) mergedChurchIds.add(church.getChurchUuid());
        for (WDEresident resident : residents.getAllResidents()) {
            if (resident.getChurchUuid() != null && mergedChurchIds.contains(resident.getChurchUuid())) {
                WDEresident updatedResident = copy(resident);
                updatedResident.setChurchUuid(null, now);
                detached.add(updatedResident);
            }
        }
        List<DataStorageManager.EntityWrite> writes = new ArrayList<>();
        writes.add(write("religions", updatedSource.getReligionName().toLowerCase(Locale.ROOT), updatedSource));
        writes.add(write(PROPOSALS_CATEGORY, completed.getProposalUuid().toString(), completed));
        for (WDEChurch church : merged) writes.add(write(CHURCHES_CATEGORY, church.getChurchUuid().toString(), church));
        for (WDEresident resident : detached) writes.add(write("residents", resident.getUserUUID().toString(), resident));
        commit(writes, List.of(updatedSource), merged, List.of(completed), detached);
    }

    private void markProposalFailed(IdeologyCouncilProposal proposal, long now, String outcome) {
        IdeologyCouncilProposal failed = copy(proposal);
        failed.fail(now, outcome);
        storage.saveEntity(PROPOSALS_CATEGORY, failed.getProposalUuid().toString(), failed);
        proposals.put(failed.getProposalUuid(), failed);
    }

    private void validateProposalSource(WDEreligion ideology) {
        if (ideology == null || ideology.getGovernanceMode() != WDEreligion.GovernanceMode.SYNODAL) {
            throw new IllegalArgumentException("The ideology is not governed synodally.");
        }
    }

    private WDEreligion requireSynodal(String ideologyName) {
        WDEreligion ideology = religions.getReligion(ideologyName);
        validateProposalSource(ideology);
        return ideology;
    }

    private WDEreligion requireIdeology(String name, WDEreligion.GovernanceMode first,
            WDEreligion.GovernanceMode second) {
        WDEreligion ideology = religions.getReligion(name);
        if (ideology == null || (ideology.getGovernanceMode() != first && ideology.getGovernanceMode() != second)) {
            throw new IllegalArgumentException("The ideology cannot enter synodal governance from its current state.");
        }
        return ideology;
    }

    private WDEChurch requireHead(WDEreligion ideology, UUID playerUuid, boolean allowSanctioned) {
        WDEresident resident = residents.getResident(playerUuid);
        WDEChurch church = getActiveChurchForResident(resident, ideology);
        if (church == null || !playerUuid.equals(church.getHeadUuid())
                || (!allowSanctioned && church.isSanctioned(System.currentTimeMillis()))) {
            throw new IllegalArgumentException("Only an eligible active church head may perform this action.");
        }
        return church;
    }

    private WDEresident requireFollower(WDEreligion ideology, Player player) {
        if (player == null) throw new IllegalArgumentException("A player is required.");
        WDEresident resident = residents.getResident(player.getUniqueId());
        if (resident == null || !ideology.getReligionName().equalsIgnoreCase(resident.getReligionName())) {
            throw new IllegalArgumentException("You must follow this ideology.");
        }
        return resident;
    }

    private WDEChurch findChurch(WDEreligion ideology, String token) {
        if (token == null || token.isBlank()) return null;
        UUID id = parseUuid(token);
        if (id != null) {
            WDEChurch church = churches.get(id);
            return church != null && church.getSourceIdeologyUuid().equals(ideology.getIdeologyUuid()) ? church : null;
        }
        return activeChurches(ideology.getIdeologyUuid()).stream()
                .filter(church -> church.getChurchName().equalsIgnoreCase(token))
                .findFirst().orElse(null);
    }

    private WDEChurch requireOtherActiveChurch(WDEreligion ideology, String token, WDEChurch proposer) {
        WDEChurch target = findChurch(ideology, token);
        if (target == null || !target.isActive() || target.getChurchUuid().equals(proposer.getChurchUuid())) {
            throw new IllegalArgumentException("The target must be another active church.");
        }
        return target;
    }

    private UUID parseTargetChurch(WDEreligion ideology, String token) {
        if (token == null || token.isBlank()) return null;
        WDEChurch church = findChurch(ideology, token);
        return church == null ? null : church.getChurchUuid();
    }

    private WDEresident resolveFollower(WDEChurch church, String token, WDEreligion ideology) {
        UUID uuid = parseUuid(token);
        if (uuid != null) {
            WDEresident resident = residents.getResident(uuid);
            return resident != null && ideology.getReligionName().equalsIgnoreCase(resident.getReligionName())
                    ? resident : null;
        }
        return residents.getAllResidents().stream()
                .filter(resident -> church.isMember(resident.getUserUUID()))
                .filter(resident -> resident.getUsername().equalsIgnoreCase(token))
                .filter(resident -> ideology.getReligionName().equalsIgnoreCase(resident.getReligionName()))
                .findFirst().orElse(null);
    }

    private List<WDEChurch> activeChurches(UUID ideologyUuid) {
        return churches.values().stream()
                .filter(WDEChurch::isActive)
                .filter(church -> church.getSourceIdeologyUuid().equals(ideologyUuid))
                .sorted(Comparator.comparing(church -> church.getChurchUuid().toString()))
                .toList();
    }

    private List<WDEresident> currentChurchMembers(WDEChurch church, WDEreligion ideology) {
        List<WDEresident> result = new ArrayList<>();
        for (String uuidString : church.getMembers().keySet()) {
            UUID uuid = parseUuid(uuidString);
            WDEresident resident = uuid == null ? null : residents.getResident(uuid);
            if (resident != null && church.getChurchUuid().equals(resident.getChurchUuid())
                    && ideology.getReligionName().equalsIgnoreCase(resident.getReligionName())) {
                result.add(resident);
            }
        }
        return result;
    }

    private boolean isEligibleFollower(WDEreligion ideology, UUID playerUuid) {
        if (playerUuid == null || ideology.isPlayerExcommunicated(playerUuid)) return false;
        WDEresident resident = residents.getResident(playerUuid);
        return resident != null && ideology.getReligionName().equalsIgnoreCase(resident.getReligionName());
    }

    private boolean isEligibleChurchHead(WDEChurch church) {
        WDEreligion ideology = religions.getReligion(church.getSourceIdeologyName());
        return ideology != null && ideology.getIdeologyUuid().equals(church.getSourceIdeologyUuid())
                && ideology.getGovernanceMode() == WDEreligion.GovernanceMode.SYNODAL
                && isEligibleChurchHead(church, ideology);
    }

    private boolean isEligibleChurchHead(WDEChurch church, WDEreligion ideology) {
        WDEresident resident = residents.getResident(church.getHeadUuid());
        return church.isActive() && resident != null && church.isMember(church.getHeadUuid())
                && church.getChurchUuid().equals(resident.getChurchUuid())
                && isEligibleFollower(ideology, church.getHeadUuid());
    }

    private int churchWeight(WDEChurch church, WDEreligion ideology) {
        if ("EQUAL_CHURCHES".equalsIgnoreCase(config().getString("council.voteWeighting", "MEMBERS"))) {
            return 1;
        }
        int weight = Math.max(1, currentChurchMembers(church, ideology).size());
        return Math.min(Math.max(1, config().getInt("council.maximumChurchWeight", 10000)), weight);
    }

    private void requireMembershipCooldown(WDEresident resident) {
        long cooldown = Math.max(0L, config().getLong("churches.membershipCooldownHours", 24L))
                * TimeUnit.HOURS.toMillis(1);
        if (System.currentTimeMillis() - resident.getChurchChangedAt() < cooldown) {
            throw new IllegalArgumentException("Church membership is still under cooldown.");
        }
    }

    private void requireGovernanceActionCooldown(WDEChurch church, long now) {
        long cooldown = Math.max(0L, config().getLong("churches.governanceActionCooldownHours", 24L))
                * TimeUnit.HOURS.toMillis(1);
        if (now - church.getLastGovernanceActionAt() < cooldown) {
            throw new IllegalArgumentException("This church is still under governance action cooldown.");
        }
    }

    private void validateChurchName(String name) {
        if (name == null || !name.matches("[\\p{L}\\p{N}_-]{2,32}")) {
            throw new IllegalArgumentException("Church names must contain 2-32 letters, digits, underscores, or hyphens.");
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 8
                || amount.compareTo(WDEreligion.MAX_FIXED_TITHE) > 0) {
            throw new IllegalArgumentException("Invalid church treasury amount.");
        }
    }

    private List<String> stringList(Object raw) {
        if (!(raw instanceof Collection<?> collection)) return List.of();
        return collection.stream().map(String::valueOf).toList();
    }

    private int readPayloadInt(Object raw, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double clampRatio(double ratio) {
        return Double.isFinite(ratio) ? Math.max(0D, Math.min(1D, ratio)) : 0.5D;
    }

    private String sanitizeOutcome(String raw) {
        if (raw == null || raw.isBlank()) return "PROPOSAL_NO_LONGER_VALID";
        return raw.trim().replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private void copyDoctrineSnapshot(WDEreligion source, WDEreligion target) {
        for (var doctrine : religions.getRegisteredDoctrines()) {
            String attitude = source.getDoctrineAttitude(doctrine.getId());
            if (attitude == null) attitude = religions.resolveDoctrineDefaultAttitude(source, doctrine);
            if (attitude != null) religions.setDoctrineAttitude(target, doctrine.getId(), attitude);
        }
    }

    private WDEChurch copy(WDEChurch church) {
        return WDEChurch.deserialize(church.serialize());
    }

    private WDEreligion copy(WDEreligion ideology) {
        return WDEreligion.deserialize(ideology.serialize());
    }

    private WDEresident copy(WDEresident resident) {
        return WDEresident.deserialize(resident.serialize());
    }

    private IdeologyCouncilProposal copy(IdeologyCouncilProposal proposal) {
        return IdeologyCouncilProposal.deserialize(proposal.serialize());
    }

    private UUID parseUuid(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private FileConfiguration config() {
        return plugin.getIdeologyConfig();
    }

    private void broadcast(String key, Object... values) {
        Bukkit.broadcastMessage(TextColors.translateAlternateColorCodes('&',
                plugin.formatLocaleMessage(key, values)));
    }

    private EconomyBridge requireEconomy() {
        EconomyBridge economy = plugin.getEconomyBridge();
        if (economy == null || !economy.hasProvider()) throw new IllegalStateException("Economy provider is unavailable.");
        return economy;
    }

    private EconomyBridge.Charge churchCreationCharge(EconomyBridge economy) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(config().getString("economy.governance.churchCreation.amount", "5000.00"));
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid church creation fee.", ex);
        }
        if (amount.signum() < 0 || amount.scale() > 8 || amount.compareTo(WDEreligion.MAX_FIXED_TITHE) > 0) {
            throw new IllegalStateException("Invalid church creation fee.");
        }
        return economy.charge(amount,
                config().getString("economy.governance.churchCreation.currency", economy.getDefaultCurrency()));
    }

    private void refund(EconomyBridge economy, Player player, EconomyBridge.Charge charge) {
        try {
            if (!economy.depositExact(player.getUniqueId(), player.getWorld().getName(), charge).success()) {
                plugin.getLogger().severe("Church creation fee refund failed for " + player.getUniqueId());
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Church creation fee refund failed for "
                    + player.getUniqueId() + ": " + ex.getMessage());
        }
    }

    private DataStorageManager.EntityWrite write(String category, String key,
            org.bukkit.configuration.serialization.ConfigurationSerializable value) {
        return new DataStorageManager.EntityWrite(category, key, value);
    }

    private List<DataStorageManager.EntityWrite> buildChurchResidentWrites(
            Collection<WDEChurch> changedChurches, Collection<WDEresident> changedResidents) {
        List<DataStorageManager.EntityWrite> writes = new ArrayList<>();
        for (WDEChurch church : changedChurches) {
            writes.add(write(CHURCHES_CATEGORY, church.getChurchUuid().toString(), church));
        }
        for (WDEresident resident : changedResidents) {
            writes.add(write("residents", resident.getUserUUID().toString(), resident));
        }
        return writes;
    }

    private void persistChurch(WDEChurch church) {
        storage.saveEntity(CHURCHES_CATEGORY, church.getChurchUuid().toString(), church);
        churches.put(church.getChurchUuid(), church);
    }

    private Map<String, WDEChurch> stringKeyedChurches() {
        Map<String, WDEChurch> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, WDEChurch> entry : churches.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue());
        }
        return result;
    }

    private Map<String, IdeologyCouncilProposal> stringKeyedProposals() {
        Map<String, IdeologyCouncilProposal> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, IdeologyCouncilProposal> entry : proposals.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue());
        }
        return result;
    }

    private void commit(Collection<DataStorageManager.EntityWrite> writes,
            Collection<WDEreligion> changedReligions,
            Collection<WDEChurch> changedChurches,
            Collection<IdeologyCouncilProposal> changedProposals,
            Collection<WDEresident> changedResidents) {
        storage.saveEntities(writes);
        for (WDEreligion ideology : changedReligions) religions.publishPersistedReligion(ideology);
        for (WDEChurch church : changedChurches) churches.put(church.getChurchUuid(), church);
        for (IdeologyCouncilProposal proposal : changedProposals) proposals.put(proposal.getProposalUuid(), proposal);
        residents.publishResidents(changedResidents);
    }
}
