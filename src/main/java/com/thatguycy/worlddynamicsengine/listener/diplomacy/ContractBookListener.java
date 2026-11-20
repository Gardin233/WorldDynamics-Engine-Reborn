package com.thatguycy.worlddynamicsengine.listener.diplomacy;

import com.thatguycy.worlddynamicsengine.manager.diplomacy.ContractManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;

/** Converts a signed writable book into a durable contract proposal. */
public final class ContractBookListener implements Listener {
    private final ContractManager contractManager;

    public ContractBookListener(ContractManager contractManager) {
        this.contractManager = contractManager;
    }

    @EventHandler
    public void onBookSign(PlayerEditBookEvent event) {
        contractManager.onBookEdit(event);
    }
}
