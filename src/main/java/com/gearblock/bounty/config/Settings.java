package com.gearblock.bounty.config;

import org.bukkit.configuration.file.FileConfiguration;

/** Immutable snapshot of config.yml. Rebuilt on every reload. */
public record Settings(
        String language,
        double minAmount,
        double maxAmount,
        double taxPercent,
        int expiryDays,
        long expiryCheckMinutes,
        int topSize,
        boolean sameIpProtection,
        boolean broadcastOnPlace,
        boolean broadcastOnClaim,
        double broadcastMinimum
) {

    public static Settings from(FileConfiguration c) {
        return new Settings(
                c.getString("language", "en"),
                Math.max(0, c.getDouble("bounty.min-amount", 100)),
                Math.max(0, c.getDouble("bounty.max-amount", 1_000_000)),
                clamp(c.getDouble("bounty.tax-percent", 5), 0, 100),
                Math.max(0, c.getInt("bounty.expiry-days", 7)),
                Math.max(1, c.getLong("bounty.expiry-check-minutes", 10)),
                Math.max(1, c.getInt("bounty.top-size", 10)),
                c.getBoolean("anti-abuse.same-ip-protection", true),
                c.getBoolean("broadcast.on-place", true),
                c.getBoolean("broadcast.on-claim", true),
                Math.max(0, c.getDouble("broadcast.minimum-total", 1000))
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
