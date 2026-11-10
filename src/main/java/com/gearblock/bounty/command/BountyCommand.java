package com.gearblock.bounty.command;

import com.gearblock.bounty.GearBountyPlugin;
import com.gearblock.bounty.config.Messages;
import com.gearblock.bounty.gui.BountyMenu;
import com.gearblock.bounty.hook.EconomyHook;
import com.gearblock.bounty.service.BountyService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** /bounty [set|check|top|remove|reload] */
public final class BountyCommand implements CommandExecutor, TabCompleter {

    private static final String USE = "gearbounty.use";
    private static final String ADMIN = "gearbounty.admin";

    private final GearBountyPlugin plugin;

    public BountyCommand(GearBountyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        Messages msg = plugin.messages();
        if (!sender.hasPermission(USE)) {
            sender.sendMessage(msg.chat("no-permission"));
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player player) {
                BountyMenu.open(plugin, player, 0);
            } else {
                sender.sendMessage(msg.plain("usage"));
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> set(sender, args);
            case "check" -> check(sender, args);
            case "top" -> top(sender);
            case "remove" -> remove(sender, args);
            case "reload" -> {
                if (!sender.hasPermission(ADMIN)) {
                    sender.sendMessage(msg.chat("no-permission"));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(plugin.messages().chat("reload"));
            }
            default -> sender.sendMessage(msg.plain("usage"));
        }
        return true;
    }

    private void set(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        EconomyHook eco = plugin.economy();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.chat("player-only"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(msg.plain("usage"));
            return;
        }
        OfflinePlayer target = findPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(msg.chat("player-not-found", Placeholder.unparsed("player", args[1])));
            return;
        }
        double amount = parseAmount(args[2]);
        if (Double.isNaN(amount)) {
            sender.sendMessage(msg.chat("invalid-amount", Placeholder.unparsed("amount", args[2])));
            return;
        }
        String targetName = target.getName() != null ? target.getName() : args[1];
        BountyService.PlaceResult result = plugin.service().place(player, target, targetName, amount);
        switch (result) {
            case SELF -> sender.sendMessage(msg.chat("self-bounty"));
            case TOO_LOW -> sender.sendMessage(msg.chat("amount-too-low",
                    Placeholder.unparsed("min", eco.format(plugin.settings().minAmount()))));
            case TOO_HIGH -> sender.sendMessage(msg.chat("amount-too-high",
                    Placeholder.unparsed("max", eco.format(plugin.settings().maxAmount()))));
            case NO_MONEY, NO_ECONOMY -> sender.sendMessage(msg.chat("not-enough-money",
                    Placeholder.unparsed("amount", eco.format(amount))));
            case OK -> {
                double total = plugin.service().total(target.getUniqueId());
                sender.sendMessage(msg.chat("placed",
                        Placeholder.unparsed("player", targetName),
                        Placeholder.unparsed("amount", eco.format(amount)),
                        Placeholder.unparsed("total", eco.format(total))));
                if (plugin.settings().broadcastOnPlace() && total >= plugin.settings().broadcastMinimum()) {
                    Bukkit.getServer().broadcast(msg.chat("placed-broadcast",
                            Placeholder.unparsed("placer", player.getName()),
                            Placeholder.unparsed("player", targetName),
                            Placeholder.unparsed("total", eco.format(total))));
                }
            }
        }
    }

    private void check(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        OfflinePlayer target;
        if (args.length >= 2) {
            target = findPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(msg.chat("player-not-found", Placeholder.unparsed("player", args[1])));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(msg.plain("usage"));
            return;
        }
        String name = target.getName() != null ? target.getName() : args[1];
        BountyService.Wanted wanted = plugin.service().wanted(target.getUniqueId());
        if (wanted == null) {
            sender.sendMessage(msg.chat("check-none", Placeholder.unparsed("player", name)));
            return;
        }
        sender.sendMessage(msg.chat("check",
                Placeholder.unparsed("player", name),
                Placeholder.unparsed("total", plugin.economy().format(wanted.total())),
                Placeholder.unparsed("count", String.valueOf(wanted.contributions()))));
    }

    private void top(CommandSender sender) {
        Messages msg = plugin.messages();
        List<BountyService.Wanted> ranking = plugin.service().ranking();
        if (ranking.isEmpty()) {
            sender.sendMessage(msg.chat("top-empty"));
            return;
        }
        sender.sendMessage(msg.plain("top-header"));
        int size = Math.min(plugin.settings().topSize(), ranking.size());
        for (int i = 0; i < size; i++) {
            BountyService.Wanted w = ranking.get(i);
            sender.sendMessage(msg.plain("top-line",
                    Placeholder.unparsed("rank", String.valueOf(i + 1)),
                    Placeholder.unparsed("player", w.name()),
                    Placeholder.unparsed("amount", plugin.economy().format(w.total()))));
        }
    }

    private void remove(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        if (!sender.hasPermission(ADMIN)) {
            sender.sendMessage(msg.chat("no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(msg.plain("usage"));
            return;
        }
        OfflinePlayer target = findPlayer(args[1]);
        if (target == null || !plugin.service().removeAndRefund(target.getUniqueId())) {
            sender.sendMessage(msg.chat("check-none", Placeholder.unparsed("player", args[1])));
            return;
        }
        sender.sendMessage(msg.chat("removed", Placeholder.unparsed("player", args[1])));
    }

    /** Online players first; otherwise only players the server has already seen. */
    private static OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null && cached.hasPlayedBefore() ? cached : null;
    }

    /** Returns NaN for anything that is not a finite, positive number. Accepts "1,000.50" and "1k". */
    static double parseAmount(String input) {
        String s = input.trim().toLowerCase(Locale.ROOT).replace(",", "");
        double multiplier = 1;
        if (s.endsWith("k")) {
            multiplier = 1_000;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m")) {
            multiplier = 1_000_000;
            s = s.substring(0, s.length() - 1);
        }
        try {
            double value = Double.parseDouble(s) * multiplier;
            if (!Double.isFinite(value) || value <= 0) {
                return Double.NaN;
            }
            return Math.round(value * 100) / 100.0;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) {
            Stream<String> subs = sender.hasPermission(ADMIN)
                    ? Stream.of("set", "check", "top", "remove", "reload")
                    : Stream.of("set", "check", "top");
            return filter(subs, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("set") || sub.equals("check") || sub.equals("remove"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName), args[1]);
        }
        if (args.length == 3 && sub.equals("set")) {
            return filter(Stream.of("100", "1000", "10k"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(Stream<String> options, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        return options.filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
