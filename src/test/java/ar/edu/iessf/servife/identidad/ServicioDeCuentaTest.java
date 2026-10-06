package ar.edu.iessf.servife.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.repository.TipoServicioRepository;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;
import ar.edu.iessf.servife.identidad.domain.Gestor;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.dto.ActualizarUsuarioRequest;
import ar.edu.iessf.servife.identidad.dto.CambiarContraseniaRequest;
import ar.edu.iessf.servife.identidad.dto.UsuarioResponse;
import ar.edu.iessf.servife.identidad.mapper.UsuarioMapper;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.GestorRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;
import ar.edu.iessf.servife.identidad.service.Cuentas;
import ar.edu.iessf.servife.identidad.service.ServicioDeCuenta;
import ar.edu.iessf.servife.identidad.service.ServicioDeTokens;

/** A4 (sesión), A6 (datos de la cuenta) y A7 (cambio de contraseña) contra PostgreSQL real. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ServicioDeCuentaTest {

    private static final String CLAVE = "clave1234";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private Cuentas cuentas;
    @Autowired private ClienteRepository clientes;
    @Autowired private PrestadorRepository prestadores;
    @Autowired private GestorRepository gestores;
    @Autowired private TipoServicioRepository tiposServicio;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UsuarioMapper mapper;

    private ServicioDeTokens tokens;
    private ServicioDeCuenta servicio;

    @BeforeEach
    void preparar() {
        tokens = mock(ServicioDeTokens.class);
        servicio = new ServicioDeCuenta(cuentas, mapper, passwordEncoder, tokens);
    }

    private Cliente guardarAna() {
        return clientes.saveAndFlush(new Cliente("Ana Pérez", "ana@mail.com", passwordEncoder.encode(CLAVE)));
    }

    private Prestador guardarPrestador() {
        TipoServicio tipo = tiposServicio.findAll().get(0);
        return prestadores.saveAndFlush(new Prestador("Pedro Gas", "pedro@mail.com", passwordEncoder.encode(CLAVE), tipo));
    }

    private Gestor guardarGestor() {
        return gestores.saveAndFlush(new Gestor("Gina Gestora", "gina@mail.com", passwordEncoder.encode(CLAVE)));
    }

    @Test
    void obtenerDeClienteNoTrajeEstadoDeValidacion() {
        Cliente ana = guardarAna();

        UsuarioResponse r = servicio.obtener(ana.getUuid(), Rol.CLIENTE);

        assertThat(r.uuid()).isEqualTo(ana.getUuid());
        assertThat(r.email()).isEqualTo("ana@mail.com");
        assertThat(r.estadoValidacion()).isNull();
    }

    @Test
    void obtenerDePrestadorIncluyeEstadoDeValidacion() {
        Prestador p = guardarPrestador();

        assertThat(servicio.obtener(p.getUuid(), Rol.PRESTADOR).estadoValidacion())
            .isEqualTo(EstadoValidacion.PENDIENTE);
    }

    @Test
    void obtenerDeCuentaInexistenteODadaDeBajaEsNoEncontrada() {
        Cliente ana = guardarAna();
        UUID uuid = ana.getUuid();
        assertThatThrownBy(() -> servicio.obtener(UUID.randomUUID(), Rol.CLIENTE))
            .isInstanceOf(RecursoNoEncontradoException.class);

        ana.darDeBaja();
        clientes.saveAndFlush(ana);

        assertThatThrownBy(() -> servicio.obtener(uuid, Rol.CLIENTE))
            .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void actualizarClienteRecortaEspaciosYDevuelveLosDatos() {
        Cliente ana = guardarAna();

        UsuarioResponse r = servicio.actualizar(ana.getUuid(), Rol.CLIENTE, new ActualizarUsuarioRequest(
            "  Ana María Pérez ", " 3425551234 ", " Calle 1 ", LocalDate.of(1990, 5, 17)));

        assertThat(r.nombreApellido()).isEqualTo("Ana María Pérez");
        assertThat(r.telefono()).isEqualTo("3425551234");
        assertThat(r.direccion()).isEqualTo("Calle 1");
        assertThat(r.fecNacimiento()).isEqualTo(LocalDate.of(1990, 5, 17));
        assertThat(clientes.findByUuidAndEliminadoEnIsNull(ana.getUuid()).orElseThrow().getTelefono())
            .isEqualTo("3425551234");
    }

    @Test
    void actualizarGuardaNullEnTelefonoYDireccionEnBlanco() {
        Cliente ana = guardarAna();
        ana.setTelefono("111");
        ana.setDireccion("Calle 1");
        clientes.saveAndFlush(ana);

        UsuarioResponse r = servicio.actualizar(ana.getUuid(), Rol.CLIENTE,
            new ActualizarUsuarioRequest("Ana", "   ", "", null));

        assertThat(r.telefono()).isNull();
        assertThat(r.direccion()).isNull();
        assertThat(r.fecNacimiento()).isNull();
    }

    @Test
    void actualizarPrestadorConservaElEstadoDeValidacion() {
        Prestador p = guardarPrestador();

        UsuarioResponse r = servicio.actualizar(p.getUuid(), Rol.PRESTADOR,
            new ActualizarUsuarioRequest("Pedro G.", "999", null, null));

        assertThat(r.nombreApellido()).isEqualTo("Pedro G.");
        assertThat(r.telefono()).isEqualTo("999");
        assertThat(r.estadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);
    }

    @Test
    void actualizarGestorSoloCambiaElNombre() {
        Gestor g = guardarGestor();

        UsuarioResponse r = servicio.actualizar(g.getUuid(), Rol.GESTOR,
            new ActualizarUsuarioRequest("Gina G.", null, null, null));

        assertThat(r.nombreApellido()).isEqualTo("Gina G.");
        assertThat(r.telefono()).isNull();
    }

    @Test
    void actualizarGestorConTelefonoDireccionOFechaEsValidacionDeEseCampo() {
        Gestor g = guardarGestor();
        UUID uuid = g.getUuid();

        assertThatThrownBy(() -> servicio.actualizar(uuid, Rol.GESTOR,
            new ActualizarUsuarioRequest("Gina", "123", null, null)))
            .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getCampo()).isEqualTo("telefono"));
        assertThatThrownBy(() -> servicio.actualizar(uuid, Rol.GESTOR,
            new ActualizarUsuarioRequest("Gina", null, "Calle", null)))
            .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getCampo()).isEqualTo("direccion"));
        assertThatThrownBy(() -> servicio.actualizar(uuid, Rol.GESTOR,
            new ActualizarUsuarioRequest("Gina", null, null, LocalDate.of(1980, 1, 1))))
            .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getCampo()).isEqualTo("fecNacimiento"));
    }

    @Test
    void actualizarCuentaInexistenteEsNoEncontrada() {
        assertThatThrownBy(() -> servicio.actualizar(UUID.randomUUID(), Rol.CLIENTE,
            new ActualizarUsuarioRequest("Ana", null, null, null)))
            .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void cambiarContraseniaGuardaElHashNuevoYRevocaLosRefresh() {
        Cliente ana = guardarAna();

        servicio.cambiarContrasenia(ana.getUuid(), Rol.CLIENTE, new CambiarContraseniaRequest(CLAVE, "nueva-clave9"));

        Cliente guardada = clientes.findByUuidAndEliminadoEnIsNull(ana.getUuid()).orElseThrow();
        assertThat(passwordEncoder.matches("nueva-clave9", guardada.getContrasenia())).isTrue();
        assertThat(guardada.getContrasenia()).doesNotContain("nueva-clave9");
        verify(tokens).revocarTodos(ana.getUuid());
    }

    @Test
    void cambiarContraseniaConActualIncorrectaNoCambiaNadaNiRevoca() {
        Cliente ana = guardarAna();
        UUID uuid = ana.getUuid();

        assertThatThrownBy(() -> servicio.cambiarContrasenia(uuid, Rol.CLIENTE,
            new CambiarContraseniaRequest("incorrecta1", "nueva-clave9")))
            .isInstanceOfSatisfying(ValidacionException.class, e -> {
                assertThat(e.getCampo()).isEqualTo("contraseniaActual");
                assertThat(e.getDetalle()).isEqualTo("no coincide");
            });

        assertThat(passwordEncoder.matches(CLAVE,
            clientes.findByUuidAndEliminadoEnIsNull(uuid).orElseThrow().getContrasenia())).isTrue();
        verify(tokens, never()).revocarTodos(uuid);
    }

    @Test
    void cambiarContraseniaDeCuentaInexistenteEsNoEncontrada() {
        assertThatThrownBy(() -> servicio.cambiarContrasenia(UUID.randomUUID(), Rol.PRESTADOR,
            new CambiarContraseniaRequest(CLAVE, "nueva-clave9")))
            .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
