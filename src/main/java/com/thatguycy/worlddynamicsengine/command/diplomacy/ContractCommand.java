package com.thatguycy.worlddynamicsengine.command.diplomacy;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.manager.diplomacy.ContractManager;
import com.thatguycy.worlddynamicsengine.model.ContractRecord;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Command interface for writing, reviewing, and recording non-enforceable contracts. */
public final class ContractCommand implements CommandExecutor {
    private final WorldDynamicsEngine plugin;
    private final ContractManager contractManager;

    public ContractCommand(WorldDynamicsEngine plugin, ContractManager contractManager) {
        this.plugin = plugin;
        this.contractManager = contractManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLocaleMessage("common_only_players"));
            return true;
        }
        if (args.length < 2 || "help".equalsIgnoreCase(args[1])) {
            tell(player, "contract_usage");
            return true;
        }
        switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "create" -> {
                if (args.length < 5) {
                    tell(player, "contract_usage");
                    return true;
                }
                String targetName = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                contractManager.beginDraft(player, args[2], args[3], targetName);
                return true;
            }
            case "inbox" -> {
                showList(player, contractManager.pendingFor(player), true);
                return true;
            }
            case "list" -> {
                showList(player, contractManager.visibleTo(player), false);
                return true;
            }
            case "view" -> {
                if (args.length != 3) {
                    tell(player, "contract_usage");
                    return true;
                }
                view(player, args[2]);
                return true;
            }
            case "accept", "reject" -> {
                if (args.length != 3) {
                    tell(player, "contract_usage");
                    return true;
                }
                boolean accepted = args[1].equalsIgnoreCase("accept");
                String result = contractManager.decide(player, args[2], accepted);
                tell(player, result, args[2]);
                return true;
            }
            default -> {
                tell(player, "contract_usage");
                return true;
            }
        }
    }

    private void view(Player player, String id) {
        ContractRecord contract = contractManager.get(id);
        if (contract == null) {
            tell(player, "contract_not_found");
            return;
        }
        ItemStack book = contractManager.openForViewing(player, contract);
        if (book == null) {
            tell(player, "contract_not_authorized");
            return;
        }
        player.openBook(book);
        if ("PENDING".equals(contract.status) && contractManager.isRecipient(player, contract)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.sendMessage(Component.text(plugin.getLocaleMessage("contract_decision_prompt", contract.id))
                        .color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text(plugin.getLocaleMessage("contract_accept_button"))
                        .color(NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/wde contract accept " + contract.id))
                        .append(Component.text("  "))
                        .append(Component.text(plugin.getLocaleMessage("contract_reject_button"))
                                .color(NamedTextColor.RED)
                                .clickEvent(ClickEvent.runCommand("/wde contract reject " + contract.id))));
            }, 2L);
        }
    }

    private void showList(Player player, List<ContractRecord> contracts, boolean inbox) {
        if (contracts.isEmpty()) {
            tell(player, inbox ? "contract_inbox_empty" : "contract_list_empty");
            return;
        }
        tell(player, inbox ? "contract_inbox_header" : "contract_list_header");
        for (ContractRecord contract : contracts) {
            String status = contractManager.displayStatus(contract.status);
            Component entry = Component.text(plugin.formatLocaleMessage(player.getLocale(),
                    "contract_list_entry", contract.id, contract.fromDisplay, contract.toDisplay, status))
                    .color("PENDING".equals(contract.status) ? NamedTextColor.YELLOW : NamedTextColor.GRAY)
                    .clickEvent(ClickEvent.runCommand("/wde contract view " + contract.id));
            player.sendMessage(entry);
        }
        tell(player, "contract_list_hint");
    }

    private void tell(Player player, String key, Object... args) {
        player.sendMessage(plugin.formatLocaleMessage(player.getLocale(), key, args));
    }
}
