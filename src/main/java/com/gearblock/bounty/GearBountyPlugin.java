package com.gearblock.bounty;

import com.gearblock.bounty.command.BountyCommand;
import com.gearblock.bounty.config.Messages;
import com.gearblock.bounty.config.Settings;
import com.gearblock.bounty.hook.BountyExpansion;
import com.gearblock.bounty.hook.EconomyHook;
import com.gearblock.bounty.listener.DeathListener;
import com.gearblock.bounty.listener.MenuListener;
import com.gearblock.bounty.service.BountyService;
import com.gearblock.bounty.storage.BountyStorage;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

/**
 * Entry point. Wires config, storage, economy and listeners together.
 * All scheduling goes through Paper's region/async schedulers, so the plugin also runs on Folia.
 */
public final class GearBountyPlugin extends JavaPlugin {

    private Settings settings;
    private Messages messages;
    private BountyStorage storage;
    private BountyService service;
    private EconomyHook economy;
    private ScheduledTask expiryTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("lang/en.yml");
        saveResourceIfMissing("lang/pt_br.yml");
        loadSettings();

        economy = new EconomyHook(this);
        storage = new BountyStorage(getDataFolder().toPath().resolve("data.db"));
        try {
            storage.open();
        } catch (SQLException e) {
            getLogger().severe("Could not open the bounty database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        service = new BountyService(this, storage, economy);
        try {
            service.loadAll();
        } catch (SQLException e) {
            getLogger().severe("Could not load bounties: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        BountyCommand command = new BountyCommand(this);
        PluginCommand bounty = getCommand("bounty");
        if (bounty != null) {
            bounty.setExecutor(command);
            bounty.setTabCompleter(command);
        }

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new DeathListener(this), this);
        pm.registerEvents(new MenuListener(this), this);
        if (pm.getPlugin("PlaceholderAPI") != null) {
            new BountyExpansion(this).register();
            getLogger().info("Hooked into PlaceholderAPI.");
        }

        // Economy plugins register with Vault during their own startup, so check once the server is running.
        Bukkit.getGlobalRegionScheduler().run(this, task -> {
            if (!economy.isAvailable()) {
                getLogger().warning("No economy plugin found through Vault. Install EssentialsX, CMI or another economy plugin.");
            }
        });

        scheduleExpiry();
        getLogger().info("GearBounty enabled with " + service.targetCount() + " wanted players.");
    }

    @Override
    public void onDisable() {
        if (expiryTask != null) {
            expiryTask.cancel();
        }
        if (storage != null) {
            storage.close();
        }
    }

    /** Reloads config.yml and the language file. */
    public void reload() {
        reloadConfig();
        loadSettings();
        scheduleExpiry();
    }

    private void loadSettings() {
        settings = Settings.from(getConfig());
        messages = Messages.load(this, settings.language());
    }

    private void scheduleExpiry() {
        if (expiryTask != null) {
            expiryTask.cancel();
        }
        long minutes = settings.expiryCheckMinutes();
        expiryTask = Bukkit.getAsyncScheduler().runAtFixedRate(this, task -> service.expireOld(), minutes, minutes, TimeUnit.MINUTES);
    }

    private void saveResourceIfMissing(String path) {
        if (!new File(getDataFolder(), path).exists()) {
            saveResource(path, false);
        }
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public BountyService service() {
        return service;
    }

    public EconomyHook economy() {
        return economy;
    }
}
