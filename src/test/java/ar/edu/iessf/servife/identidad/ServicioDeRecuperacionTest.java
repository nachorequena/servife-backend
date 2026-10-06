package ar.edu.iessf.servife.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ar.edu.iessf.servife.common.correo.EnviadorDeCorreos;
import ar.edu.iessf.servife.common.error.NegocioException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.CodigoRecuperacion;
import ar.edu.iessf.servife.identidad.dto.ConfirmarRecuperacionRequest;
import ar.edu.iessf.servife.identidad.dto.RecuperarRequest;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.CodigoRecuperacionRepository;
import ar.edu.iessf.servife.identidad.service.Hashes;
import ar.edu.iessf.servife.identidad.service.ServicioDeRecuperacion;
import ar.edu.iessf.servife.identidad.service.ServicioDeTokens;

/**
 * A8 y A9 contra PostgreSQL real. Sin @Transactional a propósito: así se comprueba que el intento
 * fallido queda guardado aunque confirmar() termine en excepción.
 */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
class ServicioDeRecuperacionTest {

    private static final String CLAVE = "clave1234";
    private static final String NUEVA = "nueva-clave9";
    private static final Pattern SEIS_DIGITOS = Pattern.compile("\\b(\\d{6})\\b");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class RelojConfigDePrueba {
        @Bean
        @Primary
        RelojDePrueba relojDePrueba() {
            return new RelojDePrueba();
        }
    }

    @Autowired private ClienteRepository clientes;
    @Autowired private CodigoRecuperacionRepository codigos;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ServicioDeRecuperacion servicio;
    @Autowired private RelojDePrueba reloj;

    @MockitoBean private EnviadorDeCorreos correos;
    @MockitoBean private ServicioDeTokens tokens;

    @BeforeEach
    void preparar() {
        limpiar();
    }

    @AfterEach
    void limpiar() {
        codigos.deleteAll();
        clientes.deleteAll();
    }

    private Cliente guardarAna() {
        return clientes.saveAndFlush(new Cliente("Ana Pérez", "ana@mail.com", passwordEncoder.encode(CLAVE)));
    }

