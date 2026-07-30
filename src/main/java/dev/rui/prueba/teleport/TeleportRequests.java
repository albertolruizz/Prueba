package dev.rui.prueba.teleport;

import dev.rui.prueba.Prueba;
import dev.rui.prueba.util.Messages;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TeleportRequests implements Listener {

    private final Prueba plugin;
    private final PendingTeleport pending;
    private final Map<UUID, Request> requests = new HashMap<>();

    private record Request(UUID sender, String senderName, String senderServer, long expiresAt) {

        boolean local(String server) {
            return senderServer.equals(server);
        }
    }

    public TeleportRequests(Prueba plugin, PendingTeleport pending) {
        this.plugin = plugin;
        this.pending = pending;
        if (plugin.redis() != null) {
            plugin.redis().addListener(this::onRedisMessage);
        }
    }

    public void request(Player sender, String targetName) {
        Player local = Bukkit.getPlayerExact(targetName);
        if (local != null) {
            requestLocal(sender, local);
            return;
        }
        if (plugin.redis() == null || !plugin.settings().proxyEnabled) {
            Messages.send(sender, "tpa.player-offline");
            return;
        }
        plugin.executor().execute(() -> {
            Document target = plugin.store().findByName(targetName);
            Document session = target == null ? null : target.get("session", Document.class);
            if (session == null || !Boolean.TRUE.equals(session.getBoolean("online"))) {
                Messages.send(sender, "tpa.player-offline");
                return;
            }
            String server = session.getString("server");
            if (plugin.settings().server.equals(server)) {
                Messages.send(sender, "tpa.player-offline");
                return;
            }
            plugin.redis().publish(String.join("|", "tpa-request",
                    target.getString("_id"),
                    server,
                    sender.getUniqueId().toString(),
                    sender.getName(),
                    plugin.settings().server));
            Messages.send(sender, "tpa.request-sent", "player", target.getString("name"));
        });
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

    public void accept(Player target) {
        Request request = requests.remove(target.getUniqueId());
        if (request == null || request.expiresAt() < System.currentTimeMillis()) {
            Messages.send(target, "tpa.no-request");
            return;
        }
        if (request.local(plugin.settings().server)) {
            Player sender = Bukkit.getPlayer(request.sender());
            if (sender == null || !sender.isOnline()) {
                Messages.send(target, "tpa.offline");
                return;
            }
            sender.teleportAsync(target.getLocation());
            Messages.send(sender, "tpa.teleported", "player", target.getName());
            Messages.send(target, "tpa.teleported-target", "player", sender.getName());
            return;
        }
        plugin.redis().publish(String.join("|", "tpa-accept",
                request.sender().toString(),
                request.senderServer(),
                target.getUniqueId().toString(),
                target.getName(),
                plugin.settings().server));
    }

    public void deny(Player target) {
        Request request = requests.remove(target.getUniqueId());
        if (request == null || request.expiresAt() < System.currentTimeMillis()) {
            Messages.send(target, "tpa.no-request");
            return;
        }
        Messages.send(target, "tpa.denied");
        if (request.local(plugin.settings().server)) {
            Player sender = Bukkit.getPlayer(request.sender());
            if (sender != null && sender.isOnline()) {
                Messages.send(sender, "tpa.denied-sender", "player", target.getName());
            }
            return;
        }
        plugin.redis().publish(String.join("|", "tpa-deny",
                request.sender().toString(),
                request.senderServer(),
                target.getName()));
    }

    private void onRedisMessage(String[] parts) {
        switch (parts[0]) {
            case "tpa-request" -> {
                if (!plugin.settings().server.equals(parts[2])) {
                    return;
                }
                Player target = Bukkit.getPlayer(UUID.fromString(parts[1]));
                if (target == null || !target.isOnline()) {
                    return;
                }
                store(target.getUniqueId(), UUID.fromString(parts[3]), parts[4], parts[5]);
                Messages.send(target, "tpa.request-received-other",
                        "player", parts[4],
                        "server", parts[5],
                        "seconds", String.valueOf(plugin.settings().teleportRequestSeconds));
            }
            case "tpa-accept" -> {
                if (!plugin.settings().server.equals(parts[2])) {
                    return;
                }
                Player sender = Bukkit.getPlayer(UUID.fromString(parts[1]));
                if (sender == null || !sender.isOnline()) {
                    return;
                }
                String targetServer = parts[5];
                UUID targetId = UUID.fromString(parts[3]);
                plugin.executor().execute(() -> {
                    try {
                        pending.toPlayer(sender.getUniqueId(), targetServer, targetId);
                    } catch (Exception e) {
                        Messages.send(sender, "proxy.failed");
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!sender.isOnline()) {
                            return;
                        }
                        Messages.send(sender, "tpa.accepted-remote", "player", parts[4], "server", targetServer);
                        plugin.proxy().send(sender, plugin.settings().proxyName(targetServer));
                    });
                });
            }
            case "tpa-deny" -> {
                if (!plugin.settings().server.equals(parts[2])) {
                    return;
                }
                Player sender = Bukkit.getPlayer(UUID.fromString(parts[1]));
                if (sender != null && sender.isOnline()) {
                    Messages.send(sender, "tpa.denied-sender", "player", parts[3]);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        requests.remove(uuid);
        requests.entrySet().removeIf(entry -> entry.getValue().sender().equals(uuid));
    }
}
