package com.gearblock.bounty.service;

import com.gearblock.bounty.GearBountyPlugin;
import com.gearblock.bounty.config.Settings;
import com.gearblock.bounty.hook.EconomyHook;
import com.gearblock.bounty.storage.Bounty;
import com.gearblock.bounty.storage.BountyStorage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The bounty rules. Memory is the source of truth for reads (fast GUI and placeholders);
 * every change is also written to {@link BountyStorage} in the background.
 */
public final class BountyService {

    /** One wanted player, with all contributions added up. */
    public record Wanted(UUID target, String name, double total, int contributions) {
    }

    public enum PlaceResult { OK, SELF, TOO_LOW, TOO_HIGH, NO_MONEY, NO_ECONOMY }

    /** What a killer received: the reward from others, and their own money returned. */
    public record Claim(double reward, double refunded) {
    }

    private final GearBountyPlugin plugin;
    private final BountyStorage storage;
    private final EconomyHook economy;
    private final Map<UUID, List<Bounty>> byTarget = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public BountyService(GearBountyPlugin plugin, BountyStorage storage, EconomyHook economy) {
        this.plugin = plugin;
        this.storage = storage;
        this.economy = economy;
    }

    public void loadAll() throws SQLException {
        long maxId = 0;
        for (Bounty b : storage.loadAll()) {
            byTarget.computeIfAbsent(b.target(), k -> new ArrayList<>()).add(b);
            maxId = Math.max(maxId, b.id());
        }
        nextId.set(maxId + 1);
    }

    public PlaceResult place(Player placer, OfflinePlayer target, String targetName, double amount) {
        Settings s = plugin.settings();
        if (placer.getUniqueId().equals(target.getUniqueId())) {
            return PlaceResult.SELF;
        }
        if (amount < s.minAmount()) {
            return PlaceResult.TOO_LOW;
        }
        if (amount > s.maxAmount()) {
            return PlaceResult.TOO_HIGH;
        }
        if (!economy.isAvailable()) {
            return PlaceResult.NO_ECONOMY;
        }
        if (!economy.has(placer, amount) || !economy.withdraw(placer, amount)) {
            return PlaceResult.NO_MONEY;
        }
        Bounty bounty = new Bounty(nextId.getAndIncrement(), target.getUniqueId(), targetName,
                placer.getUniqueId(), placer.getName(), amount, System.currentTimeMillis());
        byTarget.compute(target.getUniqueId(), (k, list) -> {
            List<Bounty> copy = list == null ? new ArrayList<>() : new ArrayList<>(list);
            copy.add(bounty);
            return copy;
        });
        add(bounty);
        return PlaceResult.OK;
    }

    /**
     * Admin/console bounty paid by the server (events, staff manhunts). No money is taken from
     * anyone, and it is never refunded, since nobody paid for it.
     */
    public double placeFromServer(OfflinePlayer target, String targetName, double amount) {
        add(new Bounty(nextId.getAndIncrement(), target.getUniqueId(), targetName,
                Bounty.SERVER, Bounty.SERVER_NAME, amount, System.currentTimeMillis()));
        return total(target.getUniqueId());
    }

    private void add(Bounty bounty) {
        byTarget.compute(bounty.target(), (k, list) -> {
            List<Bounty> copy = list == null ? new ArrayList<>() : new ArrayList<>(list);
            copy.add(bounty);
            return copy;
        });
        storage.insert(bounty);
    }

    /** Pays the killer the result of {@link #split}. */
    public Claim claim(Player killer, Player victim) {
        List<Bounty> bounties = byTarget.remove(victim.getUniqueId());
        if (bounties == null || bounties.isEmpty()) {
            return null;
        }
        Claim claim = split(bounties, killer.getUniqueId(), plugin.settings().taxPercent());
        if (claim.reward() + claim.refunded() > 0) {
            economy.deposit(killer, claim.reward() + claim.refunded());
        }
        storage.delete(bounties);
        return claim;
    }

    /**
     * Pure money math, kept separate so it can be unit tested. Money the killer put on the victim
     * comes back untaxed, so placing a bounty and killing the target yourself never makes a profit.
     * Everyone else's contributions (including server bounties) are paid minus the tax.
     */
    public static Claim split(List<Bounty> bounties, UUID killer, double taxPercent) {
        double own = 0;
        double others = 0;
        for (Bounty b : bounties) {
            if (b.placer().equals(killer)) {
                own += b.amount();
            } else {
                others += b.amount();
            }
        }
        double reward = Math.round(others * (1 - taxPercent / 100.0) * 100) / 100.0;
        return new Claim(reward, own);
    }

    /** Admin removal: every contribution goes back to whoever placed it (server bounties are just dropped). */
    public boolean removeAndRefund(UUID target) {
        List<Bounty> bounties = byTarget.remove(target);
        if (bounties == null || bounties.isEmpty()) {
            return false;
        }
        bounties.stream().filter(b -> !b.fromServer())
                .forEach(b -> economy.deposit(Bukkit.getOfflinePlayer(b.placer()), b.amount()));
        storage.delete(bounties);
        return true;
    }

    /** Runs on the async scheduler; money and messages are handed to the global region thread. */
    public void expireOld() {
        int days = plugin.settings().expiryDays();
        if (days <= 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
        List<Bounty> expired = new ArrayList<>();
        for (UUID target : List.copyOf(byTarget.keySet())) {
            byTarget.computeIfPresent(target, (k, list) -> {
                List<Bounty> keep = new ArrayList<>();
                for (Bounty b : list) {
                    (b.createdAt() < cutoff ? expired : keep).add(b);
                }
                return keep.isEmpty() ? null : keep;
            });
        }
        if (expired.isEmpty()) {
            return;
        }
        storage.delete(expired);
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            for (Bounty b : expired) {
                if (b.fromServer()) {
                    continue;   // nobody paid for a server bounty, so there is nothing to refund
                }
                OfflinePlayer placer = Bukkit.getOfflinePlayer(b.placer());
                economy.deposit(placer, b.amount());
                Player online = placer.getPlayer();
                if (online != null) {
                    online.sendMessage(plugin.messages().chat("expired-refund",
                            Placeholder.unparsed("player", b.targetName()),
                            Placeholder.unparsed("amount", economy.format(b.amount()))));
                }
            }
        });
    }

    public double total(UUID target) {
        List<Bounty> list = byTarget.get(target);
        return list == null ? 0 : list.stream().mapToDouble(Bounty::amount).sum();
    }

    public Wanted wanted(UUID target) {
        List<Bounty> list = byTarget.get(target);
        if (list == null || list.isEmpty()) {
            return null;
        }
        String name = list.get(list.size() - 1).targetName();
        return new Wanted(target, name, list.stream().mapToDouble(Bounty::amount).sum(), list.size());
    }

    /** Wanted players, highest total first. */
    public List<Wanted> ranking() {
        List<Wanted> result = new ArrayList<>();
        for (UUID target : byTarget.keySet()) {
            Wanted w = wanted(target);
            if (w != null) {
                result.add(w);
            }
        }
        result.sort(Comparator.comparingDouble(Wanted::total).reversed());
        return result;
    }

    public int targetCount() {
        return byTarget.size();
    }
}
