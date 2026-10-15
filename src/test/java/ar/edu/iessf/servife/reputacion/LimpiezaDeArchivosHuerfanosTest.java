package ar.edu.iessf.servife.reputacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.repository.TipoServicioRepository;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;
import ar.edu.iessf.servife.reputacion.service.LimpiezaDeArchivosHuerfanos;
import ar.edu.iessf.servife.solicitudes.domain.Solicitud;
import ar.edu.iessf.servife.solicitudes.repository.SolicitudRepository;

/** Borra las imágenes de más de 24 h que no están en una solicitud ni en una publicación, fila y bytes. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
class LimpiezaDeArchivosHuerfanosTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @TempDir
    static Path directorio;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("servife.archivos.directorio", () -> directorio.toString());
    }

    @Autowired private LimpiezaDeArchivosHuerfanos limpieza;
    @Autowired private ClienteRepository clientes;
    @Autowired private PrestadorRepository prestadores;
    @Autowired private TipoServicioRepository tiposServicio;
    @Autowired private SolicitudRepository solicitudes;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void borraSoloLasHuerfanasViejas() throws Exception {
        Cliente ana = clientes.save(new Cliente("Ana", UUID.randomUUID() + "@mail.com", "hash"));
        TipoServicio tipo = tiposServicio.findByEliminadoEnIsNullOrderByNombre().get(0);
        Prestador beto = prestadores.save(new Prestador("Beto", UUID.randomUUID() + "@mail.com", "hash", tipo));

        UUID huerfanaVieja = insertarArchivo(ana.getId(), "CLIENTE", 25);
        UUID huerfanaNueva = insertarArchivo(ana.getId(), "CLIENTE", 1);
        UUID enSolicitud = insertarArchivo(ana.getId(), "CLIENTE", 48);
        UUID enPublicacion = insertarArchivo(beto.getId(), "PRESTADOR", 48);

        Solicitud s = solicitudes.save(new Solicitud(ana, beto, tipo, "Pérdida", LocalDate.of(2026, 10, 20), null, null));
        jdbc.update("INSERT INTO solicitud_imagenes (id_solicitud, id_archivo) SELECT ?, id_archivo FROM archivos WHERE uuid = ?",
            s.getId(), enSolicitud);
        Long publicacion = jdbc.queryForObject(
            "INSERT INTO publicaciones (id_prestador, titulo) VALUES (?, 'Baño nuevo') RETURNING id_publicacion",
            Long.class, beto.getId());
        jdbc.update("INSERT INTO publicacion_imagenes (id_publicacion, id_archivo) SELECT ?, id_archivo FROM archivos WHERE uuid = ?",
            publicacion, enPublicacion);

        assertThat(limpieza.borrarHuerfanos()).isEqualTo(1);

        assertThat(existeFila(huerfanaVieja)).isFalse();
        assertThat(directorio.resolve(huerfanaVieja.toString())).doesNotExist();
        for (UUID queda : new UUID[] {huerfanaNueva, enSolicitud, enPublicacion}) {
            assertThat(existeFila(queda)).isTrue();
            assertThat(directorio.resolve(queda.toString())).exists();
        }
        assertThat(limpieza.borrarHuerfanos()).isZero();
    }

    @Test
    void borraDeALotesHastaTerminar() throws Exception {
        Cliente ana = clientes.save(new Cliente("Ana", UUID.randomUUID() + "@mail.com", "hash"));
        int cantidad = 205; // más que un lote (200)
        for (int i = 0; i < cantidad; i++) {
            insertarArchivo(ana.getId(), "CLIENTE", 30);
        }

        assertThat(limpieza.borrarHuerfanos()).isEqualTo(cantidad);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM archivos WHERE id_propietario = ? AND rol_propietario = 'CLIENTE'",
            Long.class, ana.getId())).isZero();
    }

    private boolean existeFila(UUID uuid) {
        return jdbc.queryForObject("SELECT count(*) FROM archivos WHERE uuid = ?", Long.class, uuid) == 1;
    }

    /** Fila con la antigüedad pedida y sus bytes en disco. */
    private UUID insertarArchivo(Long idPropietario, String rol, int horasDeAntiguedad) throws Exception {
        UUID uuid = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO archivos (uuid, ruta, mime, bytes, id_propietario, rol_propietario, creado_en)
            VALUES (?, ?, 'image/png', ?, ?, ?, now() - make_interval(hours => ?))
            """, uuid, uuid.toString(), PNG.length, idPropietario, rol, horasDeAntiguedad);
        Files.write(directorio.resolve(uuid.toString()), PNG);
        return uuid;
    }
}
