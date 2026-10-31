package co.proyopti.rutas.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PolylineDecoderTest {
    @Test void rechazaTrazadosVaciosTruncadosOFueraDeRango() {
        for (String encoded : new String[]{"", "_", "?", "~~", "~~~~~~~~~~~~", "\u0000"}) {
            assertThatThrownBy(() -> PolylineDecoder.decode(encoded)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
