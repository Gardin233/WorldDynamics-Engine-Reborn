package com.thatguycy.worlddynamicsengine.command.history;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.ChurchChronicle;
import com.thatguycy.worlddynamicsengine.model.IdeologyChronicle;
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
        if (args.length >= 2 && args[1].equalsIgnoreCase("town")) return townHistory(sender, args);
        if (args.length >= 2 && (args[1].equalsIgnoreCase("ideology") || args[1].equalsIgnoreCase("church"))) {
            return ideologyHistory(sender, args, args[1].equalsIgnoreCase("church"));
        }
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

    private boolean ideologyHistory(CommandSender sender, String[] args, boolean church) {
        var manager = plugin.getIdeologyHistoryManager();
        if (manager == null || args.length < 3) {
            if (manager != null) sender.sendMessage(manager.message(church, "usage", Map.of()));
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        try {
            if (action.equals("list")) {
                int page = args.length >= 4 ? Integer.parseInt(args[3]) : 1;
                List<String> entries = new ArrayList<>();
                if (church) {
                    manager.churches().forEach(book -> entries.add(
                            manager.churchDisplayName(book) + " [" + book.id + "]"));
                } else {
                    manager.ideologies().forEach(book -> entries.add(
                            manager.ideologyDisplayName(book) + " [" + book.id + "]"));
                }
                int pages = Math.max(1, (entries.size() + 7) / 8);
                if (page < 1 || page > pages) {
                    sender.sendMessage(manager.message(church, "invalid_page", Map.of()));
                    return true;
                }
                if (entries.isEmpty()) {
                    sender.sendMessage(manager.message(church, "empty", Map.of()));
                } else {
                    sender.sendMessage(manager.message(church, "list_header", Map.of()));
                    entries.subList((page - 1) * 8, Math.min(page * 8, entries.size()))
                            .forEach(sender::sendMessage);
                }
                sender.sendMessage(manager.message(church, "page", Map.of("page", page, "pages", pages)));
                return true;
            }
            if (!List.of("read", "txt", "book").contains(action) || args.length < 4) {
                sender.sendMessage(manager.message(church, "usage", Map.of()));
                return true;
            }

            String name = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            int volume = 1;
            int page = 1;
            IdeologyChronicle ideologyBook = church ? null : manager.findIdeology(name);
            ChurchChronicle churchBook = church ? manager.findChurch(name) : null;
            if (ideologyBook == null && churchBook == null
                    && (action.equals("read") || action.equals("book")) && args.length > 4) {
                try {
                    int value = Integer.parseInt(args[args.length - 1]);
                    String candidate = String.join(" ", Arrays.copyOfRange(args, 3, args.length - 1));
                    ideologyBook = church ? null : manager.findIdeology(candidate);
                    churchBook = church ? manager.findChurch(candidate) : null;
                    if (ideologyBook != null || churchBook != null) {
                        if (action.equals("read")) page = value;
                        else volume = value;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (ideologyBook == null && churchBook == null) {
                sender.sendMessage(manager.message(church, "not_found", Map.of()));
                return true;
            }

            String title = church ? manager.churchTitle(churchBook) : manager.ideologyTitle(ideologyBook);
            List<String> lines = church ? manager.renderChurch(churchBook) : manager.renderIdeology(ideologyBook);
            if (action.equals("txt")) {
                if (church) manager.exportChurchText(churchBook);
                else manager.exportIdeologyText(ideologyBook);
                sender.sendMessage(manager.message(church, "exported", Map.of()));
            } else if (action.equals("book")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(manager.message(church, "usage", Map.of()));
                    return true;
                }
                List<String> pages = bookPages(String.join("\n", lines));
                int volumes = Math.max(1, (pages.size() + 49) / 50);
                if (volume < 1 || volume > volumes) {
                    sender.sendMessage(manager.message(church, "invalid_page", Map.of()));
                    return true;
                }
                if (player.getInventory().firstEmpty() < 0) {
                    sender.sendMessage(manager.message(church, "inventory_full", Map.of()));
                    return true;
                }
                ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
                BookMeta meta = (BookMeta) item.getItemMeta();
                meta.setTitle(title.length() > 32 ? title.substring(0, 32) : title);
                meta.setDisplayName(title + (volumes > 1 ? " (" + volume + "/" + volumes + ")" : ""));
                meta.setAuthor(church ? manager.churchAuthor() : manager.ideologyAuthor());
                meta.setPages(pages.subList((volume - 1) * 50, Math.min(volume * 50, pages.size())));
                item.setItemMeta(meta);
                player.getInventory().addItem(item);
                sender.sendMessage(manager.message(church, "book_given",
                        Map.of("volume", volume, "volumes", volumes)));
            } else {
                int pages = Math.max(1, (lines.size() + 7) / 8);
                if (page < 1 || page > pages) {
                    sender.sendMessage(manager.message(church, "invalid_page", Map.of()));
                    return true;
                }
                sender.sendMessage(title);
                lines.subList((page - 1) * 8, Math.min(page * 8, lines.size()))
                        .forEach(sender::sendMessage);
                sender.sendMessage(manager.message(church, "page", Map.of("page", page, "pages", pages)));
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage(manager.message(church, "invalid_page", Map.of()));
        } catch (Exception ex) {
            plugin.getLogger().severe("Ideology history command failed: " + ex.getMessage());
            sender.sendMessage(manager.message(church, "failure", Map.of()));
        }
        return true;
    }

    private boolean townHistory(CommandSender sender, String[] args) {
        var manager = plugin.getTownHistoryManager();
        if (manager == null || args.length < 3) {
            if (manager != null) sender.sendMessage(manager.message("usage", Map.of()));
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        try {
            if (action.equals("list")) {
                int page = args.length >= 4 ? Integer.parseInt(args[3]) : 1;
                var books = manager.list();
                int pages = Math.max(1, (books.size() + 7) / 8);
                if (page < 1 || page > pages) {
                    sender.sendMessage(manager.message("invalid_page", Map.of()));
                    return true;
                }
                if (books.isEmpty()) {
                    sender.sendMessage(manager.message("empty", Map.of()));
                } else {
                    sender.sendMessage(manager.message("list_header", Map.of()));
                    for (int i = (page - 1) * 8; i < Math.min(page * 8, books.size()); i++) {
                        var book = books.get(i);
                        sender.sendMessage(manager.displayName(book) + " [" + book.id + "]");
                    }
                }
                sender.sendMessage(manager.message("page", Map.of("page", page, "pages", pages)));
                return true;
            }
            if (!List.of("read", "txt", "book").contains(action) || args.length < 4) {
                sender.sendMessage(manager.message("usage", Map.of()));
                return true;
            }

            String name = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            int volume = 1;
            int page = 1;
            var book = manager.find(name);
            if (book == null && (action.equals("read") || action.equals("book")) && args.length > 4) {
                try {
                    int value = Integer.parseInt(args[args.length - 1]);
                    String candidateName = String.join(" ", Arrays.copyOfRange(args, 3, args.length - 1));
                    book = manager.find(candidateName);
                    if (book != null) {
                        if (action.equals("read")) page = value;
                        else volume = value;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (book == null) {
                sender.sendMessage(manager.message("not_found", Map.of()));
                return true;
            }
            if (action.equals("txt")) {
                manager.exportText(book);
                sender.sendMessage(manager.message("exported", Map.of()));
            } else if (action.equals("book")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(manager.message("usage", Map.of()));
                    return true;
                }
                List<String> pages = bookPages(String.join("\n", manager.render(book)));
                int volumes = Math.max(1, (pages.size() + 49) / 50);
                if (volume < 1 || volume > volumes) {
                    sender.sendMessage(manager.message("invalid_page", Map.of()));
                    return true;
                }
                if (player.getInventory().firstEmpty() < 0) {
                    sender.sendMessage(manager.message("inventory_full", Map.of()));
                    return true;
                }
                ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
                BookMeta meta = (BookMeta) item.getItemMeta();
                String title = manager.title(book);
                meta.setTitle(title.length() > 32 ? title.substring(0, 32) : title);
                meta.setDisplayName(title + (volumes > 1 ? " (" + volume + "/" + volumes + ")" : ""));
                meta.setAuthor(manager.author());
                meta.setPages(pages.subList((volume - 1) * 50, Math.min(volume * 50, pages.size())));
                item.setItemMeta(meta);
                player.getInventory().addItem(item);
                sender.sendMessage(manager.message("book_given", Map.of("volume", volume, "volumes", volumes)));
            } else {
                List<String> lines = manager.render(book);
                int pages = Math.max(1, (lines.size() + 7) / 8);
                if (page < 1 || page > pages) {
                    sender.sendMessage(manager.message("invalid_page", Map.of()));
                    return true;
                }
                sender.sendMessage(manager.title(book));
                lines.subList((page - 1) * 8, Math.min(page * 8, lines.size())).forEach(sender::sendMessage);
                sender.sendMessage(manager.message("page", Map.of("page", page, "pages", pages)));
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage(manager.message("invalid_page", Map.of()));
        } catch (Exception ex) {
            plugin.getLogger().severe("Town history command failed: " + ex.getMessage());
            sender.sendMessage(manager.message("failure", Map.of()));
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
