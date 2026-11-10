package com.gearblock.bounty.listener;

import com.gearblock.bounty.GearBountyPlugin;
import com.gearblock.bounty.service.BountyService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.net.InetSocketAddress;

/** Pays the killer when a wanted player dies. */
public final class DeathListener implements Listener {

    private final GearBountyPlugin plugin;

    public DeathListener(GearBountyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim) || plugin.service().total(victim.getUniqueId()) <= 0) {
            return;
        }
        if (plugin.settings().sameIpProtection() && sameIp(killer, victim)) {
            killer.sendMessage(plugin.messages().chat("same-ip", Placeholder.unparsed("player", victim.getName())));
            return;
        }
        BountyService.Claim claim = plugin.service().claim(killer, victim);
        if (claim == null) {
            return;
        }
        String reward = plugin.economy().format(claim.reward());
        killer.sendMessage(plugin.messages().chat("claimed",
                Placeholder.unparsed("player", victim.getName()),
                Placeholder.unparsed("amount", reward)));
        if (claim.refunded() > 0) {
            killer.sendMessage(plugin.messages().chat("claimed-refund",
                    Placeholder.unparsed("amount", plugin.economy().format(claim.refunded()))));
        }
        if (plugin.settings().broadcastOnClaim() && claim.reward() >= plugin.settings().broadcastMinimum()) {
            Bukkit.getServer().broadcast(plugin.messages().chat("claimed-broadcast",
                    Placeholder.unparsed("killer", killer.getName()),
                    Placeholder.unparsed("player", victim.getName()),
                    Placeholder.unparsed("amount", reward)));
        }
    }

    private static boolean sameIp(Player a, Player b) {
        InetSocketAddress addrA = a.getAddress();
        InetSocketAddress addrB = b.getAddress();
        return addrA != null && addrB != null && addrA.getAddress().equals(addrB.getAddress());
    }
}
