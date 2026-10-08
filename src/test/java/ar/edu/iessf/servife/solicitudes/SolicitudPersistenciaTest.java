package ar.edu.iessf.servife.solicitudes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.repository.TipoServicioRepository;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;
import ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud;
import ar.edu.iessf.servife.solicitudes.domain.Solicitud;
import ar.edu.iessf.servife.solicitudes.repository.SolicitudRepository;
import jakarta.persistence.EntityManager;

/** V5 aplicada y Solicitud mapeada (Hibernate validate) contra PostgreSQL real. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class SolicitudPersistenciaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private SolicitudRepository solicitudes;
    @Autowired private ClienteRepository clientes;
    @Autowired private PrestadorRepository prestadores;
    @Autowired private TipoServicioRepository tiposServicio;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    @Test
    void v5AgregaLasColumnasNuevas() {
        Integer columnas = jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.columns WHERE table_name = 'solicitudes_servicio'
            AND column_name IN ('motivo', 'cancelada_por', 'precio_acordado')""", Integer.class);
        assertThat(columnas).isEqualTo(3);
    }

    @Test
    void guardaYCargaUnaSolicitudConMotivoCanceladaPorYPrecio() {
        Cliente ana = clientes.saveAndFlush(new Cliente("Ana Pérez", "ana@mail.com", "hash"));
        TipoServicio tipo = tiposServicio.findByEliminadoEnIsNullOrderByNombre().get(0);
        Prestador beto = prestadores.saveAndFlush(new Prestador("Beto Gas", "beto@mail.com", "hash", tipo));

        Solicitud s = new Solicitud(ana, beto, tipo, "Pérdida en la cocina", LocalDate.of(2026, 10, 20),
            "A la tarde", "Calle 1 123");
        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
        s.cambiarEstado(EstadoSolicitud.ACEPTADA);
        s.fijarPrecioAcordado(1_500_000L);
        s.cancelar(Rol.PRESTADOR, "Me surgió otro trabajo");
        solicitudes.saveAndFlush(s);
        em.clear();

        Solicitud cargada = solicitudes.findByUuidAndEliminadoEnIsNull(s.getUuid()).orElseThrow();

        assertThat(cargada.getEstado()).isEqualTo(EstadoSolicitud.CANCELADA);
        assertThat(cargada.getMotivo()).isEqualTo("Me surgió otro trabajo");
        assertThat(cargada.getCanceladaPor()).isEqualTo(Rol.PRESTADOR);
        assertThat(cargada.getPrecioAcordado()).isEqualTo(1_500_000L);
        assertThat(cargada.getFechaDeseada()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(cargada.getCliente().getUuid()).isEqualTo(ana.getUuid());
        assertThat(cargada.getPrestador().getUuid()).isEqualTo(beto.getUuid());
    }
}
