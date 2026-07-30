package dev.rui.prueba.teleport;

import dev.rui.prueba.Prueba;
import dev.rui.prueba.util.Messages;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class HomeCommand implements TabExecutor {

    private final Prueba plugin;
    private final HomeService homes;

    public HomeCommand(Prueba plugin, HomeService homes) {
        this.plugin = plugin;
        this.homes = homes;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.raw("generic.only-players"));
            return true;
        }
        if (!plugin.settings().homesEnabled) {
            Messages.send(player, "homes.disabled");
            return true;
        }
        if (!plugin.sync().isReady(player.getUniqueId())) {
            Messages.send(player, "generic.still-loading");
            return true;
        }
        String name = args.length > 0 ? args[0] : "casa";
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "sethome" -> homes.save(player, name);
            case "delhome" -> homes.delete(player, name);
            case "homes" -> homes.list(player);
            default -> homes.travel(player, name);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
