package dev.rui.prueba.teleport;

import dev.rui.prueba.Prueba;
import dev.rui.prueba.util.Messages;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TeleportRequests implements Listener {

    private final Prueba plugin;
    private final Map<UUID, Request> requests = new HashMap<>();

    private record Request(UUID sender, String senderName, String senderServer, long expiresAt) {
    }

    public TeleportRequests(Prueba plugin) {
        this.plugin = plugin;
    }

    public void request(Player sender, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            Messages.send(sender, "tpa.player-offline");
            return;
        }
        requestLocal(sender, target);
    }

    private void requestLocal(Player sender, Player target) {
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            Messages.send(sender, "tpa.self");
            return;
        }
        store(target.getUniqueId(), sender.getUniqueId(), sender.getName(), plugin.settings().server);
        Messages.send(sender, "tpa.request-sent", "player", target.getName());
        Messages.send(target, "tpa.request-received",
                "player", sender.getName(),
                "seconds", String.valueOf(plugin.settings().teleportRequestSeconds));
    }

    private void store(UUID target, UUID sender, String senderName, String senderServer) {
        long expires = System.currentTimeMillis() + plugin.settings().teleportRequestSeconds * 1000L;
        requests.put(target, new Request(sender, senderName, senderServer, expires));
    }

    private Player senderOf(Request request) {
        return Bukkit.getPlayer(request.sender());
    }

    public void accept(Player target) {
        Request request = requests.remove(target.getUniqueId());
        if (request == null || request.expiresAt() < System.currentTimeMillis()) {
            Messages.send(target, "tpa.no-request");
            return;
        }
        Player sender = senderOf(request);
        if (sender == null || !sender.isOnline()) {
            Messages.send(target, "tpa.offline");
            return;
        }
        sender.teleportAsync(target.getLocation());
        Messages.send(sender, "tpa.teleported", "player", target.getName());
        Messages.send(target, "tpa.teleported-target", "player", sender.getName());
    }

    public void deny(Player target) {
        Request request = requests.remove(target.getUniqueId());
        if (request == null || request.expiresAt() < System.currentTimeMillis()) {
            Messages.send(target, "tpa.no-request");
            return;
        }
        Messages.send(target, "tpa.denied");
        Player sender = senderOf(request);
        if (sender != null && sender.isOnline()) {
            Messages.send(sender, "tpa.denied-sender", "player", target.getName());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        requests.remove(uuid);
        requests.entrySet().removeIf(entry -> entry.getValue().sender().equals(uuid));
    }
}
