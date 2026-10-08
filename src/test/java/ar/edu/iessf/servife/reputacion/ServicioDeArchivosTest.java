package ar.edu.iessf.servife.reputacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.repository.TipoServicioRepository;
import ar.edu.iessf.servife.common.archivos.AlmacenDeArchivos;
import ar.edu.iessf.servife.common.error.NegocioException;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;
import ar.edu.iessf.servife.reputacion.dto.ArchivoResponse;
import ar.edu.iessf.servife.reputacion.service.ServicioDeArchivos;
import ar.edu.iessf.servife.solicitudes.domain.Solicitud;
import ar.edu.iessf.servife.solicitudes.repository.SolicitudRepository;

/** Subida (firma, tamaño, nombre en disco) y lectura protegida (dueño o participante) contra PostgreSQL real. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
class ServicioDeArchivosTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 9, 9, 9, 9, 'W', 'E', 'B', 'P', 1};

    @TempDir
    static Path directorio;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("servife.archivos.directorio", () -> directorio.toString());
    }

    @Autowired private ServicioDeArchivos servicio;
    @Autowired private AlmacenDeArchivos almacen;
    @Autowired private ClienteRepository clientes;
    @Autowired private PrestadorRepository prestadores;
    @Autowired private TipoServicioRepository tiposServicio;
    @Autowired private SolicitudRepository solicitudes;
    @Autowired private JdbcTemplate jdbc;

    private Cliente cliente() {
        return clientes.save(new Cliente("Ana", UUID.randomUUID() + "@mail.com", "hash"));
    }

    private Prestador prestador() {
        TipoServicio tipo = tiposServicio.findByEliminadoEnIsNullOrderByNombre().get(0);
        return prestadores.save(new Prestador("Beto", UUID.randomUUID() + "@mail.com", "hash", tipo));
    }

    @Test
    void guardaPorFirmaElMimeDetectadoYEscribeEnDiscoConElUuidComoNombre() throws Exception {
        Cliente ana = cliente();

        ArchivoResponse png = servicio.subir(ana.getUuid(), Rol.CLIENTE, PNG);
        ArchivoResponse jpeg = servicio.subir(ana.getUuid(), Rol.CLIENTE, JPEG);
        ArchivoResponse webp = servicio.subir(ana.getUuid(), Rol.CLIENTE, WEBP);

        assertThat(png.mime()).isEqualTo("image/png");
        assertThat(jpeg.mime()).isEqualTo("image/jpeg");
        assertThat(webp.mime()).isEqualTo("image/webp");
        assertThat(png.bytes()).isEqualTo(PNG.length);
        assertThat(Files.readAllBytes(directorio.resolve(png.uuid().toString()))).isEqualTo(PNG);
        String ruta = jdbc.queryForObject("SELECT ruta FROM archivos WHERE uuid = ?", String.class, png.uuid());
        assertThat(ruta).isEqualTo(png.uuid().toString());
    }

    @Test
    void rechazaVacioYFirmasNoPermitidas() {
        UUID quien = cliente().getUuid();
        byte[] texto = "esto es texto, no una imagen.jpg".getBytes(StandardCharsets.UTF_8);
        byte[] riffSinWebp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};

        assertThatThrownBy(() -> servicio.subir(quien, Rol.CLIENTE, new byte[0]))
            .isInstanceOfSatisfying(ValidacionException.class, e -> {
                assertThat(e.getCampo()).isEqualTo("archivo");
                assertThat(e.getDetalle()).isEqualTo("está vacío");
            });
        for (byte[] malo : new byte[][] {texto, riffSinWebp}) {
            assertThatThrownBy(() -> servicio.subir(quien, Rol.CLIENTE, malo))
                .isInstanceOfSatisfying(ValidacionException.class, e -> {
                    assertThat(e.getCampo()).isEqualTo("archivo");
                    assertThat(e.getDetalle()).isEqualTo("tiene que ser una imagen JPEG, PNG o WEBP");
                });
        }
    }

    @Test
    void masDe5MibEs413() {
        UUID quien = cliente().getUuid();
        byte[] grande = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, grande, 0, PNG.length);

        assertThatThrownBy(() -> servicio.subir(quien, Rol.CLIENTE, grande))
            .isInstanceOfSatisfying(NegocioException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                assertThat(e.getCodigo()).isEqualTo("ARCHIVO_DEMASIADO_GRANDE");
            });
    }

    @Test
    void leePropietarioYParticipantesPeroNoAjenos() {
        Cliente ana = cliente();
        Prestador beto = prestador();
        Cliente intruso = cliente();
        ArchivoResponse subido = servicio.subir(ana.getUuid(), Rol.CLIENTE, PNG);

        assertThat(servicio.leer(subido.uuid(), ana.getUuid(), Rol.CLIENTE).datos()).isEqualTo(PNG);
        assertThat(servicio.leer(subido.uuid(), ana.getUuid(), Rol.CLIENTE).mime()).isEqualTo("image/png");
        assertThatThrownBy(() -> servicio.leer(subido.uuid(), intruso.getUuid(), Rol.CLIENTE))
            .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.leer(subido.uuid(), beto.getUuid(), Rol.PRESTADOR))
            .isInstanceOf(RecursoNoEncontradoException.class);

        TipoServicio tipo = tiposServicio.findByEliminadoEnIsNullOrderByNombre().get(0);
        Solicitud s = solicitudes.save(new Solicitud(ana, beto, tipo, "Pérdida", LocalDate.of(2026, 10, 20), null, null));
        jdbc.update("INSERT INTO solicitud_imagenes (id_solicitud, id_archivo) SELECT ?, id_archivo FROM archivos WHERE uuid = ?",
            s.getId(), subido.uuid());

        assertThat(servicio.leer(subido.uuid(), beto.getUuid(), Rol.PRESTADOR).datos()).isEqualTo(PNG);
        assertThatThrownBy(() -> servicio.leer(subido.uuid(), intruso.getUuid(), Rol.CLIENTE))
            .isInstanceOf(RecursoNoEncontradoException.class);

        s.darDeBaja();
        solicitudes.save(s);
        assertThatThrownBy(() -> servicio.leer(subido.uuid(), beto.getUuid(), Rol.PRESTADOR))
            .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void uuidInexistenteEs404() {
        Cliente ana = cliente();
        assertThatThrownBy(() -> servicio.leer(UUID.randomUUID(), ana.getUuid(), Rol.CLIENTE))
            .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void elAlmacenSoloAceptaNombresUuid() {
        assertThatThrownBy(() -> almacen.leer("../secreto")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> almacen.guardar("..\\x", new ByteArrayInputStream(PNG)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> almacen.guardar("foto.jpg", new ByteArrayInputStream(PNG)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
