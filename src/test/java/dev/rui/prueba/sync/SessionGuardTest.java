package dev.rui.prueba.sync;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.Document;
import org.junit.jupiter.api.Test;

class SessionGuardTest {

    private Document conSesion(String server, boolean online) {
        return new Document("session", new Document("server", server).append("online", online));
    }

    @Test
    void hayQueEsperarSiOtroServidorLoTieneEnLinea() {
        assertTrue(SessionGuard.busyElsewhere(conSesion("server-1", true), "server-2"));
    }

    @Test
    void noSeEsperaSiElOtroServidorYaLoSolto() {
        assertFalse(SessionGuard.busyElsewhere(conSesion("server-1", false), "server-2"));
    }

    @Test
    void noSeEsperaASiMismo() {
        assertFalse(SessionGuard.busyElsewhere(conSesion("server-2", true), "server-2"));
    }

    @Test
    void unJugadorNuevoNoBloquea() {
        assertFalse(SessionGuard.busyElsewhere(null, "server-2"));
        assertFalse(SessionGuard.busyElsewhere(new Document("name", "Rui"), "server-2"));
        assertFalse(SessionGuard.busyElsewhere(new Document("session", new Document()), "server-2"));
    }
}
