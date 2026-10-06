package ar.edu.iessf.servife.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.repository.TipoServicioRepository;
import ar.edu.iessf.servife.common.error.NegocioException;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.config.JwtPropiedades;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;
import ar.edu.iessf.servife.identidad.domain.Gestor;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.dto.LoginRequest;
import ar.edu.iessf.servife.identidad.dto.RefreshRequest;
import ar.edu.iessf.servife.identidad.dto.TokensResponse;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.GestorRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;
import ar.edu.iessf.servife.identidad.repository.RefreshTokenRepository;
import ar.edu.iessf.servife.identidad.service.Cuentas;
import ar.edu.iessf.servife.identidad.service.ServicioDeSesion;
import ar.edu.iessf.servife.identidad.service.ServicioDeTokens;

/** A2 (login) y A3 (refresh con rotación) contra PostgreSQL real. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ServicioDeSesionTest {

    private static final String CLAVE = "clave1234";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private Cuentas cuentas;
    @Autowired private ClienteRepository clientes;
    @Autowired private PrestadorRepository prestadores;
    @Autowired private GestorRepository gestores;
    @Autowired private TipoServicioRepository tiposServicio;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private JwtPropiedades propiedades;

    private RelojDePrueba reloj;
    private PasswordEncoder encoderEspiado;
    private ServicioDeTokens tokens;
    private ServicioDeSesion sesion;

    @BeforeEach
    void preparar() {
        reloj = new RelojDePrueba();
        encoderEspiado = spy(passwordEncoder);
        tokens = new ServicioDeTokens(refreshTokens, jwtEncoder, propiedades, reloj);
        sesion = new ServicioDeSesion(cuentas, tokens, encoderEspiado);
    }

    private Cliente guardarAna() {
        return clientes.saveAndFlush(new Cliente("Ana Pérez", "ana@mail.com", passwordEncoder.encode(CLAVE)));
    }

    @Test
    void loginConEmailConMayusculasYEspaciosDevuelveRolYTokens() {
        Cliente ana = guardarAna();

        TokensResponse emitidos = sesion.iniciar(new LoginRequest(" Ana@Mail.com ", CLAVE));

        assertThat(emitidos.rol()).isEqualTo(Rol.CLIENTE);
        assertThat(emitidos.refreshToken()).isNotBlank();
        Jwt jwt = jwtDecoder.decode(emitidos.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(ana.getUuid().toString());
        assertThat(jwt.<String>getClaim(JwtConfig.CLAIM_ROL)).isEqualTo("CLIENTE");
    }

    @Test
    void contraseniaMalaYEmailInexistenteDanElMismoError() {
        guardarAna();

        NegocioException mala = capturar(() -> sesion.iniciar(new LoginRequest("ana@mail.com", "otra-clave1")));
        NegocioException inexistente = capturar(() -> sesion.iniciar(new LoginRequest("nadie@mail.com", CLAVE)));

        for (NegocioException e : new NegocioException[] {mala, inexistente}) {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(e.getCodigo()).isEqualTo("CREDENCIALES_INVALIDAS");
            assertThat(e.getMessage()).isEqualTo("El correo o la contraseña no son correctos.");
        }
    }

    @Test
    void conEmailInexistenteIgualSeCorreBcryptContraUnHashFijo() {
        assertThatThrownBy(() -> sesion.iniciar(new LoginRequest("nadie@mail.com", CLAVE)))
            .isInstanceOf(NegocioException.class);

        verify(encoderEspiado).matches(eq(CLAVE), startsWith("$2a$10$"));
    }

    @Test
    void contraseniaDeMasDe72BytesDa401EnVezDeFallar() {
        guardarAna();
        String larga = "a1" + "ñ".repeat(68); // 70 caracteres, 138 bytes

        assertThatThrownBy(() -> sesion.iniciar(new LoginRequest("ana@mail.com", larga)))
            .isInstanceOfSatisfying(NegocioException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(e.getCodigo()).isEqualTo("CREDENCIALES_INVALIDAS");
            });
        verify(encoderEspiado, never()).matches(anyString(), anyString());
    }

    @Test
    void cuentaSuspendidaDa403() {
        Cliente ana = guardarAna();
        ana.setEstadoCuenta(EstadoCuenta.SUSPENDIDA);
        clientes.saveAndFlush(ana);

        assertThatThrownBy(() -> sesion.iniciar(new LoginRequest("ana@mail.com", CLAVE)))
            .isInstanceOfSatisfying(NegocioException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(e.getCodigo()).isEqualTo("CUENTA_SUSPENDIDA");
                assertThat(e.getMessage()).isEqualTo("Tu cuenta está suspendida.");
            });
    }

    @Test
    void cuentaSuspendidaConContraseniaMalaNoRevelaLaSuspension() {
        Cliente ana = guardarAna();
        ana.setEstadoCuenta(EstadoCuenta.SUSPENDIDA);
        clientes.saveAndFlush(ana);

        assertThatThrownBy(() -> sesion.iniciar(new LoginRequest("ana@mail.com", "otra-clave1")))
            .isInstanceOfSatisfying(NegocioException.class,
                e -> assertThat(e.getCodigo()).isEqualTo("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void cuentaDadaDeBajaEsComoSiNoExistiera() {
        Cliente ana = guardarAna();
        ana.darDeBaja();
        clientes.saveAndFlush(ana);

        assertThatThrownBy(() -> sesion.iniciar(new LoginRequest("ana@mail.com", CLAVE)))
            .isInstanceOfSatisfying(NegocioException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(e.getCodigo()).isEqualTo("CREDENCIALES_INVALIDAS");
            });
    }

    @Test
    void prestadorPendienteSiPuedeEntrar() {
        TipoServicio tipo = tiposServicio.findByEliminadoEnIsNullOrderByNombre().get(0);
        prestadores.saveAndFlush(new Prestador("Pablo Gas", "pablo@mail.com", passwordEncoder.encode(CLAVE), tipo));

        TokensResponse emitidos = sesion.iniciar(new LoginRequest("pablo@mail.com", CLAVE));

        assertThat(emitidos.rol()).isEqualTo(Rol.PRESTADOR);
    }

    @Test
    void gestorEntraPorElMismoLogin() {
        gestores.saveAndFlush(new Gestor("Gaby", "gaby@mail.com", passwordEncoder.encode(CLAVE)));

        assertThat(sesion.iniciar(new LoginRequest("gaby@mail.com", CLAVE)).rol()).isEqualTo(Rol.GESTOR);
    }

    @Test
    void renovarRotaElRefreshYElViejoYaNoSirve() {
        guardarAna();
        TokensResponse inicial = sesion.iniciar(new LoginRequest("ana@mail.com", CLAVE));

        TokensResponse nuevo = sesion.renovar(new RefreshRequest(inicial.refreshToken()));

        assertThat(nuevo.refreshToken()).isNotEqualTo(inicial.refreshToken());
        assertThat(nuevo.rol()).isEqualTo(Rol.CLIENTE);
        assertThat(jwtDecoder.decode(nuevo.accessToken()).getSubject())
            .isEqualTo(jwtDecoder.decode(inicial.accessToken()).getSubject());
        assertThatThrownBy(() -> sesion.renovar(new RefreshRequest(inicial.refreshToken())))
            .isInstanceOfSatisfying(NegocioException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(e.getCodigo()).isEqualTo("REFRESH_INVALIDO");
            });
        // el nuevo sigue sirviendo
        assertThat(sesion.renovar(new RefreshRequest(nuevo.refreshToken())).refreshToken())
            .isNotEqualTo(nuevo.refreshToken());
    }

    @Test
    void renovarConRefreshVencidoDa401() {
        guardarAna();
        TokensResponse inicial = sesion.iniciar(new LoginRequest("ana@mail.com", CLAVE));
        reloj.avanzar(Duration.ofDays(8));

        assertThatThrownBy(() -> sesion.renovar(new RefreshRequest(inicial.refreshToken())))
            .isInstanceOfSatisfying(NegocioException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(e.getCodigo()).isEqualTo("REFRESH_INVALIDO");
            });
    }

    @Test
    void renovarConCuentaSuspendidaDespuesDeEmitirDa403() {
        Cliente ana = guardarAna();
        TokensResponse inicial = sesion.iniciar(new LoginRequest("ana@mail.com", CLAVE));
        ana.setEstadoCuenta(EstadoCuenta.SUSPENDIDA);
        clientes.saveAndFlush(ana);

        assertThatThrownBy(() -> sesion.renovar(new RefreshRequest(inicial.refreshToken())))
            .isInstanceOfSatisfying(NegocioException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(e.getCodigo()).isEqualTo("CUENTA_SUSPENDIDA");
            });
    }

    @Test
    void renovarConCuentaDadaDeBajaDa401RefreshInvalido() {
        Cliente ana = guardarAna();
        TokensResponse inicial = sesion.iniciar(new LoginRequest("ana@mail.com", CLAVE));
        ana.darDeBaja();
        clientes.saveAndFlush(ana);

        assertThatThrownBy(() -> sesion.renovar(new RefreshRequest(inicial.refreshToken())))
            .isInstanceOfSatisfying(NegocioException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(e.getCodigo()).isEqualTo("REFRESH_INVALIDO");
            });
    }

    @Test
    void renovarConCuentaInexistenteDa401RefreshInvalido() {
        TokensResponse huerfano = tokens.emitir(UUID.randomUUID(), Rol.CLIENTE);

        assertThatThrownBy(() -> sesion.renovar(new RefreshRequest(huerfano.refreshToken())))
            .isInstanceOfSatisfying(NegocioException.class,
                e -> assertThat(e.getCodigo()).isEqualTo("REFRESH_INVALIDO"));
    }

    private static NegocioException capturar(Runnable accion) {
        try {
            accion.run();
        } catch (NegocioException e) {
            return e;
        }
        throw new AssertionError("Se esperaba una NegocioException");
    }
}
