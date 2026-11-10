package com.gearblock.bounty.hook;

import com.gearblock.bounty.GearBountyPlugin;
import com.gearblock.bounty.service.BountyService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Placeholders:
 * %gearbounty_amount%, %gearbounty_amount_raw%, %gearbounty_count%,
 * %gearbounty_top_<n>_name%, %gearbounty_top_<n>_amount%
 */
public final class BountyExpansion extends PlaceholderExpansion {

    private final GearBountyPlugin plugin;

    public BountyExpansion(GearBountyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "gearbounty";
    }

    @Override
    public @NotNull String getAuthor() {
        return "GearBlock";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String p = params.toLowerCase(Locale.ROOT);
        if (p.startsWith("top_")) {
            return top(p);
        }
        if (player == null) {
            return "";
        }
        BountyService.Wanted w = plugin.service().wanted(player.getUniqueId());
        return switch (p) {
            case "amount" -> plugin.economy().format(w == null ? 0 : w.total());
            case "amount_raw" -> String.format(Locale.ROOT, "%.2f", w == null ? 0 : w.total());
            case "count" -> String.valueOf(w == null ? 0 : w.contributions());
            default -> null;
        };
    }

    private String top(String p) {
        String[] parts = p.split("_");
        if (parts.length != 3) {
            return null;
        }
        int rank;
        try {
            rank = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        List<BountyService.Wanted> ranking = plugin.service().ranking();
        if (rank < 1 || rank > ranking.size()) {
            return "";
        }
        BountyService.Wanted w = ranking.get(rank - 1);
        return switch (parts[2]) {
            case "name" -> w.name();
            case "amount" -> plugin.economy().format(w.total());
            default -> null;
        };
    }
}
