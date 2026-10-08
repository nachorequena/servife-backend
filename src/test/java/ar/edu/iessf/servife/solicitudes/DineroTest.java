package ar.edu.iessf.servife.solicitudes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ar.edu.iessf.servife.solicitudes.service.Dinero;

class DineroTest {

    @Test
    void formateaPesosConSeparadorDeMilesYCentavosSoloSiHace() {
        assertThat(Dinero.pesos(800_000L)).isEqualTo("$ 8.000");
        assertThat(Dinero.pesos(800_050L)).isEqualTo("$ 8.000,50");
        assertThat(Dinero.pesos(0L)).isEqualTo("$ 0");
        assertThat(Dinero.pesos(5L)).isEqualTo("$ 0,05");
        assertThat(Dinero.pesos(123_456_789L)).isEqualTo("$ 1.234.567,89");
    }
}
