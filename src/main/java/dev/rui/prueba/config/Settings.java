package dev.rui.prueba.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class Settings {

    public final String server;
    public final String mongoUri;
    public final String database;
    public final String collection;
    public final int saveIntervalSeconds;
    public final boolean saveOnQuit;
    public final boolean redisEnabled;
    public final String redisHost;
    public final int redisPort;
    public final String redisPassword;
    public final String redisChannel;
    public final boolean redisLogHandover;
    public final boolean tradeEnabled;
    public final double tradeRange;
    public final int tradeRequestSeconds;
    public final boolean homesEnabled;
    public final int homesLimit;
    public final boolean teleportRequestsEnabled;
    public final int teleportRequestSeconds;
    public final boolean proxyEnabled;

    private final Set<String> enabledFields = new HashSet<>();
    private final Map<String, String> proxyNames = new HashMap<>();

    public Settings(FileConfiguration cfg) {
        server = cfg.getString("server", "server-1");
        mongoUri = cfg.getString("mongodb.uri", "mongodb://localhost:27017");
        database = cfg.getString("mongodb.database", "prueba");
        collection = cfg.getString("mongodb.collection", "players");
        saveIntervalSeconds = cfg.getInt("save.interval-seconds", 300);
        saveOnQuit = cfg.getBoolean("save.on-quit", true);
        ConfigurationSection fields = cfg.getConfigurationSection("save.fields");
        if (fields != null) {
            for (String key : fields.getKeys(false)) {
                if (fields.getBoolean(key)) {
                    enabledFields.add(key.toLowerCase(Locale.ROOT));
                }
            }
        }
        redisEnabled = cfg.getBoolean("redis.enabled", false);
        redisHost = cfg.getString("redis.host", "localhost");
        redisPort = cfg.getInt("redis.port", 6379);
        redisPassword = cfg.getString("redis.password", "");
        redisChannel = cfg.getString("redis.channel", "prueba:sync");
        redisLogHandover = cfg.getBoolean("redis.log-handover", true);
        tradeEnabled = cfg.getBoolean("trade.enabled", true);
        tradeRange = cfg.getDouble("trade.max-range", 10.0);
        tradeRequestSeconds = cfg.getInt("trade.request-seconds", 30);
        homesEnabled = cfg.getBoolean("homes.enabled", true);
        homesLimit = cfg.getInt("homes.limit", 3);
        teleportRequestsEnabled = cfg.getBoolean("teleport-requests.enabled", true);
        teleportRequestSeconds = cfg.getInt("teleport-requests.request-seconds", 60);
        proxyEnabled = cfg.getBoolean("proxy.enabled", false);
        ConfigurationSection names = cfg.getConfigurationSection("proxy.server-names");
        if (names != null) {
            for (String key : names.getKeys(false)) {
                proxyNames.put(key, names.getString(key));
            }
        }
    }

    public boolean field(String name) {
        return enabledFields.contains(name);
    }

    public String proxyName(String server) {
        return proxyNames.getOrDefault(server, server);
    }
}
