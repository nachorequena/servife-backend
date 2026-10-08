package ar.edu.iessf.servife.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.gestion.dto.AvisoResponse;
import ar.edu.iessf.servife.gestion.service.Avisos;
import ar.edu.iessf.servife.gestion.service.TipoDeAviso;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;

/** Avisos dentro de la app contra PostgreSQL real (migración V4 incluida). */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class AvisosTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private Avisos avisos;
    @Autowired private ClienteRepository clientes;
    @Autowired private JdbcTemplate jdbc;

    private Cliente cliente(String nombre, String email) {
        return clientes.saveAndFlush(new Cliente(nombre, email, "hash"));
    }

    @Test
    void avisarCreaUnaFilaNoLeidaParaElDestinatario() {
        Cliente ana = cliente("Ana", "ana@mail.com");
        UUID solicitud = UUID.randomUUID();

        avisos.avisar(ana, TipoDeAviso.SOLICITUD_NUEVA, "Nueva solicitud", "Te llegó una solicitud", solicitud);

        var fila = jdbc.queryForMap("SELECT n.* FROM notificaciones n");
        assertThat(fila.get("rol_usuario")).isEqualTo("CLIENTE");
        assertThat(fila.get("tipo")).isEqualTo("SOLICITUD_NUEVA");
        assertThat(fila.get("leida")).isEqualTo(false);
        assertThat(fila.get("uuid_solicitud")).isEqualTo(solicitud);
        assertThat(avisos.contarNoLeidos(ana.getUuid(), Rol.CLIENTE)).isEqualTo(1);
    }

    @Test
    void avisarRecortaTitulosDeMasDe120Caracteres() {
        Cliente ana = cliente("Ana", "ana@mail.com");

        avisos.avisar(ana, TipoDeAviso.PERFIL_APROBADO, "x".repeat(300), null, null);

        Pagina<AvisoResponse> pagina = avisos.listar(ana.getUuid(), Rol.CLIENTE, PageRequest.of(0, 20));
        assertThat(pagina.contenido().get(0).titulo()).hasSize(120);
    }

    @Test
    void listarDevuelveSoloLosPropiosMasNuevosPrimero() {
        Cliente ana = cliente("Ana", "ana@mail.com");
        Cliente beto = cliente("Beto", "beto@mail.com");
        avisos.avisar(ana, TipoDeAviso.SOLICITUD_NUEVA, "Primero", null, null);
        avisos.avisar(beto, TipoDeAviso.SOLICITUD_NUEVA, "De Beto", null, null);
        avisos.avisar(ana, TipoDeAviso.SOLICITUD_ACEPTADA, "Segundo", null, null);

        Pagina<AvisoResponse> pagina = avisos.listar(ana.getUuid(), Rol.CLIENTE, PageRequest.of(0, 20));

        assertThat(pagina.totalElementos()).isEqualTo(2);
        assertThat(pagina.contenido()).extracting(AvisoResponse::titulo).containsExactly("Segundo", "Primero");
        assertThat(pagina.contenido().get(0).tipo()).isEqualTo("SOLICITUD_ACEPTADA");
        assertThat(pagina.contenido().get(0).leida()).isFalse();
    }

    @Test
    void marcarLeidoActualizaLaFilaYElContador() {
        Cliente ana = cliente("Ana", "ana@mail.com");
        avisos.avisar(ana, TipoDeAviso.SOLICITUD_NUEVA, "Uno", null, null);
        avisos.avisar(ana, TipoDeAviso.SOLICITUD_NUEVA, "Dos", null, null);
        List<AvisoResponse> lista = avisos.listar(ana.getUuid(), Rol.CLIENTE, PageRequest.of(0, 20)).contenido();

        avisos.marcarLeido(ana.getUuid(), Rol.CLIENTE, lista.get(0).uuid());

        assertThat(avisos.contarNoLeidos(ana.getUuid(), Rol.CLIENTE)).isEqualTo(1);
        assertThat(avisos.listar(ana.getUuid(), Rol.CLIENTE, PageRequest.of(0, 20)).contenido().get(0).leida()).isTrue();
    }

    @Test
    void marcarLeidoUnAvisoAjenoEs404() {
        Cliente ana = cliente("Ana", "ana@mail.com");
        Cliente beto = cliente("Beto", "beto@mail.com");
        avisos.avisar(beto, TipoDeAviso.SOLICITUD_NUEVA, "De Beto", null, null);
        UUID deBeto = avisos.listar(beto.getUuid(), Rol.CLIENTE, PageRequest.of(0, 20)).contenido().get(0).uuid();

        assertThatThrownBy(() -> avisos.marcarLeido(ana.getUuid(), Rol.CLIENTE, deBeto))
            .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> avisos.marcarLeido(ana.getUuid(), Rol.CLIENTE, UUID.randomUUID()))
            .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(avisos.contarNoLeidos(beto.getUuid(), Rol.CLIENTE)).isEqualTo(1);
    }

    @Test
    void laMigracionV4PoneIconoALosSieteRubros() {
        List<String> iconos = jdbc.queryForList(
            "SELECT icono FROM tipos_servicio WHERE eliminado_en IS NULL ORDER BY nombre", String.class);
        assertThat(iconos).hasSize(7).doesNotContainNull();
        assertThat(jdbc.queryForObject("SELECT icono FROM tipos_servicio WHERE nombre = 'Gas'", String.class))
            .isEqualTo("flame");
        assertThat(jdbc.queryForObject("SELECT icono FROM tipos_servicio WHERE nombre = 'Plomería'", String.class))
            .isEqualTo("water");
    }

    @Test
    void laMigracionV4RechazaUnNombreDeRubroQueSoloDifiereEnMayusculas() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tipos_servicio (nombre) VALUES ('PLOMERÍA')"))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uq_tipos_servicio_nombre_lower");
    }
}
