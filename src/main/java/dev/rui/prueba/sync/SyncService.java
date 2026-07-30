package dev.rui.prueba.sync;

import dev.rui.prueba.Prueba;
import dev.rui.prueba.mongo.MongoStore;
import dev.rui.prueba.redis.RedisBridge;
import dev.rui.prueba.teleport.PendingTeleport;
import dev.rui.prueba.util.Messages;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class SyncService implements Listener {

    private static final int HANDOVER_RETRIES = 12;
    private static final long HANDOVER_WAIT_MS = 400L;

    private final Prueba plugin;
    private final MongoStore store;
    private final RedisBridge redis;
    private final ExecutorService executor;
    private final Set<UUID> ready = ConcurrentHashMap.newKeySet();
    private PendingTeleport pending;

    public SyncService(Prueba plugin, MongoStore store, RedisBridge redis, ExecutorService executor) {
        this.plugin = plugin;
        this.store = store;
        this.redis = redis;
        this.executor = executor;
    }

    public void setPending(PendingTeleport pending) {
        this.pending = pending;
    }

    public boolean isReady(UUID uuid) {
        return ready.contains(uuid);
    }

    public int readyCount() {
        return ready.size();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        executor.execute(() -> {
            Document data;
            try {
                data = loadAfterHandover(uuid);
                store.markSession(uuid, plugin.settings().server, true);
            } catch (Exception e) {
                plugin.getLogger().severe("No se pudieron cargar los datos de " + player.getName() + ": " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.kick(Messages.component("sync.load-failed"));
                    }
                });
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (data != null) {
                    try {
                        PlayerSerializer.apply(player, data, plugin.settings());
                    } catch (Exception e) {
                        plugin.getLogger().severe("Error aplicando los datos de " + player.getName() + ": " + e.getMessage());
                        player.kick(Messages.component("sync.apply-failed"));
                        return;
                    }
                }
                ready.add(uuid);
                if (pending != null) {
                    pending.resume(player, data);
                }
            });
        });
    }

    private Document loadAfterHandover(UUID uuid) throws InterruptedException {
        Document data = store.load(uuid);
        int attempts = 0;
        while (data != null && busyElsewhere(data) && attempts++ < HANDOVER_RETRIES) {
            if (redis != null) {
                redis.awaitSave(uuid, HANDOVER_WAIT_MS);
            } else {
                Thread.sleep(HANDOVER_WAIT_MS);
            }
            data = store.load(uuid);
        }
        return data;
    }

    private boolean busyElsewhere(Document data) {
        Document session = data.get("session", Document.class);
        if (session == null || !Boolean.TRUE.equals(session.getBoolean("online"))) {
            return false;
        }
        String server = session.getString("server");
        return server != null && !server.equals(plugin.settings().server);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!ready.remove(uuid)) {
            return;
        }
        if (!plugin.settings().saveOnQuit) {
            executor.execute(() -> {
                try {
                    store.markSession(uuid, plugin.settings().server, false);
                } catch (Exception e) {
                    plugin.getLogger().warning("No se pudo liberar la sesion de " + player.getName() + ": " + e.getMessage());
                }
            });
            return;
        }
        Map<String, Object> fields = PlayerSerializer.capture(player, plugin.settings());
        fields.put("session", session(false));
        executor.execute(() -> {
            try {
                store.save(uuid, fields);
                if (redis != null) {
                    redis.publishSaved(uuid);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("No se pudieron guardar los datos de " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    public void startAutoSave() {
        int interval = plugin.settings().saveIntervalSeconds;
        if (interval <= 0) {
            return;
        }
        long ticks = interval * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (!ready.contains(uuid)) {
                    continue;
                }
                Map<String, Object> fields = PlayerSerializer.capture(player, plugin.settings());
                fields.put("session", session(true));
                executor.execute(() -> {
                    try {
                        store.save(uuid, fields);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Fallo en el autoguardado de " + player.getName() + ": " + e.getMessage());
                    }
                });
            }
        }, ticks, ticks);
    }

    public void saveNow(Player player, Runnable done) {
        Map<String, Object> fields = PlayerSerializer.capture(player, plugin.settings());
        fields.put("session", session(true));
        executor.execute(() -> {
            try {
                store.save(player.getUniqueId(), fields);
                if (done != null) {
                    done.run();
                }
            } catch (Exception e) {
                plugin.getLogger().severe("No se pudieron guardar los datos de " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    public void saveAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!ready.contains(player.getUniqueId())) {
                continue;
            }
            try {
                Map<String, Object> fields = PlayerSerializer.capture(player, plugin.settings());
                fields.put("session", session(false));
                store.save(player.getUniqueId(), fields);
            } catch (Exception e) {
                plugin.getLogger().severe("No se pudieron guardar los datos de " + player.getName() + " al apagar: " + e.getMessage());
            }
        }
    }

    private Document session(boolean online) {
        return new Document("server", plugin.settings().server)
                .append("online", online)
                .append("updated", new Date());
    }

    @EventHandler(ignoreCancelled = true)
    public void onClickWhileLoading(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && !ready.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDropWhileLoading(PlayerDropItemEvent event) {
        if (!ready.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickupWhileLoading(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && !ready.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
