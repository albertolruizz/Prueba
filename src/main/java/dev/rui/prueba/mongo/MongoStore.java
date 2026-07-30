package dev.rui.prueba.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import dev.rui.prueba.config.Settings;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.bson.conversions.Bson;

public final class MongoStore implements Closeable {

    private static final UpdateOptions UPSERT = new UpdateOptions().upsert(true);

    private final MongoClient client;
    private final MongoCollection<Document> players;

    public MongoStore(Settings settings) {
        MongoClientSettings options = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(settings.mongoUri))
                .applyToClusterSettings(b -> b.serverSelectionTimeout(5, TimeUnit.SECONDS))
                .build();
        client = MongoClients.create(options);
        players = client.getDatabase(settings.database).getCollection(settings.collection);
    }

    public Document load(UUID uuid) {
        return players.find(Filters.eq("_id", uuid.toString())).first();
    }

    public void save(UUID uuid, Map<String, Object> fields) {
        List<Bson> updates = new ArrayList<>();
        fields.forEach((key, value) -> updates.add(Updates.set(key, value)));
        updates.add(Updates.setOnInsert("created", new Date()));
        players.updateOne(Filters.eq("_id", uuid.toString()), Updates.combine(updates), UPSERT);
    }

    public void markSession(UUID uuid, String server, boolean online) {
        Bson updates = Updates.combine(
                Updates.set("session.server", server),
                Updates.set("session.online", online),
                Updates.set("session.updated", new Date()),
                Updates.setOnInsert("created", new Date()));
        players.updateOne(Filters.eq("_id", uuid.toString()), updates, UPSERT);
    }

    public long ping() {
        long start = System.nanoTime();
        client.getDatabase("admin").runCommand(new Document("ping", 1));
        return (System.nanoTime() - start) / 1_000_000L;
    }

    @Override
    public void close() {
        client.close();
    }
}
