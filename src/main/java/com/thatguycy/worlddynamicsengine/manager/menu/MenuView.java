package com.thatguycy.worlddynamicsengine.manager.menu;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Inventory state shared by menu renderers and the central click listener. */
final class MenuView implements InventoryHolder {
    final UUID owner;
    final Inventory inventory;
    final Map<Integer, Runnable> actions = new HashMap<>();
    boolean busy;

    MenuView(UUID owner, String title) {
        this.owner = owner;
        this.inventory = Bukkit.createInventory(this, 54, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
