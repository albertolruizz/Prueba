package dev.rui.prueba.command;

import dev.rui.prueba.Prueba;
import dev.rui.prueba.util.Messages;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class AdminCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("status", "save", "reload");

    private final Prueba plugin;

    public AdminCommand(Prueba plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            Messages.send(sender, "admin.usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender);
            case "save" -> save(sender, args);
            case "reload" -> {
                plugin.reloadSettings();
                Messages.send(sender, "admin.reloaded");
            }
            default -> Messages.send(sender, "admin.unknown-sub");
        }
        return true;
    }

    private void status(CommandSender sender) {
        plugin.executor().execute(() -> {
            long ping;
            try {
                ping = plugin.store().ping();
            } catch (Exception e) {
                ping = -1;
            }
            Messages.send(sender, "admin.status-server", "server", plugin.settings().server);
            if (ping >= 0) {
                Messages.send(sender, "admin.status-mongo-ok", "ms", String.valueOf(ping));
            } else {
                Messages.send(sender, "admin.status-mongo-fail");
            }
            Messages.send(sender, plugin.redis() != null ? "admin.status-redis-on" : "admin.status-redis-off");
            if (plugin.settings().proxyEnabled) {
                Messages.send(sender, "admin.status-proxy-on",
                        "name", plugin.settings().proxyName(plugin.settings().server));
            } else {
                Messages.send(sender, "admin.status-proxy-off");
            }
            Messages.send(sender, "admin.status-players",
                    "ready", String.valueOf(plugin.sync().readyCount()),
                    "online", String.valueOf(Bukkit.getOnlinePlayers().size()));
        });
    }

    private void save(CommandSender sender, String[] args) {
        Player target;
        if (args.length > 1) {
            target = Bukkit.getPlayerExact(args[1]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            Messages.send(sender, "admin.save-usage");
            return;
        }
        if (target == null || !target.isOnline()) {
            Messages.send(sender, "admin.player-offline");
            return;
        }
        if (!plugin.sync().isReady(target.getUniqueId())) {
            Messages.send(sender, "admin.player-loading");
            return;
        }
        String name = target.getName();
        plugin.sync().saveNow(target,
                () -> Messages.send(sender, "admin.saved", "player", name));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("save")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
