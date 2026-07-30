package dev.rui.prueba.teleport;

import dev.rui.prueba.Prueba;
import dev.rui.prueba.util.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class HomeService {

    private final Prueba plugin;

    public HomeService(Prueba plugin) {
        this.plugin = plugin;
    }

    public void save(Player player, String name) {
        String key = normalize(name);
        Location point = player.getLocation();
        Document home = new Document("server", plugin.settings().server)
                .append("world", point.getWorld().getName())
                .append("x", point.getX())
                .append("y", point.getY())
                .append("z", point.getZ())
                .append("yaw", (double) point.getYaw())
                .append("pitch", (double) point.getPitch());
        plugin.executor().execute(() -> {
            try {
                Document current = homes(player);
                int limit = plugin.settings().homesLimit;
                if (!current.containsKey(key) && current.size() >= limit) {
                    Messages.send(player, "homes.limit-reached", "limit", String.valueOf(limit));
                    return;
                }
                plugin.store().setField(player.getUniqueId(), "homes." + key, home);
                Messages.send(player, "homes.saved", "name", key);
            } catch (Exception e) {
                Messages.send(player, "homes.save-error");
                plugin.getLogger().warning("Error guardando el hogar de " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    public void delete(Player player, String name) {
        String key = normalize(name);
        plugin.executor().execute(() -> {
            try {
                if (!homes(player).containsKey(key)) {
                    Messages.send(player, "homes.unknown", "name", key);
                    return;
                }
                plugin.store().unsetField(player.getUniqueId(), "homes." + key);
                Messages.send(player, "homes.deleted", "name", key);
            } catch (Exception e) {
                Messages.send(player, "homes.delete-error");
                plugin.getLogger().warning("Error borrando el hogar de " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    public void list(Player player) {
        plugin.executor().execute(() -> {
            try {
                Document homes = homes(player);
                if (homes.isEmpty()) {
                    Messages.send(player, "homes.empty");
                    return;
                }
                List<String> entries = new ArrayList<>();
                for (String key : homes.keySet()) {
                    Document home = homes.get(key, Document.class);
                    String server = home.getString("server");
                    boolean here = plugin.settings().server.equals(server);
                    entries.add(Messages.raw(here ? "homes.list-entry" : "homes.list-entry-other",
                            "name", key, "server", server));
                }
                Messages.send(player, "homes.list", "homes", String.join("<gray>, ", entries));
            } catch (Exception e) {
                Messages.send(player, "homes.read-error");
                plugin.getLogger().warning("Error leyendo los hogares de " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    public void travel(Player player, String name) {
        String key = normalize(name);
        plugin.executor().execute(() -> {
            Document home;
            try {
                home = homes(player).get(key, Document.class);
            } catch (Exception e) {
                Messages.send(player, "homes.read-error");
                return;
            }
            if (home == null) {
                Messages.send(player, "homes.unknown", "name", key);
                return;
            }
            String server = home.getString("server");
            if (!plugin.settings().server.equals(server)) {
                Messages.send(player, "homes.other-server", "server", server, "name", key);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
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
                Messages.send(player, "homes.arrived", "name", key);
            });
        });
    }

    private Document homes(Player player) {
        Document data = plugin.store().load(player.getUniqueId());
        if (data == null) {
            return new Document();
        }
        Document homes = data.get("homes", Document.class);
        return homes == null ? new Document() : homes;
    }

    static String normalize(String name) {
        String clean = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return clean.isEmpty() ? "casa" : clean.substring(0, Math.min(16, clean.length()));
    }
}
