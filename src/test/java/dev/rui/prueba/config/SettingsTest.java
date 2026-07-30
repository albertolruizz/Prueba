package dev.rui.prueba.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SettingsTest {

    private Settings parse(String yaml) throws InvalidConfigurationException {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(yaml);
        return new Settings(cfg);
    }

    @Test
    void leeLaConfiguracionCompleta() throws Exception {
        Settings settings = parse("""
                server: "lobby"
                mongodb:
                  uri: "mongodb://uno:27017"
                  database: "red"
                  collection: "jugadores"
                save:
                  interval-seconds: 60
                  on-quit: false
                  fields:
                    profile: true
                    state: false
                redis:
                  enabled: true
                  port: 6380
                trade:
                  max-range: 4.5
                proxy:
                  enabled: true
                  server-names:
                    server-1: "supervivencia"
                """);

        assertEquals("lobby", settings.server);
        assertEquals("mongodb://uno:27017", settings.mongoUri);
        assertEquals("red", settings.database);
        assertEquals("jugadores", settings.collection);
        assertEquals(60, settings.saveIntervalSeconds);
        assertFalse(settings.saveOnQuit);
        assertTrue(settings.redisEnabled);
        assertEquals(6380, settings.redisPort);
        assertEquals(4.5, settings.tradeRange);
        assertTrue(settings.proxyEnabled);
    }

    @Test
    void soloQuedanActivosLosCamposMarcados() throws Exception {
        Settings settings = parse("""
                save:
                  fields:
                    profile: true
                    state: false
                    inventory: true
                """);

        assertTrue(settings.field("profile"));
        assertTrue(settings.field("inventory"));
        assertFalse(settings.field("state"));
        assertFalse(settings.field("statistics"));
    }

    @Test
    void aplicaLosValoresPorDefectoCuandoFaltanClaves() throws Exception {
        Settings settings = parse("server: \"solo\"\n");

        assertEquals("mongodb://localhost:27017", settings.mongoUri);
        assertEquals("players", settings.collection);
        assertEquals(300, settings.saveIntervalSeconds);
        assertTrue(settings.saveOnQuit);
        assertFalse(settings.redisEnabled);
        assertFalse(settings.proxyEnabled);
        assertEquals(3, settings.homesLimit);
    }

    @Test
    void elNombreEnElProxyCaeAlNombreDelServidorSiNoSeMapea() throws Exception {
        Settings settings = parse("""
                proxy:
                  enabled: true
                  server-names:
                    server-1: "supervivencia"
                """);

        assertEquals("supervivencia", settings.proxyName("server-1"));
        assertEquals("server-2", settings.proxyName("server-2"));
    }
}
