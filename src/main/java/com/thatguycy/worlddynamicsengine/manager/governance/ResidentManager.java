package com.thatguycy.worlddynamicsengine.manager.governance;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.thatguycy.worlddynamicsengine.manager.storage.DataStorageManager;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 维护居民扩展档案及存档，承载个人意识形态、背景选择和其他长期标记。
 */
public class ResidentManager {
    private static final String STORAGE_CATEGORY = "residents";

    private final JavaPlugin plugin;
    private final DataStorageManager dataStorageManager;
    private final Map<UUID, WDEresident> residents;

    public ResidentManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataStorageManager = ((com.thatguycy.worlddynamicsengine.WorldDynamicsEngine) plugin).getDataStorageManager();
        this.residents = new LinkedHashMap<>();
        loadResidents();
    }

    public void loadResidents() {
        residents.clear();
        residents.putAll(dataStorageManager.loadUuidKeyed(STORAGE_CATEGORY, WDEresident.class));
        plugin.getLogger().info("Total residents loaded: " + residents.size());
    }

    public synchronized void saveResidents() {
        dataStorageManager.saveUuidKeyed(STORAGE_CATEGORY, residents);
    }

    public WDEresident getResident(UUID userUUID) {
        return residents.get(userUUID);
    }

    public java.util.List<WDEresident> getAllResidents() {
        return java.util.List.copyOf(residents.values());
    }

    public WDEresident getOrCreateResident(UUID userUUID, String username) {
        WDEresident resident = residents.get(userUUID);
        if (resident == null) {
            resident = new WDEresident(userUUID, username);
            residents.put(userUUID, resident);
        } else if (username != null && !username.isBlank()) {
            resident.setUsername(username);
        }
        return resident;
    }

    public void addResident(WDEresident resident) {
        residents.put(resident.getUserUUID(), resident);
    }

    public synchronized void publishResidents(java.util.Collection<WDEresident> updatedResidents) {
        if (updatedResidents == null) return;
        for (WDEresident resident : updatedResidents) {
            if (resident != null) residents.put(resident.getUserUUID(), resident);
        }
    }

    public void enableAutoSave() {
        new BukkitRunnable() {
            @Override
            public void run() {
                saveResidents();
            }
        }.runTaskTimer(plugin, 0L, 20L * 60 * 5);
    }

    public void syncWithTowny() {
        for (Resident townyResident : TownyUniverse.getInstance().getResidents()) {
            UUID uuid = townyResident.getUUID();
            getOrCreateResident(uuid, townyResident.getName());
        }
    }

}
