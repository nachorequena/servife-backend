package ar.edu.iessf.servife.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ar.edu.iessf.servife.catalogo.dto.TipoServicioRequest;
import ar.edu.iessf.servife.catalogo.dto.TipoServicioResponse;
import ar.edu.iessf.servife.catalogo.service.TipoServicioService;
import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.config.JwtConfig;

/** B2, B3 y B4 (CU13) contra PostgreSQL real. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
class TipoServicioServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private TipoServicioService servicio;
    @Autowired private JdbcTemplate jdbc;

    private UUID gestor;

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM prestadores");
        jdbc.update("DELETE FROM tipos_servicio WHERE id_gestor IS NOT NULL");
        jdbc.update("DELETE FROM gestores WHERE email LIKE 't4-%'");
        gestor = UUID.randomUUID();
        jdbc.update("INSERT INTO gestores (uuid, nombre_apellido, email, contrasenia) VALUES (?, 'Gestor T4', ?, 'hash')",
            gestor, "t4-" + gestor + "@mail.com");
        autenticarComo(gestor);
    }

    private static void autenticarComo(UUID uuid) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(uuid.toString())
            .claim(JwtConfig.CLAIM_ROL, "GESTOR").build();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_GESTOR"))));
    }

    private static TipoServicioRequest pedido(String nombre) {
        return new TipoServicioRequest(nombre, "water", true);
    }

    private boolean listado(UUID uuid) {
        return servicio.listarActivos().stream().anyMatch(t -> t.uuid().equals(uuid));
    }

    private void sembrarPrestador(UUID tipo, boolean baja) {
        jdbc.update("""
            INSERT INTO prestadores (uuid, nombre_apellido, email, contrasenia, id_tipo_servicio, eliminado_en)
            VALUES (gen_random_uuid(), 'P', ?, 'hash', (SELECT id_tipo_servicio FROM tipos_servicio WHERE uuid = ?),
                CASE WHEN ? THEN now() END)
            """, "p" + UUID.randomUUID() + "@mail.com", tipo, baja);
    }

    @Test
    void creaConNombreRecortadoYGuardaAlGestor() {
        TipoServicioResponse r = servicio.crear(new TipoServicioRequest("  T4 Pintura  ", "brush", false));

        assertThat(r.nombre()).isEqualTo("T4 Pintura");
        assertThat(r.icono()).isEqualTo("brush");
        assertThat(r.requiereMatricula()).isFalse();
        assertThat(listado(r.uuid())).isTrue();
        Long idGestor = jdbc.queryForObject("SELECT id_gestor FROM tipos_servicio WHERE uuid = ?", Long.class, r.uuid());
        Long esperado = jdbc.queryForObject("SELECT id_gestor FROM gestores WHERE uuid = ?", Long.class, gestor);
        assertThat(idGestor).isEqualTo(esperado);
    }

    @Test
    void iconoFueraDeLaListaEs400ConCampoIcono() {
        assertThatThrownBy(() -> servicio.crear(new TipoServicioRequest("T4 X", "dragon", false)))
            .isInstanceOfSatisfying(ValidacionException.class, e -> {
                assertThat(e.getCampo()).isEqualTo("icono");
                assertThat(e.getDetalle()).isEqualTo("no es un ícono permitido");
            });
    }

    @Test
    void duplicadoActivoSinMayusculasEs409() {
        servicio.crear(pedido("T4 Cerrajería"));

        assertThatThrownBy(() -> servicio.crear(pedido("  t4 CERRAJERÍA ")))
            .isInstanceOfSatisfying(ConflictoException.class, e -> {
                assertThat(e.getCodigo()).isEqualTo("TIPO_SERVICIO_DUPLICADO");
                assertThat(e.getErrores()).extracting("campo").containsExactly("nombre");
            });
    }

    @Test
    void crearConElNombreDeUnoDadoDeBajaLoReactivaConLosDatosNuevos() {
        TipoServicioResponse original = servicio.crear(new TipoServicioRequest("T4 Vidriería", "water", false));
        servicio.eliminar(original.uuid());
        assertThat(listado(original.uuid())).isFalse();

        TipoServicioResponse nuevo = servicio.crear(new TipoServicioRequest("t4 vidriería", "home", true));

        assertThat(nuevo.uuid()).isEqualTo(original.uuid());
        assertThat(nuevo.nombre()).isEqualTo("t4 vidriería");
        assertThat(nuevo.icono()).isEqualTo("home");
        assertThat(nuevo.requiereMatricula()).isTrue();
        assertThat(listado(original.uuid())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tipos_servicio WHERE lower(nombre) = 't4 vidriería'",
            Long.class)).isEqualTo(1L);
    }

    @Test
    void actualizaNombreIconoYMatricula() {
        TipoServicioResponse t = servicio.crear(pedido("T4 Aire"));

        TipoServicioResponse r = servicio.actualizar(t.uuid(), new TipoServicioRequest("T4 Climatización", "leaf", false));

        assertThat(r.uuid()).isEqualTo(t.uuid());
        assertThat(r.nombre()).isEqualTo("T4 Climatización");
        assertThat(r.icono()).isEqualTo("leaf");
        assertThat(r.requiereMatricula()).isFalse();
    }

    @Test
    void puedeCambiarSoloLasMayusculasDeSuPropioNombre() {
        TipoServicioResponse t = servicio.crear(pedido("T4 gas"));

        assertThat(servicio.actualizar(t.uuid(), pedido("T4 Gas")).nombre()).isEqualTo("T4 Gas");
    }

    @Test
    void renombrarAUnNombreExistenteEs409() {
        servicio.crear(pedido("T4 Uno"));
        TipoServicioResponse dos = servicio.crear(pedido("T4 Dos"));

        assertThatThrownBy(() -> servicio.actualizar(dos.uuid(), pedido("t4 uno")))
            .isInstanceOfSatisfying(ConflictoException.class,
                e -> assertThat(e.getCodigo()).isEqualTo("TIPO_SERVICIO_DUPLICADO"));
    }

    @Test
    void renombrarAUnNombreDadoDeBajaEs409YNoFusionaFilas() {
        TipoServicioResponse viejo = servicio.crear(pedido("T4 Viejo"));
        servicio.eliminar(viejo.uuid());
        TipoServicioResponse otro = servicio.crear(pedido("T4 Otro"));

        assertThatThrownBy(() -> servicio.actualizar(otro.uuid(), pedido("T4 Viejo")))
            .isInstanceOfSatisfying(ConflictoException.class,
                e -> assertThat(e.getCodigo()).isEqualTo("TIPO_SERVICIO_DUPLICADO"));
        assertThat(listado(viejo.uuid())).isFalse();
    }

    @Test
    void actualizarUnoInexistenteODadoDeBajaEs404() {
        TipoServicioResponse t = servicio.crear(pedido("T4 Fugaz"));
        servicio.eliminar(t.uuid());

        assertThatThrownBy(() -> servicio.actualizar(t.uuid(), pedido("T4 Fugaz 2")))
            .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.actualizar(UUID.randomUUID(), pedido("T4 Nada")))
            .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void bajaConPrestadoresNoDadosDeBajaEs409() {
        TipoServicioResponse t = servicio.crear(pedido("T4 Ocupado"));
        sembrarPrestador(t.uuid(), false);

        assertThatThrownBy(() -> servicio.eliminar(t.uuid()))
            .isInstanceOfSatisfying(ConflictoException.class,
                e -> assertThat(e.getCodigo()).isEqualTo("TIPO_SERVICIO_EN_USO"));
        assertThat(listado(t.uuid())).isTrue();
    }

    @Test
    void bajaSinPrestadoresOSoloConPrestadoresDeBajaDesapareceDelListado() {
        TipoServicioResponse libre = servicio.crear(pedido("T4 Libre"));
        TipoServicioResponse conBaja = servicio.crear(pedido("T4 Con baja"));
        sembrarPrestador(conBaja.uuid(), true);

        servicio.eliminar(libre.uuid());
        servicio.eliminar(conBaja.uuid());

        assertThat(listado(libre.uuid())).isFalse();
        assertThat(listado(conBaja.uuid())).isFalse();
    }

    @Test
    void eliminarUnoInexistenteEs404() {
        assertThatThrownBy(() -> servicio.eliminar(UUID.randomUUID())).isInstanceOf(RecursoNoEncontradoException.class);
    }
}
