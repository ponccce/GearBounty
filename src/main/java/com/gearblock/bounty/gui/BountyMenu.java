package com.gearblock.bounty.gui;

import com.gearblock.bounty.GearBountyPlugin;
import com.gearblock.bounty.config.Messages;
import com.gearblock.bounty.service.BountyService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The bounty board: 45 player heads per page plus a navigation row.
 * The holder pattern lets {@link com.gearblock.bounty.listener.MenuListener} recognise this
 * inventory safely, without comparing titles.
 */
public final class BountyMenu implements InventoryHolder {

    public static final int PER_PAGE = 45;
    public static final int SLOT_PREVIOUS = 45;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_NEXT = 53;

    private final int page;
    private final int pageCount;
    private final Inventory inventory;

    private BountyMenu(GearBountyPlugin plugin, int requestedPage) {
        Messages msg = plugin.messages();
        List<BountyService.Wanted> ranking = plugin.service().ranking();
        this.pageCount = Math.max(1, (ranking.size() + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        this.inventory = Bukkit.createInventory(this, 54,
                msg.plain("menu.title", Placeholder.unparsed("page", String.valueOf(page + 1))));

        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < ranking.size(); i++) {
            BountyService.Wanted w = ranking.get(start + i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(w.target()));
            meta.displayName(msg.plain("menu.entry-name", Placeholder.unparsed("player", w.name())));
            meta.lore(msg.lines("menu.entry-lore",
                    Placeholder.unparsed("amount", plugin.economy().format(w.total())),
                    Placeholder.unparsed("count", String.valueOf(w.contributions()))));
            head.setItemMeta(meta);
            inventory.setItem(i, head);
        }

        ItemStack filler = hiddenItem(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = PER_PAGE; slot < 54; slot++) {
            inventory.setItem(slot, filler);
        }
        if (page > 0) {
            inventory.setItem(SLOT_PREVIOUS, named(Material.ARROW, msg, "menu.previous"));
        }
        if (page < pageCount - 1) {
            inventory.setItem(SLOT_NEXT, named(Material.ARROW, msg, "menu.next"));
        }
        ItemStack info = named(Material.GOLD_INGOT, msg, "menu.info-name");
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.lore(msg.lines("menu.info-lore", Placeholder.unparsed("targets", String.valueOf(ranking.size()))));
        info.setItemMeta(infoMeta);
        inventory.setItem(SLOT_INFO, info);
    }

    /** Opens the menu on the player's own thread (required on Folia, harmless on Paper). */
    public static void open(GearBountyPlugin plugin, Player player, int page) {
        player.getScheduler().run(plugin, task -> player.openInventory(new BountyMenu(plugin, page).getInventory()), null);
    }

    private static ItemStack named(Material material, Messages msg, String key) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(msg.plain(key));
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack hiddenItem(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setHideTooltip(true);
        stack.setItemMeta(meta);
        return stack;
    }

    public int page() {
        return page;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
