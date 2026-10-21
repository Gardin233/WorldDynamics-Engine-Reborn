package com.thatguycy.worlddynamicsengine.command.history;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/** 分页读取史书、导出 TXT，并按卷领取完整成书，不截断后续历史。 */
public final class HistoryCommand implements CommandExecutor {
    private final WorldDynamicsEngine plugin;
    public HistoryCommand(WorldDynamicsEngine plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("wde.player")) { send(sender, "denied", Map.of()); return true; }
        var manager = plugin.getNationHistoryManager();
        if (args.length < 2 || !List.of("list", "read", "txt", "book").contains(args[1].toLowerCase(Locale.ROOT))) {
            send(sender, "usage", Map.of()); return true;
        }
        try {
            if (args[1].equalsIgnoreCase("list")) {
                int page = args.length >= 3 ? Integer.parseInt(args[2]) : 1;
                var books = manager.list();
                int pages = Math.max(1, (books.size() + 7) / 8);
                if (page < 1 || page > pages) { send(sender, "invalid_page", Map.of()); return true; }
                for (int i = (page - 1) * 8; i < Math.min(page * 8, books.size()); i++) {
                    var book = books.get(i);
                    sender.sendMessage(manager.displayName(book) + " [" + book.id + "]");
                }
                send(sender, "page", Map.of("page", page, "pages", pages));
                return true;
            }
            if (args.length < 3) { send(sender, "usage", Map.of()); return true; }
            var book = manager.find(args[2]);
            if (book == null) { send(sender, "not_found", Map.of()); return true; }
            if (args[1].equalsIgnoreCase("txt")) {
                manager.exportText(book);
                send(sender, "exported", Map.of());
            } else if (args[1].equalsIgnoreCase("book")) {
                if (!(sender instanceof Player player)) { send(sender, "usage", Map.of()); return true; }
                List<String> pages = bookPages(String.join("\n", manager.render(book)));
                int volumes = Math.max(1, (pages.size() + 49) / 50);
                int volume = args.length >= 4 ? Integer.parseInt(args[3]) : 1;
                if (volume < 1 || volume > volumes) { send(sender, "invalid_page", Map.of()); return true; }
                if (player.getInventory().firstEmpty() < 0) { send(sender, "inventory_full", Map.of()); return true; }
                ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
                BookMeta meta = (BookMeta) item.getItemMeta();
                String title = manager.title(book);
                meta.setTitle(title.length() > 32 ? title.substring(0, 32) : title);
                meta.setDisplayName(title + (volumes > 1 ? " (" + volume + "/" + volumes + ")" : ""));
                meta.setAuthor(manager.author());
                meta.setPages(pages.subList((volume - 1) * 50, Math.min(volume * 50, pages.size())));
                item.setItemMeta(meta);
                player.getInventory().addItem(item);
                send(sender, "book_given", Map.of("volume", volume, "volumes", volumes));
            } else {
                List<String> lines = manager.render(book);
                int page = args.length >= 4 ? Integer.parseInt(args[3]) : 1;
                int pages = Math.max(1, (lines.size() + 7) / 8);
                if (page < 1 || page > pages) { send(sender, "invalid_page", Map.of()); return true; }
                sender.sendMessage(manager.title(book));
                lines.subList((page - 1) * 8, Math.min(page * 8, lines.size())).forEach(sender::sendMessage);
                send(sender, "page", Map.of("page", page, "pages", pages));
            }
        } catch (NumberFormatException ex) {
            send(sender, "invalid_page", Map.of());
        } catch (Exception ex) {
            plugin.getLogger().severe("History command failed: " + ex.getMessage());
            send(sender, "failure", Map.of());
        }
        return true;
    }

    public static List<String> bookPages(String text) {
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        int width = 0, lines = 1;
        for (int offset = 0; offset < text.length();) {
            int point = text.codePointAt(offset);
            offset += Character.charCount(point);
            int advance = point < 128 ? 1 : 2;
            if (point == '\n' || width + advance > 20) {
                if (lines == 12) {
                    pages.add(page.toString());
                    page.setLength(0);
                    lines = 1;
                } else {
                    page.append('\n');
                    lines++;
                }
                width = 0;
                if (point == '\n') continue;
            }
            page.appendCodePoint(point);
            width += advance;
        }
        if (!page.isEmpty()) pages.add(page.toString());
        if (pages.isEmpty()) pages.add(" ");
        return pages;
    }

    private void send(CommandSender sender, String key, Map<String, Object> values) {
        sender.sendMessage(plugin.getNationHistoryManager().message(key, values));
    }
}