    /** Pide el código y lo lee del correo enviado (la última vez que se llamó a enviar). */
    private String pedirCodigo() {
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        servicio.solicitar(new RecuperarRequest("ana@mail.com"));
        verify(correos, org.mockito.Mockito.atLeastOnce()).enviar(anyString(), anyString(), texto.capture());
        List<String> textos = texto.getAllValues();
        Matcher m = SEIS_DIGITOS.matcher(textos.get(textos.size() - 1));
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private void confirmar(String codigo) {
        servicio.confirmar(new ConfirmarRecuperacionRequest("ana@mail.com", codigo, NUEVA));
    }

    private static void esCodigoInvalido(Throwable e) {
        assertThat(e).isInstanceOfSatisfying(NegocioException.class, n -> {
            assertThat(n.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(n.getCodigo()).isEqualTo("CODIGO_INVALIDO");
            assertThat(n.getMessage()).isEqualTo("El código no es válido o venció. Pedí uno nuevo.");
        });
    }

    private boolean contraseniaEsLaNueva() {
        return passwordEncoder.matches(NUEVA, clientes.findAll().get(0).getContrasenia());
    }

    @Test
    void solicitarConEmailExistenteGuardaElHashYEnviaUnCorreoConSeisDigitos() {
        guardarAna();

        servicio.solicitar(new RecuperarRequest(" Ana@Mail.com "));

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(correos, times(1)).enviar(org.mockito.ArgumentMatchers.eq("ana@mail.com"),
            org.mockito.ArgumentMatchers.eq("Tu código de ServiFe"), texto.capture());
        Matcher m = SEIS_DIGITOS.matcher(texto.getValue());
        assertThat(m.find()).isTrue();
        String codigo = m.group(1);
        assertThat(texto.getValue()).contains("15 minutos");

        List<CodigoRecuperacion> guardados = codigos.findAll();
        assertThat(guardados).hasSize(1);
        assertThat(guardados.get(0).getEmail()).isEqualTo("ana@mail.com");
        assertThat(guardados.get(0).getCodigoHash()).isNotEqualTo(codigo).isEqualTo(Hashes.sha256Hex(codigo));
        assertThat(guardados.get(0).getIntentos()).isZero();
    }

    @Test
    void solicitarConEmailInexistenteNoGuardaNiEnvia() {
        servicio.solicitar(new RecuperarRequest("nadie@mail.com"));

        assertThat(codigos.findAll()).isEmpty();
        verify(correos, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void solicitarConCuentaDadaDeBajaNoGuardaNiEnvia() {
        Cliente ana = guardarAna();
        ana.darDeBaja();
        clientes.saveAndFlush(ana);

        servicio.solicitar(new RecuperarRequest("ana@mail.com"));

        assertThat(codigos.findAll()).isEmpty();
        verify(correos, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void unSegundoPedidoInvalidaElPrimero() {
        guardarAna();
        String primero = pedirCodigo();
        String segundo = pedirCodigo();
        // Con 1 en 1.000.000 de probabilidad coinciden; el hash igual no serviría porque el primero quedó usado.

        List<CodigoRecuperacion> guardados = codigos.findAll();
        assertThat(guardados).hasSize(2);
        assertThat(guardados.stream().filter(c -> c.getUsadoEn() == null)).hasSize(1);

        if (!primero.equals(segundo)) {
            assertThatThrownBy(() -> confirmar(primero)).satisfies(ServicioDeRecuperacionTest::esCodigoInvalido);
        }
        confirmar(segundo);
        assertThat(contraseniaEsLaNueva()).isTrue();
    }

    @Test
    void confirmarConElCodigoCorrectoCambiaLaContraseniaMarcaUsadoYRevocaLosRefresh() {
        Cliente ana = guardarAna();
        String hashAnterior = ana.getContrasenia();
        String codigo = pedirCodigo();

        confirmar(codigo);

        Cliente guardada = clientes.findAll().get(0);
        assertThat(guardada.getContrasenia()).isNotEqualTo(hashAnterior);
        assertThat(passwordEncoder.matches(NUEVA, guardada.getContrasenia())).isTrue();
        assertThat(codigos.findAll().get(0).getUsadoEn()).isNotNull();
        verify(tokens).revocarTodos(ana.getUuid());
    }

    @Test
    void elCodigoNoSirveDosVeces() {
        guardarAna();
        String codigo = pedirCodigo();
        confirmar(codigo);

        assertThatThrownBy(() -> confirmar(codigo)).satisfies(ServicioDeRecuperacionTest::esCodigoInvalido);
    }

    @Test
    void unCodigoIncorrectoSumaUnIntentoQueQuedaGuardadoYNoCambiaNada() {
        Cliente ana = guardarAna();
        String correcto = pedirCodigo();
        String incorrecto = correcto.equals("000000") ? "000001" : "000000";

        assertThatThrownBy(() -> confirmar(incorrecto)).satisfies(ServicioDeRecuperacionTest::esCodigoInvalido);

        assertThat(codigos.findAll().get(0).getIntentos()).isEqualTo(1);
        assertThat(contraseniaEsLaNueva()).isFalse();
        verify(tokens, never()).revocarTodos(ana.getUuid());
        // y el correcto todavía sirve
        confirmar(correcto);
        assertThat(contraseniaEsLaNueva()).isTrue();
    }

    @Test
    void conCincoFallosElCodigoQuedaInvalidoAunqueDespuesVengaElCorrecto() {
        Cliente ana = guardarAna();
        String correcto = pedirCodigo();
        String incorrecto = correcto.equals("000000") ? "000001" : "000000";

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> confirmar(incorrecto)).satisfies(ServicioDeRecuperacionTest::esCodigoInvalido);
        }
        assertThat(codigos.findAll().get(0).getIntentos()).isEqualTo(5);

        assertThatThrownBy(() -> confirmar(correcto)).satisfies(ServicioDeRecuperacionTest::esCodigoInvalido);
        assertThat(contraseniaEsLaNueva()).isFalse();
        verify(tokens, never()).revocarTodos(ana.getUuid());
    }

    @Test
    void unCodigoVencidoEsInvalido() {
        guardarAna();
        String codigo = pedirCodigo();
        reloj.avanzar(Duration.ofMinutes(16));

        assertThatThrownBy(() -> confirmar(codigo)).satisfies(ServicioDeRecuperacionTest::esCodigoInvalido);
        assertThat(contraseniaEsLaNueva()).isFalse();
    }

    @Test
    void unCodigoAMenosDeQuinceMinutosSigueVigente() {
        guardarAna();
        String codigo = pedirCodigo();
        reloj.avanzar(Duration.ofMinutes(14));

        confirmar(codigo);

        assertThat(contraseniaEsLaNueva()).isTrue();
    }

    @Test
    void confirmarSinHaberPedidoCodigoEsInvalido() {
        guardarAna();

        assertThatThrownBy(() -> confirmar("123456")).satisfies(ServicioDeRecuperacionTest::esCodigoInvalido);
    }

    @Test
    void confirmarConContraseniaDeMasDe72BytesEsValidacionDeEseCampo() {
        guardarAna();
        String codigo = pedirCodigo();

        assertThatThrownBy(() -> servicio.confirmar(
            new ConfirmarRecuperacionRequest("ana@mail.com", codigo, "ñ".repeat(40) + "1")))
            .isInstanceOfSatisfying(ValidacionException.class,
                e -> assertThat(e.getCampo()).isEqualTo("contraseniaNueva"));
    }
}
