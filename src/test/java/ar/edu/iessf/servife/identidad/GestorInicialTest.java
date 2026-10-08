package ar.edu.iessf.servife.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import ar.edu.iessf.servife.identidad.domain.Gestor;
import ar.edu.iessf.servife.identidad.repository.GestorRepository;
import ar.edu.iessf.servife.identidad.service.Cuentas;
import ar.edu.iessf.servife.identidad.service.GestorInicial;
import ar.edu.iessf.servife.identidad.service.GestorInicialPropiedades;

@ExtendWith(MockitoExtension.class)
class GestorInicialTest {

    private static final String CONTRASENIA = "Secreta123";

    @Mock GestorRepository gestores;
    @Mock Cuentas cuentas;
    @Mock PasswordEncoder encoder;

    private GestorInicial con(String email, String contrasenia, String nombre) {
        return new GestorInicial(new GestorInicialPropiedades(email, contrasenia, nombre), gestores, cuentas, encoder);
    }

    @Test
    void sinVariablesNoHaceNada() {
        con("", "", "").run(null);
        con(null, null, null).run(null);

        verify(gestores, never()).save(any());
    }

    @Test
    void conVariablesYSinGestoresCreaUnoConEmailNormalizadoYHash() {
        when(gestores.count()).thenReturn(0L);
        when(cuentas.emailRegistrado(" Admin@Mail.com ")).thenReturn(false);
        when(encoder.encode(CONTRASENIA)).thenReturn("hash");

        con(" Admin@Mail.com ", CONTRASENIA, "").run(null);

        ArgumentCaptor<Gestor> captor = ArgumentCaptor.forClass(Gestor.class);
        verify(gestores).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("admin@mail.com");
        assertThat(captor.getValue().getContrasenia()).isEqualTo("hash");
        assertThat(captor.getValue().getNombreApellido()).isEqualTo("Gestor inicial");
    }

    @Test
    void usaElNombreConfigurado() {
        when(gestores.count()).thenReturn(0L);
        when(encoder.encode(CONTRASENIA)).thenReturn("hash");

        con("a@mail.com", CONTRASENIA, "Ana Gestora").run(null);

        ArgumentCaptor<Gestor> captor = ArgumentCaptor.forClass(Gestor.class);
        verify(gestores).save(captor.capture());
        assertThat(captor.getValue().getNombreApellido()).isEqualTo("Ana Gestora");
    }

    @Test
    void siYaHayGestoresNoHaceNada() {
        when(gestores.count()).thenReturn(1L);

        con("a@mail.com", CONTRASENIA, "").run(null);

        verify(gestores, never()).save(any());
    }

    @Test
    void contraseniaDebilFallaSinRevelarla() {
        when(gestores.count()).thenReturn(0L);

        assertThatThrownBy(() -> con("a@mail.com", "corta", "").run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("GESTOR_INICIAL_CONTRASENIA")
            .hasMessageNotContaining("corta");
        verify(gestores, never()).save(any());
    }

    @Test
    void contraseniaDeMasDe72BytesFalla() {
        when(gestores.count()).thenReturn(0L);
        String larga = "ñ".repeat(40) + "a1";

        assertThatThrownBy(() -> con("a@mail.com", larga, "").run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageNotContaining(larga);
        verify(gestores, never()).save(any());
    }

    @Test
    void emailYaRegistradoComoOtraCuentaFalla() {
        when(gestores.count()).thenReturn(0L);
        when(cuentas.emailRegistrado("a@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> con("a@mail.com", CONTRASENIA, "").run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("a@mail.com")
            .hasMessageNotContaining(CONTRASENIA);
        verify(gestores, never()).save(any());
    }

    @Test
    void soloUnaDeLasDosVariablesEsConfiguracionIncorrecta() {
        assertThatThrownBy(() -> con("a@mail.com", "", "").run(null))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> con("", CONTRASENIA, "").run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageNotContaining(CONTRASENIA);
    }
}
