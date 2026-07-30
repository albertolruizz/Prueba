package dev.rui.prueba.trade;

import dev.rui.prueba.util.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class TradeCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("accept", "deny", "cancel");

    private final TradeManager manager;

    public TradeCommand(TradeManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.raw("generic.only-players"));
            return true;
        }
        if (args.length == 0) {
            Messages.send(player, "trade.usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "accept" -> manager.accept(player);
            case "deny" -> manager.deny(player);
            case "cancel" -> manager.cancel(player);
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    Messages.send(player, "trade.player-offline");
                    return true;
                }
                manager.request(player, target);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        for (String sub : SUBCOMMANDS) {
            if (sub.startsWith(prefix)) {
                options.add(sub);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.equals(sender) && player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                options.add(player.getName());
            }
        }
        return options;
    }
}
