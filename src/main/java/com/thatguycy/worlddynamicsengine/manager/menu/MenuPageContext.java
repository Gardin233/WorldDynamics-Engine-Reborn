package com.thatguycy.worlddynamicsengine.manager.menu;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.manager.background.BackgroundManager;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import java.util.List;
import java.util.function.IntConsumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Narrow shared UI operations available to an individual menu page. */
interface MenuPageContext {
    WorldDynamicsEngine plugin();
    BackgroundManager backgrounds();
    void executeCommand(Player player, String... args);
    WDEresident resident(Player player);
    String text(String key, Object... args);
    MenuView menu(Player player, String title);
    void button(MenuView menu, int slot, Material material, String title, List<String> lore, Runnable action);
    int paginate(MenuView menu, int size, int requested, IntConsumer openPage);
    void back(MenuView menu, Runnable action);
    void confirm(Player player, String title, List<String> lore, Runnable cancel, Runnable apply);
    void success(Player player);
    void openHome(Player player);
}
