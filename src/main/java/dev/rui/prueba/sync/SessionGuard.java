package dev.rui.prueba.sync;

import org.bson.Document;

public final class SessionGuard {

    private SessionGuard() {
    }

    public static boolean busyElsewhere(Document data, String server) {
        if (data == null) {
            return false;
        }
        Document session = data.get("session", Document.class);
        if (session == null || !Boolean.TRUE.equals(session.getBoolean("online"))) {
            return false;
        }
        String owner = session.getString("server");
        return owner != null && !owner.equals(server);
    }
}
