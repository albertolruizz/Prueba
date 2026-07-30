package dev.rui.prueba.redis;

import dev.rui.prueba.config.Settings;
import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

public final class RedisBridge implements Closeable {

    private final Settings settings;
    private final Logger log;
    private final JedisPool pool;
    private final Map<UUID, CountDownLatch> waiting = new ConcurrentHashMap<>();
    private final List<Consumer<String[]>> listeners = new CopyOnWriteArrayList<>();
    private final JedisPubSub subscription;
    private volatile boolean closed;

    public RedisBridge(Settings settings, Logger log) {
        this.settings = settings;
        this.log = log;
        String password = settings.redisPassword.isEmpty() ? null : settings.redisPassword;
        pool = new JedisPool(new JedisPoolConfig(), settings.redisHost, settings.redisPort, 2000, password);
        try (Jedis jedis = pool.getResource()) {
            jedis.ping();
        }
        subscription = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                handle(message);
            }
        };
        Thread thread = new Thread(this::listen, "prueba-redis");
        thread.setDaemon(true);
        thread.start();
    }

    private void listen() {
        String password = settings.redisPassword.isEmpty() ? null : settings.redisPassword;
        while (!closed) {
            try (Jedis jedis = new Jedis(settings.redisHost, settings.redisPort)) {
                if (password != null) {
                    jedis.auth(password);
                }
                jedis.subscribe(subscription, settings.redisChannel);
            } catch (Exception e) {
                if (!closed) {
                    log.warning("Conexion con Redis perdida, reintentando en 3s: " + e.getMessage());
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    public void addListener(Consumer<String[]> listener) {
        listeners.add(listener);
    }

    public void publish(String message) {
        try (Jedis jedis = pool.getResource()) {
            jedis.publish(settings.redisChannel, message);
        } catch (Exception e) {
            log.warning("No se pudo publicar en Redis: " + e.getMessage());
        }
    }

    private void handle(String message) {
        String[] parts = message.split("\\|");
        if (parts.length < 3) {
            return;
        }
        if (!parts[0].equals("saved")) {
            for (Consumer<String[]> listener : listeners) {
                try {
                    listener.accept(parts);
                } catch (Exception e) {
                    log.warning("Error procesando el mensaje '" + parts[0] + "' de Redis: " + e.getMessage());
                }
            }
            return;
        }
        if (parts[2].equals(settings.server)) {
            return;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            return;
        }
        CountDownLatch latch = waiting.remove(uuid);
        if (latch != null) {
            latch.countDown();
            if (settings.redisLogHandover) {
                log.info("Relevo recibido desde '" + parts[2] + "' para " + uuid + ".");
            }
        }
    }

    public void publishSaved(UUID uuid) {
        publish("saved|" + uuid + "|" + settings.server);
    }

    public void awaitSave(UUID uuid, long millis) throws InterruptedException {
        CountDownLatch latch = waiting.computeIfAbsent(uuid, key -> new CountDownLatch(1));
        try {
            latch.await(millis, TimeUnit.MILLISECONDS);
        } finally {
            waiting.remove(uuid);
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            subscription.unsubscribe();
        } catch (Exception ignored) {
        }
        pool.close();
    }
}
