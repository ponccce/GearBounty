package com.gearblock.bounty.listener;

import com.gearblock.bounty.GearBountyPlugin;
import com.gearblock.bounty.gui.BountyMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Makes the bounty board read-only and handles the page arrows. */
public final class MenuListener implements Listener {

    private final GearBountyPlugin plugin;

    public MenuListener(GearBountyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof BountyMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (event.getSlot() == BountyMenu.SLOT_PREVIOUS && menu.page() > 0) {
            BountyMenu.open(plugin, player, menu.page() - 1);
        } else if (event.getSlot() == BountyMenu.SLOT_NEXT) {
            BountyMenu.open(plugin, player, menu.page() + 1);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof BountyMenu) {
            event.setCancelled(true);
        }
    }
}
