package ar.edu.iessf.servife.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ar.edu.iessf.servife.common.correo.EnviadorDeCorreos;
import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.gestion.dto.ActualizarUsuarioAdminRequest;
import ar.edu.iessf.servife.gestion.dto.CambioDeEstadoDeCuentaRequest;
import ar.edu.iessf.servife.gestion.dto.CrearGestorRequest;
import ar.edu.iessf.servife.gestion.dto.UsuarioAdminResponse;
import ar.edu.iessf.servife.gestion.service.ServicioDeUsuarios;
import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;
import ar.edu.iessf.servife.identidad.service.ServicioDeTokens;

/** E3, E4, E13, E14 y E15 contra PostgreSQL real. Cada test parte de las tres tablas de cuentas vacías salvo el gestor que actúa. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
class ServicioDeUsuariosTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private ServicioDeUsuarios servicio;
    @Autowired private ServicioDeTokens tokens;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private EnviadorDeCorreos correos;

    private UUID yo;

    @BeforeEach
    void limpiar() {
        reset(correos);
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM notificaciones");
        jdbc.update("DELETE FROM clientes");
        jdbc.update("DELETE FROM prestadores");
        jdbc.update("DELETE FROM gestores");
        yo = cuenta("gestores", "Yo Gestor", "yo@mail.com", 10, false);
    }

    /** minutosAtras mayor = más viejo. */
    private UUID cuenta(String tabla, String nombre, String email, int minutosAtras, boolean baja) {
        UUID uuid = UUID.randomUUID();
        String tipo = tabla.equals("prestadores") ? ", id_tipo_servicio" : "";
        String valorTipo = tabla.equals("prestadores") ? ", (SELECT id_tipo_servicio FROM tipos_servicio WHERE nombre = 'Gas')" : "";
        jdbc.update("INSERT INTO " + tabla + " (uuid, nombre_apellido, email, contrasenia, creado_en, eliminado_en" + tipo + ")"
            + " VALUES (?, ?, ?, 'hash', now() - make_interval(mins => ?), CASE WHEN ? THEN now() END" + valorTipo + ")",
            uuid, nombre, email, minutosAtras, baja);
        return uuid;
    }

    private Pagina<UsuarioAdminResponse> listar(Rol rol, EstadoCuenta estado, String texto) {
        return servicio.listar(rol, estado, texto, PageRequest.of(0, 20));
    }

    private static List<String> nombres(Pagina<UsuarioAdminResponse> p) {
        return p.contenido().stream().map(UsuarioAdminResponse::nombreApellido).toList();
    }

    private String estadoDe(String tabla, UUID uuid) {
        return jdbc.queryForObject("SELECT estado_cuenta FROM " + tabla + " WHERE uuid = ?", String.class, uuid);
    }

    private int refreshVigentes(UUID uuid) {
        return jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE uuid_usuario = ? AND revocado_en IS NULL",
            Integer.class, uuid);
    }

    // ─── E3 ─────────────────────────────────────────────────────────────────

    @Test
    void listaLasTresTablasSinLasDadasDeBajaLosMasNuevosPrimero() {
        cuenta("clientes", "Cliente Viejo", "c1@mail.com", 60, false);
        cuenta("prestadores", "Prestador Nuevo", "p1@mail.com", 1, false);
        cuenta("clientes", "Cliente De Baja", "c2@mail.com", 5, true);

        Pagina<UsuarioAdminResponse> r = listar(null, null, null);

        assertThat(nombres(r)).containsExactly("Prestador Nuevo", "Yo Gestor", "Cliente Viejo");
        assertThat(r.totalElementos()).isEqualTo(3);
        UsuarioAdminResponse prestador = r.contenido().get(0);
        assertThat(prestador.rol()).isEqualTo(Rol.PRESTADOR);
        assertThat(prestador.tipoServicio()).isEqualTo("Gas");
        assertThat(prestador.estadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);
        assertThat(r.contenido().get(1).telefono()).isNull();
        assertThat(r.contenido().get(1).estadoValidacion()).isNull();
    }

    @Test
    void filtraPorRolEstadoYTextoEnNombreEmailOUuid() {
        UUID ana = cuenta("clientes", "Ana Gómez", "ana@mail.com", 3, false);
        cuenta("prestadores", "Anabel Pérez", "plomera@mail.com", 2, false);
        jdbc.update("UPDATE clientes SET estado_cuenta = 'SUSPENDIDA' WHERE uuid = ?", ana);

        assertThat(nombres(listar(Rol.CLIENTE, null, null))).containsExactly("Ana Gómez");
        assertThat(nombres(listar(null, EstadoCuenta.SUSPENDIDA, null))).containsExactly("Ana Gómez");
        assertThat(nombres(listar(null, null, "  ANA "))).containsExactly("Anabel Pérez", "Ana Gómez");
        assertThat(nombres(listar(null, null, "plomera@"))).containsExactly("Anabel Pérez");
        assertThat(nombres(listar(null, null, ana.toString()))).containsExactly("Ana Gómez");
        assertThat(nombres(listar(Rol.GESTOR, EstadoCuenta.SUSPENDIDA, null))).isEmpty();
        // Los comodines de LIKE se buscan literalmente.
        assertThat(nombres(listar(null, null, "%"))).isEmpty();
    }

    @Test
    void paginaYTextoLargo() {
        for (int i = 0; i < 3; i++) {
            cuenta("clientes", "Cliente " + i, "c" + i + "@mail.com", i, false);
        }
        Pagina<UsuarioAdminResponse> segunda = servicio.listar(null, null, null, PageRequest.of(1, 2));
        assertThat(segunda.totalElementos()).isEqualTo(4);
        assertThat(segunda.totalPaginas()).isEqualTo(2);
        assertThat(segunda.contenido()).hasSize(2);
        assertThatThrownBy(() -> listar(null, null, "x".repeat(121))).isInstanceOf(ValidacionException.class);
    }

    // ─── E13 ────────────────────────────────────────────────────────────────

    @Test
    void crearGestorLoDejaListoParaIngresar() {
        UsuarioAdminResponse r = servicio.crearGestor(new CrearGestorRequest(" Nueva Gestora ", "Nueva@Mail.com", "clave1234"));

        assertThat(r.rol()).isEqualTo(Rol.GESTOR);
        assertThat(r.email()).isEqualTo("nueva@mail.com");
        assertThat(r.nombreApellido()).isEqualTo("Nueva Gestora");
        assertThat(r.estadoCuenta()).isEqualTo(EstadoCuenta.ACTIVA);
        String hash = jdbc.queryForObject("SELECT contrasenia FROM gestores WHERE uuid = ?", String.class, r.uuid());
        assertThat(encoder.matches("clave1234", hash)).isTrue();
    }

    @Test
    void crearGestorConEmailDeCualquierCuentaAunqueEsteDadaDeBajaEs409() {
        cuenta("clientes", "Cliente", "usado@mail.com", 1, false);
        cuenta("prestadores", "De Baja", "baja@mail.com", 1, true);

        for (String email : List.of("USADO@mail.com", "baja@mail.com", "yo@mail.com")) {
            assertThatThrownBy(() -> servicio.crearGestor(new CrearGestorRequest("G", email, "clave1234")))
                .isInstanceOfSatisfying(ConflictoException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("EMAIL_YA_REGISTRADO");
                    assertThat(e.getErrores()).extracting(c -> c.campo()).containsExactly("email");
                });
        }
    }

    @Test
    void crearGestorConContraseniaDeMasDe72BytesEs400() {
        String larga = "ñ".repeat(37) + "1";
        assertThatThrownBy(() -> servicio.crearGestor(new CrearGestorRequest("G", "g@mail.com", larga)))
            .isInstanceOf(ValidacionException.class);
    }

    // ─── E14 ────────────────────────────────────────────────────────────────

    @Test
    void actualizarCambiaNombreEmailYTelefono() {
        UUID uuid = cuenta("prestadores", "Pedro", "pedro@mail.com", 1, false);

        UsuarioAdminResponse r = servicio.actualizar(uuid, new ActualizarUsuarioAdminRequest(" Pedro Gómez ", "PedroG@mail.com", " 342 555 "));

        assertThat(r.nombreApellido()).isEqualTo("Pedro Gómez");
        assertThat(r.email()).isEqualTo("pedrog@mail.com");
        assertThat(r.telefono()).isEqualTo("342 555");
        assertThat(jdbc.queryForObject("SELECT email FROM prestadores WHERE uuid = ?", String.class, uuid))
            .isEqualTo("pedrog@mail.com");

        UsuarioAdminResponse sinTelefono = servicio.actualizar(uuid, new ActualizarUsuarioAdminRequest("Pedro Gómez", "pedrog@mail.com", " "));
        assertThat(sinTelefono.telefono()).isNull();
    }

    @Test
    void actualizarConElEmailDeOtraCuentaEs409YConElPropioNo() {
        UUID ana = cuenta("clientes", "Ana", "ana@mail.com", 1, false);
        cuenta("gestores", "Otro", "otro@mail.com", 1, false);

        assertThatThrownBy(() -> servicio.actualizar(ana, new ActualizarUsuarioAdminRequest("Ana", "otro@mail.com", null)))
            .isInstanceOfSatisfying(ConflictoException.class, e -> assertThat(e.getCodigo()).isEqualTo("EMAIL_YA_REGISTRADO"));
        assertThat(servicio.actualizar(ana, new ActualizarUsuarioAdminRequest("Ana María", "ANA@mail.com", null)).nombreApellido())
            .isEqualTo("Ana María");
    }

    @Test
    void unGestorNoTieneTelefonoYUnoInexistenteODadoDeBajaEs404() {
        UUID baja = cuenta("clientes", "Baja", "baja@mail.com", 1, true);

        assertThatThrownBy(() -> servicio.actualizar(yo, new ActualizarUsuarioAdminRequest("Yo", "yo@mail.com", "342")))
            .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getCampo()).isEqualTo("telefono"));
        assertThat(servicio.actualizar(yo, new ActualizarUsuarioAdminRequest("Yo Mismo", "yo@mail.com", null)).nombreApellido())
            .isEqualTo("Yo Mismo");
        for (UUID uuid : List.of(baja, UUID.randomUUID())) {
            assertThatThrownBy(() -> servicio.actualizar(uuid, new ActualizarUsuarioAdminRequest("X", "x@mail.com", null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        }
    }

    // ─── E4 ─────────────────────────────────────────────────────────────────

    @Test
    void suspenderRevocaLosTokensYAvisaPorCorreoConElMotivo() {
        UUID ana = cuenta("clientes", "Ana", "ana@mail.com", 1, false);
        tokens.emitir(ana, Rol.CLIENTE);
        tokens.emitir(ana, Rol.CLIENTE);

        UsuarioAdminResponse r = servicio.cambiarEstado(ana,
            new CambioDeEstadoDeCuentaRequest(EstadoCuenta.SUSPENDIDA, " Reclamos reiterados "), yo);

        assertThat(r.estadoCuenta()).isEqualTo(EstadoCuenta.SUSPENDIDA);
        assertThat(estadoDe("clientes", ana)).isEqualTo("SUSPENDIDA");
        assertThat(refreshVigentes(ana)).isZero();
        verify(correos, timeout(5000)).enviar("ana@mail.com", "Tu cuenta de ServiFe fue suspendida",
            "Hola, Ana. Suspendimos tu cuenta de ServiFe. Motivo: Reclamos reiterados. Mientras siga suspendida no vas a poder ingresar.");
    }

    @Test
    void reactivarAvisaYNoExigeMotivo() {
        UUID pedro = cuenta("prestadores", "Pedro", "pedro@mail.com", 1, false);
        jdbc.update("UPDATE prestadores SET estado_cuenta = 'SUSPENDIDA' WHERE uuid = ?", pedro);

        servicio.cambiarEstado(pedro, new CambioDeEstadoDeCuentaRequest(EstadoCuenta.ACTIVA, null), yo);

        assertThat(estadoDe("prestadores", pedro)).isEqualTo("ACTIVA");
        verify(correos, timeout(5000)).enviar("pedro@mail.com", "Tu cuenta de ServiFe está activa de nuevo",
            "Hola, Pedro. Tu cuenta de ServiFe está activa de nuevo y ya podés ingresar.");
    }

    @Test
    void suspenderSinMotivoEs400YSinCambiosEs409SinCorreo() {
        UUID ana = cuenta("clientes", "Ana", "ana@mail.com", 1, false);

        assertThatThrownBy(() -> servicio.cambiarEstado(ana, new CambioDeEstadoDeCuentaRequest(EstadoCuenta.SUSPENDIDA, "  "), yo))
            .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getCampo()).isEqualTo("motivo"));
        assertThatThrownBy(() -> servicio.cambiarEstado(ana, new CambioDeEstadoDeCuentaRequest(EstadoCuenta.ACTIVA, null), yo))
            .isInstanceOfSatisfying(ConflictoException.class, e -> assertThat(e.getCodigo()).isEqualTo("ESTADO_SIN_CAMBIOS"));
        verify(correos, after(500).never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void unGestorNoSeSuspendeNiSeDaDeBajaASiMismo() {
        assertThatThrownBy(() -> servicio.cambiarEstado(yo, new CambioDeEstadoDeCuentaRequest(EstadoCuenta.SUSPENDIDA, "x"), yo))
            .isInstanceOfSatisfying(ConflictoException.class, e -> assertThat(e.getCodigo()).isEqualTo("NO_SOBRE_LA_PROPIA_CUENTA"));
        assertThatThrownBy(() -> servicio.darDeBaja(yo, yo))
            .isInstanceOfSatisfying(ConflictoException.class, e -> assertThat(e.getCodigo()).isEqualTo("NO_SOBRE_LA_PROPIA_CUENTA"));
        assertThat(estadoDe("gestores", yo)).isEqualTo("ACTIVA");
    }

    @Test
    void unGestorSiPuedeSuspenderAOtroGestor() {
        UUID otro = cuenta("gestores", "Otro", "otro@mail.com", 1, false);
        servicio.cambiarEstado(otro, new CambioDeEstadoDeCuentaRequest(EstadoCuenta.SUSPENDIDA, "Dejó el equipo"), yo);
        assertThat(estadoDe("gestores", otro)).isEqualTo("SUSPENDIDA");
    }

    // ─── E15 ────────────────────────────────────────────────────────────────

    @Test
    void darDeBajaEsLogicaRevocaTokensYReservaElEmail() {
        UUID ana = cuenta("clientes", "Ana", "ana@mail.com", 1, false);
        tokens.emitir(ana, Rol.CLIENTE);

        servicio.darDeBaja(ana, yo);

        assertThat(jdbc.queryForObject("SELECT eliminado_en IS NOT NULL FROM clientes WHERE uuid = ?", Boolean.class, ana)).isTrue();
        assertThat(refreshVigentes(ana)).isZero();
        assertThat(nombres(listar(null, null, null))).doesNotContain("Ana");
        assertThatThrownBy(() -> servicio.darDeBaja(ana, yo)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.crearGestor(new CrearGestorRequest("G", "ana@mail.com", "clave1234")))
            .isInstanceOf(ConflictoException.class);
        verify(correos, never()).enviar(eq("ana@mail.com"), anyString(), anyString());
    }
}
