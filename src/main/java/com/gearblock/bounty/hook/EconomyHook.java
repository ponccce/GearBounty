package com.gearblock.bounty.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * Thin wrapper around Vault. The provider is looked up on every call because economy plugins
 * may register after GearBounty starts (works with Vault and VaultUnlocked).
 */
public final class EconomyHook {

    private final JavaPlugin plugin;

    public EconomyHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private Economy economy() {
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    public boolean isAvailable() {
        return economy() != null;
    }

    public boolean has(OfflinePlayer player, double amount) {
        Economy eco = economy();
        return eco != null && eco.has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        Economy eco = economy();
        return eco != null && eco.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        Economy eco = economy();
        return eco != null && eco.depositPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        Economy eco = economy();
        return eco != null ? eco.format(amount) : String.format(Locale.ROOT, "%,.2f", amount);
    }
}
