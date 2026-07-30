package dev.rui.prueba.teleport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HomeServiceTest {

    @Test
    void pasaAMinusculas() {
        assertEquals("casadellago", HomeService.normalize("CasaDelLago"));
    }

    @Test
    void quitaLoQueNoEsSeguroParaUnaClaveDeMongo() {
        assertEquals("micasa", HomeService.normalize("mi.casa"));
        assertEquals("micasa", HomeService.normalize("mi casa"));
        assertEquals("casa-2_b", HomeService.normalize("casa-2_b"));
    }

    @Test
    void unNombreVacioOSoloDeSimbolosCaeEnCasa() {
        assertEquals("casa", HomeService.normalize(""));
        assertEquals("casa", HomeService.normalize("$$$"));
    }

    @Test
    void recortaLosNombresLargos() {
        String nombre = HomeService.normalize("unnombremuylargoquenocabe");

        assertEquals(16, nombre.length());
        assertTrue("unnombremuylargoquenocabe".startsWith(nombre));
    }
}
