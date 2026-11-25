package com.thatguycy.worlddynamicsengine.integration.papi;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.manager.background.BackgroundManager;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.model.background.RoleBackgroundStage;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Exposes basic WDER player, culture, mood, and Towny values to PlaceholderAPI. */
public final class WDERMoodExpansion extends PlaceholderExpansion {
    private final WorldDynamicsEngine plugin;

    public WDERMoodExpansion(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "wder";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || params == null) return null;
        String key = params.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "mood" -> plugin.getMoodManager() == null
                    ? "50" : Integer.toString(plugin.getMoodManager().getMood(player.getUniqueId()));
            case "mood_expectation" -> moodExpectation(player);
            case "culture" -> cultureName(player);
            case "culture_leader" -> cultureLeader(player);
            case "culture_governance" -> cultureGovernance(player);
            case "culture_memes" -> cultureMemes(player);
            case "culture_meme_1" -> cultureMeme(player, 0);
            case "culture_meme_2" -> cultureMeme(player, 1);
            case "culture_meme_3" -> cultureMeme(player, 2);
            case "culture_meme_4" -> cultureMeme(player, 3);
            case "background_childhood" -> background(player, RoleBackgroundStage.CHILDHOOD);
            case "background_adulthood" -> background(player, RoleBackgroundStage.ADULTHOOD);
            case "town" -> townOrNation(player, false);
            case "nation" -> townOrNation(player, true);
            default -> null;
        };
    }

    private String moodExpectation(OfflinePlayer player) {
        Player online = player.getPlayer();
        return plugin.getMoodManager() == null || online == null
                ? "0" : Integer.toString(plugin.getMoodManager().getExpectationModifier(online));
    }

    private WDEresident resident(OfflinePlayer player) {
        return plugin.getResidentManager() == null
                ? null : plugin.getResidentManager().getResident(player.getUniqueId());
    }

    private WDEreligion culture(OfflinePlayer player) {
        WDEresident resident = resident(player);
        String name = cultureName(resident);
        if (name.isBlank() || plugin.getReligionManager() == null) {
            return null;
        }
        return plugin.getReligionManager().getReligion(name);
    }

    private String cultureName(OfflinePlayer player) {
        return cultureName(resident(player));
    }

    private String cultureName(WDEresident resident) {
        if (resident == null) return "";
        String name = resident.getReligionName();
        return name == null || name.isBlank() || "none".equalsIgnoreCase(name) ? "" : name;
    }

    private String cultureLeader(OfflinePlayer player) {
        WDEreligion culture = culture(player);
        return culture == null ? "" : culture.getPontiffName();
    }

    private String cultureGovernance(OfflinePlayer player) {
        WDEreligion culture = culture(player);
        if (culture == null || culture.getGovernanceMode() == null) return "";
        String key = "religion_governance_" + culture.getGovernanceMode().name().toLowerCase(Locale.ROOT);
        return plugin.getLocaleMessage(key);
    }

    private String cultureMemes(OfflinePlayer player) {
        WDEreligion culture = culture(player);
        if (culture == null || plugin.getReligionManager() == null) return "";
        List<String> names = new ArrayList<>();
        for (String memeId : culture.getMemes()) {
            names.add(plugin.getReligionManager().getMemeDisplayName(memeId));
        }
        return String.join(", ", names);
    }

    private String cultureMeme(OfflinePlayer player, int index) {
        WDEreligion culture = culture(player);
        if (culture == null || plugin.getReligionManager() == null || index < 0) {
            return "";
        }
        List<String> memes = culture.getMemes();
        return index >= memes.size() ? "" : plugin.getReligionManager().getMemeDisplayName(memes.get(index));
    }

    private String background(OfflinePlayer player, RoleBackgroundStage stage) {
        WDEresident resident = resident(player);
        BackgroundManager backgrounds = plugin.getBackgroundManager();
        if (resident == null || backgrounds == null) return "";
        String backgroundId = backgrounds.getSelectedBackgroundId(resident, stage);
        var definition = backgrounds.getDefinition(stage, backgroundId);
        return definition == null ? "" : definition.getTitle();
    }

    private String townOrNation(OfflinePlayer player, boolean nation) {
        Player online = player.getPlayer();
        if (online == null) return "";
        var resident = com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(online);
        if (resident == null || !resident.hasTown() || resident.getTownOrNull() == null) return "";
        var town = resident.getTownOrNull();
        if (!nation) return town.getName();
        return town.getNationOrNull() == null ? "" : town.getNationOrNull().getName();
    }
}
