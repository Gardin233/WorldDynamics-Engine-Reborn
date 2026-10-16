package com.thatguycy.worlddynamicsengine.listener.ideology;

import com.thatguycy.worlddynamicsengine.manager.ideology.AnimalKillingDoctrineManager;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/** 在动物死亡结算时，只对玩家造成的击杀触发戒律判定。 */
public final class AnimalKillingDoctrineListener implements Listener {
    private final AnimalKillingDoctrineManager manager;

    public AnimalKillingDoctrineListener(AnimalKillingDoctrineManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnimalDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Animals animal)) {
            return;
        }
        Player killer = animal.getKiller();
        if (killer != null) {
            manager.apply(killer, animal);
        }
    }
}
