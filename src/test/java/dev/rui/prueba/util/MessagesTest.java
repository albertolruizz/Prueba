package dev.rui.prueba.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessagesTest {

    @BeforeEach
    void cargar() throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString("""
                prefix: "[Prueba] "
                homes:
                  saved: "Hogar {name} guardado."
                  limit-reached: "Ya tienes {limit} hogares, {name} no cabe."
                """);
        Messages.load(cfg);
    }

    @Test
    void sustituyeUnMarcador() {
        assertEquals("Hogar base guardado.", Messages.raw("homes.saved", "name", "base"));
    }

    @Test
    void sustituyeVariosMarcadores() {
        assertEquals("Ya tienes 3 hogares, mina no cabe.",
                Messages.raw("homes.limit-reached", "limit", "3", "name", "mina"));
    }

    @Test
    void unaClaveQueFaltaSeVeEnElChatEnVezDeQuedarseEnBlanco() {
        String texto = Messages.raw("homes.no-existe");

        assertTrue(texto.contains("homes.no-existe"), texto);
    }

    @Test
    void unMarcadorSinValorSeQuedaComoEsta() {
        assertEquals("Hogar {name} guardado.", Messages.raw("homes.saved"));
    }
}
