package dev.rui.prueba.teleport;

import dev.rui.prueba.Prueba;
import dev.rui.prueba.util.Messages;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class TeleportCommand implements TabExecutor {

    private final Prueba plugin;
    private final TeleportRequests requests;

    public TeleportCommand(Prueba plugin, TeleportRequests requests) {
        this.plugin = plugin;
        this.requests = requests;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.raw("generic.only-players"));
            return true;
        }
        if (!plugin.settings().teleportRequestsEnabled) {
            Messages.send(player, "tpa.disabled");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "tpaccept" -> requests.accept(player);
            case "tpdeny" -> requests.deny(player);
            default -> {
                if (args.length == 0) {
                    Messages.send(player, "tpa.usage");
                    return true;
                }
                requests.request(player, args[0]);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("tpa") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> !name.equalsIgnoreCase(sender.getName()))
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
