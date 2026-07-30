package dev.rui.prueba;

import dev.rui.prueba.command.AdminCommand;
import dev.rui.prueba.config.Settings;
import dev.rui.prueba.mongo.MongoStore;
import dev.rui.prueba.redis.RedisBridge;
import dev.rui.prueba.sync.SyncService;
import dev.rui.prueba.util.Messages;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Prueba extends JavaPlugin {

    private Settings settings;
    private MongoStore store;
    private RedisBridge redis;
    private SyncService sync;
    private ExecutorService executor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(getConfig());
        Messages.load(this);
        executor = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "prueba-mongo");
            thread.setDaemon(true);
            return thread;
        });
        try {
            store = new MongoStore(settings);
            long ping = store.ping();
            getLogger().info("MongoDB conectado en " + ping + " ms (" + settings.database + "." + settings.collection + ").");
        } catch (Exception e) {
            getLogger().severe("No hay conexión con MongoDB: " + e.getMessage());
            getLogger().severe("El plugin se desactiva para no arriesgar los datos de los jugadores.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (settings.redisEnabled) {
            try {
                redis = new RedisBridge(settings, getLogger());
                getLogger().info("Redis conectado (canal '" + settings.redisChannel + "').");
            } catch (Exception e) {
                redis = null;
                getLogger().warning("Redis no disponible, se sigue sin él: " + e.getMessage());
            }
        }
        sync = new SyncService(this, store, redis, executor);
        getServer().getPluginManager().registerEvents(sync, this);
        sync.startAutoSave();

        register("prueba", new AdminCommand(this));
        getLogger().info("Sincronización activa como '" + settings.server + "'.");
    }

    @Override
    public void onDisable() {
        if (sync != null) {
            sync.saveAll();
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    getLogger().warning("Quedaron guardados pendientes al apagar.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (redis != null) {
            redis.close();
        }
        if (store != null) {
            store.close();
        }
    }

    private void register(String name, org.bukkit.command.TabExecutor executor) {
        getCommand(name).setExecutor(executor);
        getCommand(name).setTabCompleter(executor);
    }

    public Settings settings() {
        return settings;
    }

    public MongoStore store() {
        return store;
    }

    public RedisBridge redis() {
        return redis;
    }

    public SyncService sync() {
        return sync;
    }

    public ExecutorService executor() {
        return executor;
    }

    public void reloadSettings() {
        reloadConfig();
        settings = new Settings(getConfig());
        Messages.load(this);
    }
}
