package dev.rui.prueba.teleport;

import dev.rui.prueba.Prueba;
import dev.rui.prueba.util.Messages;
import java.util.Date;
import java.util.UUID;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class PendingTeleport {

    public static final String FIELD = "pending_teleport";
    private static final long MAX_AGE_MS = 120_000L;

    private final Prueba plugin;

    public PendingTeleport(Prueba plugin) {
        this.plugin = plugin;
    }

    public void toHome(UUID uuid, String server, String home) {
        plugin.store().setField(uuid, FIELD, new Document("type", "home")
                .append("server", server)
                .append("home", home)
                .append("created", new Date()));
    }

    public void toPlayer(UUID uuid, String server, UUID target) {
        plugin.store().setField(uuid, FIELD, new Document("type", "player")
                .append("server", server)
                .append("target", target.toString())
                .append("created", new Date()));
    }

    public void resume(Player player, Document data) {
        Document pending = data == null ? null : data.get(FIELD, Document.class);
        if (pending == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        plugin.executor().execute(() -> {
            try {
                plugin.store().unsetField(uuid, FIELD);
            } catch (Exception e) {
                plugin.getLogger().warning("No se pudo limpiar el teletransporte pendiente de "
                        + player.getName() + ": " + e.getMessage());
            }
        });
        if (!plugin.settings().server.equals(pending.getString("server"))) {
            return;
        }
        Date created = pending.getDate("created");
        if (created != null && System.currentTimeMillis() - created.getTime() > MAX_AGE_MS) {
            return;
        }
        if ("home".equals(pending.getString("type"))) {
            resumeHome(player, data, pending.getString("home"));
        } else {
            resumePlayer(player, pending.getString("target"));
        }
    }

    private void resumeHome(Player player, Document data, String name) {
        Document homes = data.get("homes", Document.class);
        Document home = homes == null ? null : homes.get(name, Document.class);
        if (home == null) {
            Messages.send(player, "homes.unknown", "name", name);
            return;
        }
        World world = Bukkit.getWorld(home.getString("world"));
        if (world == null) {
            Messages.send(player, "homes.world-missing");
            return;
        }
        player.teleportAsync(new Location(world,
                home.getDouble("x"), home.getDouble("y"), home.getDouble("z"),
                home.getDouble("yaw").floatValue(), home.getDouble("pitch").floatValue()));
        Messages.send(player, "homes.arrived", "name", name);
    }

    private void resumePlayer(Player player, String rawTarget) {
        if (rawTarget == null) {
            return;
        }
        Player target = Bukkit.getPlayer(UUID.fromString(rawTarget));
        if (target == null || !target.isOnline()) {
            Messages.send(player, "tpa.target-gone");
            return;
        }
        player.teleportAsync(target.getLocation());
        Messages.send(player, "tpa.teleported", "player", target.getName());
        Messages.send(target, "tpa.teleported-target", "player", player.getName());
    }
}
