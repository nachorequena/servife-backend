package ar.edu.iessf.servife.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ar.edu.iessf.servife.catalogo.dto.ActualizarPerfilDePrestadorRequest;
import ar.edu.iessf.servife.catalogo.service.ServicioDePerfilDePrestador;
import ar.edu.iessf.servife.gestion.dto.Decision;
import ar.edu.iessf.servife.gestion.service.ServicioDeValidacion;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;

/** B7 contra E6: guardar el perfil no debe pisar una decisión del gestor tomada entre la lectura y el guardado. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
class PerfilYValidacionConcurrentesTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private ServicioDePerfilDePrestador perfil;
    @Autowired private ServicioDeValidacion validacion;
    @Autowired private PrestadorRepository prestadores;
    @Autowired private PlatformTransactionManager transacciones;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void guardarElPerfilConElMismoRubroNoPisaUnaAprobacionConcurrente() {
        UUID uuid = UUID.randomUUID();
        UUID tipo = jdbc.queryForObject("SELECT uuid FROM tipos_servicio WHERE nombre = 'Gas'", UUID.class);
        jdbc.update("""
            INSERT INTO prestadores (uuid, nombre_apellido, email, contrasenia, id_tipo_servicio, estado_validacion, estado_cuenta)
            VALUES (?, 'Pedro Gas', 'concurrente@mail.com', 'hash', (SELECT id_tipo_servicio FROM tipos_servicio WHERE nombre = 'Gas'),
                'PENDIENTE', 'ACTIVA')
            """, uuid);
        TransactionTemplate b7 = new TransactionTemplate(transacciones);
        TransactionTemplate e6 = new TransactionTemplate(transacciones);
        e6.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        b7.executeWithoutResult(estado -> {
            // B7 ya leyó al prestador como PENDIENTE...
            prestadores.findByUuidAndEliminadoEnIsNull(uuid).orElseThrow();
            // ...el gestor lo aprueba en otra transacción...
            e6.executeWithoutResult(otro -> validacion.decidir(uuid, Decision.APROBAR, null));
            // ...y B7 guarda con el mismo rubro.
            perfil.actualizarPerfil(uuid, new ActualizarPerfilDePrestadorRequest(tipo, "Centro", null, null, 10, "Gasista"));
        });

        assertThat(jdbc.queryForObject("SELECT estado_validacion FROM prestadores WHERE uuid = ?", String.class, uuid))
            .isEqualTo("APROBADO");
        assertThat(jdbc.queryForObject("SELECT zona FROM prestadores WHERE uuid = ?", String.class, uuid))
            .isEqualTo("Centro");
        jdbc.update("DELETE FROM notificaciones");
        jdbc.update("DELETE FROM prestadores WHERE uuid = ?", uuid);
    }
}
