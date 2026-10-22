package ar.edu.iessf.servife.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.error.NegocioException;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.gestion.domain.TipoDeDocumento;
import ar.edu.iessf.servife.gestion.dto.Decision;
import ar.edu.iessf.servife.gestion.dto.DocumentoResponse;
import ar.edu.iessf.servife.gestion.dto.ExpedienteResponse;
import ar.edu.iessf.servife.gestion.service.DocumentoLeido;
import ar.edu.iessf.servife.gestion.service.ServicioDeDocumentos;
import ar.edu.iessf.servife.gestion.service.ServicioDeValidacion;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;

/** E7, E7b, E8, E8b y el cierre del expediente (D01) contra PostgreSQL real y una carpeta temporal. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
class ServicioDeDocumentosTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 9, 9};
    private static final Path CARPETA = carpetaTemporal();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void carpeta(DynamicPropertyRegistry registro) {
        registro.add("servife.documentos.directorio", CARPETA::toString);
    }

    private static Path carpetaTemporal() {
        try {
            return Files.createTempDirectory("documentos");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired private ServicioDeDocumentos servicio;
    @Autowired private ServicioDeValidacion validacion;
    @Autowired private JdbcTemplate jdbc;

    private UUID gestor;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM notificaciones");
        jdbc.update("DELETE FROM documentos_validacion");
        jdbc.update("DELETE FROM prestadores");
        jdbc.update("DELETE FROM gestores WHERE email = 'gestor-docs@mail.com'");
        gestor = UUID.randomUUID();
        jdbc.update("INSERT INTO gestores (uuid, nombre_apellido, email, contrasenia) VALUES (?, 'Gestor', 'gestor-docs@mail.com', 'hash')",
            gestor);
    }

    private UUID prestador(String validacionInicial) {
        UUID uuid = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO prestadores (uuid, nombre_apellido, email, contrasenia, id_tipo_servicio, estado_validacion)
            VALUES (?, 'Pedro', ?, 'hash', (SELECT id_tipo_servicio FROM tipos_servicio WHERE nombre = 'Gas'), ?)
            """, uuid, uuid + "@mail.com", validacionInicial);
        return uuid;
    }

    private DocumentoResponse subir(UUID prestador, TipoDeDocumento tipo, byte[] datos) {
        return servicio.subir(prestador, tipo, true, datos);
    }

    private String rutaDe(UUID documento) {
        return jdbc.queryForObject("SELECT ruta FROM documentos_validacion WHERE uuid = ?", String.class, documento);
    }

    private boolean enDisco(String ruta) {
        return Files.exists(CARPETA.resolve(ruta));
    }

    @Test
    void subirGuardaElArchivoFueraDeLaBaseConUnNombrePropioYLaDeclaracionJurada() {
        UUID p = prestador("PENDIENTE");

        DocumentoResponse r = subir(p, TipoDeDocumento.DNI_FRENTE, PNG);

        assertThat(r.tipo()).isEqualTo(TipoDeDocumento.DNI_FRENTE);
        assertThat(r.estado()).isEqualTo(EstadoValidacion.PENDIENTE);
        String ruta = rutaDe(r.uuid());
        assertThat(ruta).isNotEqualTo(r.uuid().toString());
        assertThat(enDisco(ruta)).isTrue();
        ExpedienteResponse expediente = servicio.expedientePropio(p);
        assertThat(expediente.declaracionJuradaEn()).isNotNull();
        assertThat(expediente.documentos()).extracting(DocumentoResponse::uuid).containsExactly(r.uuid());
    }

    @Test
    void sinDeclaracionJuradaVacioONoImagenEs400YMasDe5MbEs413() {
        UUID p = prestador("PENDIENTE");

        assertThatThrownBy(() -> servicio.subir(p, TipoDeDocumento.DNI_FRENTE, false, PNG))
            .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getCampo()).isEqualTo("declaracionJurada"));
        assertThatThrownBy(() -> subir(p, TipoDeDocumento.DNI_FRENTE, new byte[0]))
            .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getCampo()).isEqualTo("archivo"));
        assertThatThrownBy(() -> subir(p, TipoDeDocumento.DNI_FRENTE, "%PDF-1.4".getBytes()))
            .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getCampo()).isEqualTo("archivo"));
        byte[] grande = Arrays.copyOf(PNG, 5 * 1024 * 1024 + 1);
        assertThatThrownBy(() -> subir(p, TipoDeDocumento.DNI_FRENTE, grande))
            .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.getCodigo()).isEqualTo("ARCHIVO_DEMASIADO_GRANDE"));
        assertThat(servicio.expedientePropio(p).documentos()).isEmpty();
        assertThat(servicio.expedientePropio(p).declaracionJuradaEn()).isNull();
    }

    @Test
    void soloSeCargaMientrasEstaPendiente() {
        for (String estado : new String[] {"APROBADO", "RECHAZADO"}) {
            UUID p = prestador(estado);
            assertThatThrownBy(() -> subir(p, TipoDeDocumento.CERTIFICACION, PNG))
                .isInstanceOfSatisfying(ConflictoException.class, e -> assertThat(e.getCodigo()).isEqualTo("EXPEDIENTE_CERRADO"));
        }
    }

    @Test
    void unDniNuevoReemplazaAlAnteriorYBorraSuArchivo() {
        UUID p = prestador("PENDIENTE");
        DocumentoResponse viejo = subir(p, TipoDeDocumento.DNI_FRENTE, PNG);
        String rutaVieja = rutaDe(viejo.uuid());

        DocumentoResponse nuevo = subir(p, TipoDeDocumento.DNI_FRENTE, JPG);

        assertThat(servicio.expedientePropio(p).documentos()).extracting(DocumentoResponse::uuid).containsExactly(nuevo.uuid());
        assertThat(enDisco(rutaVieja)).isFalse();
        assertThat(enDisco(rutaDe(nuevo.uuid()))).isTrue();
    }

    @Test
    void lasCertificacionesLleganHastaCinco() {
        UUID p = prestador("PENDIENTE");
        for (int i = 0; i < 5; i++) {
            subir(p, TipoDeDocumento.CERTIFICACION, PNG);
        }
        assertThatThrownBy(() -> subir(p, TipoDeDocumento.CERTIFICACION, PNG))
            .isInstanceOfSatisfying(ConflictoException.class, e -> assertThat(e.getCodigo()).isEqualTo("LIMITE_DE_CERTIFICACIONES"));
        assertThat(servicio.expedientePropio(p).documentos()).hasSize(5);
    }

    @Test
    void elGestorLeeLosBytesSoloDelDocumentoDeEsePrestador() throws IOException {
        UUID p = prestador("PENDIENTE");
        UUID otro = prestador("PENDIENTE");
        DocumentoResponse doc = subir(p, TipoDeDocumento.DNI_DORSO, JPG);

        try (InputStream datos = servicio.leer(p, doc.uuid()).datos()) {
            assertThat(datos.readAllBytes()).isEqualTo(JPG);
        }
        DocumentoLeido leido = servicio.leer(p, doc.uuid());
        leido.datos().close();
        assertThat(leido.mime()).isEqualTo("image/jpeg");
        assertThat(leido.bytes()).isEqualTo(JPG.length);
        assertThatThrownBy(() -> servicio.leer(otro, doc.uuid())).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.leer(p, UUID.randomUUID())).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.expedienteDe(UUID.randomUUID())).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void alAprobarSeBorranTodosLosArchivosYQuedaLaCertificacionComoDato() {
        UUID p = prestador("PENDIENTE");
        DocumentoResponse frente = subir(p, TipoDeDocumento.DNI_FRENTE, PNG);
        DocumentoResponse dorso = subir(p, TipoDeDocumento.DNI_DORSO, PNG);
        DocumentoResponse cert = subir(p, TipoDeDocumento.CERTIFICACION, PNG);
        String[] rutas = {rutaDe(frente.uuid()), rutaDe(dorso.uuid()), rutaDe(cert.uuid())};

        validacion.decidir(p, Decision.APROBAR, null, gestor);

        for (String ruta : rutas) {
            assertThat(enDisco(ruta)).isFalse();
        }
        ExpedienteResponse despues = servicio.expedienteDe(p);
        assertThat(despues.documentos()).extracting(DocumentoResponse::tipo, DocumentoResponse::estado)
            .containsExactly(tuple(TipoDeDocumento.CERTIFICACION, EstadoValidacion.APROBADO));
        assertThatThrownBy(() -> servicio.leer(p, cert.uuid())).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void alRechazarSeBorraTodo() {
        UUID p = prestador("PENDIENTE");
        String ruta = rutaDe(subir(p, TipoDeDocumento.CERTIFICACION, PNG).uuid());

        validacion.decidir(p, Decision.RECHAZAR, "No se lee", gestor);

        assertThat(enDisco(ruta)).isFalse();
        assertThat(servicio.expedienteDe(p).documentos()).isEmpty();
    }
}
