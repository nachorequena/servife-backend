package ar.edu.iessf.servife.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;


import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ar.edu.iessf.servife.common.correo.EnviadorDeCorreos;
import ar.edu.iessf.servife.identidad.service.CodigoDeRecuperacionEmitido;
import ar.edu.iessf.servife.identidad.service.EnvioDeCodigoDeRecuperacion;

/** El envío del código por correo: contenido y tolerancia a fallas del SMTP. */
class EnvioDeCodigoDeRecuperacionTest {

    private final EnviadorDeCorreos correos = mock(EnviadorDeCorreos.class);
    private final EnvioDeCodigoDeRecuperacion envio = new EnvioDeCodigoDeRecuperacion(correos);

    @Test
    void enviaElCodigoDeSeisDigitosYLaVigencia() {
        envio.enviar(new CodigoDeRecuperacionEmitido("ana@mail.com", "042917", 15));

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(correos).enviar(eq("ana@mail.com"), eq("Tu código de ServiFe"), texto.capture());
        assertThat(texto.getValue()).contains("es 042917.").contains("15 minutos");
    }

    @Test
    void siElEnviadorFallaLaExcepcionSeTragaYNoSeFiltra() {
        doThrow(new IllegalStateException("SMTP caído con 042917")).when(correos)
            .enviar(anyString(), anyString(), anyString());

        assertThatCode(() -> envio.enviar(new CodigoDeRecuperacionEmitido("ana@mail.com", "042917", 15)))
            .doesNotThrowAnyException();
    }

    @Test
    void elEventoNoMuestraElCodigoEnSuToString() {
        assertThat(new CodigoDeRecuperacionEmitido("ana@mail.com", "042917", 15).toString()).doesNotContain("042917");
    }
}
