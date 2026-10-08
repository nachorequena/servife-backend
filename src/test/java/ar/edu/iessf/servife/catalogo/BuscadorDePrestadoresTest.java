package ar.edu.iessf.servife.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ar.edu.iessf.servife.config.JwtConfig;

/** B5 por HTTP contra PostgreSQL real. Cada test parte de una tabla de prestadores vacía y siembra lo suyo. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class BuscadorDePrestadoresTest {

    private static final String API = "/api/v1";
    /** Centro de Santa Fe: el cliente de los tests. */
    private static final String LAT = "-31.6333";
    private static final String LNG = "-60.7";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    private int contador;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM disponibilidad_prestador");
        jdbc.update("DELETE FROM prestadores");
    }

    // ─── Siembra ────────────────────────────────────────────────────────────

    /** Prestador aprobado y activo, sin ubicación ni valoración. */
    private Siembra prestador(String nombre, String rubro) {
        return new Siembra(nombre, rubro);
    }

    private final class Siembra {
        private final String nombre;
        private final String rubro;
        private String estadoValidacion = "APROBADO";
        private String estadoCuenta = "ACTIVA";
        private boolean eliminado;
        private Double lat;
        private Double lng;
        private Integer radio;
        private String valoracion;
        private String zona = "Centro";

        Siembra(String nombre, String rubro) {
            this.nombre = nombre;
            this.rubro = rubro;
        }

        Siembra validacion(String v) { this.estadoValidacion = v; return this; }
        Siembra cuenta(String c) { this.estadoCuenta = c; return this; }
        Siembra baja() { this.eliminado = true; return this; }
        Siembra punto(double la, double ln, int radioKm) { this.lat = la; this.lng = ln; this.radio = radioKm; return this; }
        Siembra valoracion(String v) { this.valoracion = v; return this; }

        UUID guardar(int... dias) {
            UUID uuid = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO prestadores (uuid, nombre_apellido, email, contrasenia, id_tipo_servicio, estado_validacion,
                    estado_cuenta, zona, lat, lng, radio_km, eliminado_en)
                VALUES (?, ?, ?, 'hash', (SELECT id_tipo_servicio FROM tipos_servicio WHERE nombre = ?), ?, ?, ?, ?, ?, ?,
                    CASE WHEN ? THEN now() END)
                """, uuid, nombre, "p" + (++contador) + "@mail.com", rubro, estadoValidacion, estadoCuenta, zona,
                lat, lng, radio, eliminado);
            if (valoracion != null) {
                jdbc.update("UPDATE prestadores SET valoracion_promedio = CAST(? AS numeric) WHERE uuid = ?", valoracion, uuid);
            }
            for (int d : dias) {
                jdbc.update("INSERT INTO disponibilidad_prestador (id_prestador, dia_semana) "
                    + "SELECT id_prestador, ? FROM prestadores WHERE uuid = ?", d, uuid);
            }
            return uuid;
        }
    }

    private UUID tipoId(String nombre) {
        return jdbc.queryForObject("SELECT uuid FROM tipos_servicio WHERE nombre = ?", UUID.class, nombre);
    }

    // ─── Llamadas ───────────────────────────────────────────────────────────

    private MockHttpServletRequestBuilder pedido(String consulta) {
        return get(API + "/prestadores" + (consulta.isEmpty() ? "" : "?" + consulta)).contextPath(API)
            .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim(JwtConfig.CLAIM_ROL, "CLIENTE"))
                .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE")));
    }

    private JsonNode buscar(String consulta) throws Exception {
        String cuerpo = mvc.perform(pedido(consulta)).andExpect(status().isOk()).andReturn().getResponse()
            .getContentAsString();
        return json.readTree(cuerpo);
    }

    private static List<String> nombres(JsonNode pagina) {
        List<String> r = new ArrayList<>();
        pagina.get("contenido").forEach(n -> r.add(n.get("nombreApellido").asText()));
        return r;
    }

    private List<String> nombresDe(String consulta) throws Exception {
        return nombres(buscar(consulta));
    }

    // ─── Reglas base ────────────────────────────────────────────────────────

    @Test
    void soloAparecenAprobadosActivosYNoDadosDeBaja() throws Exception {
        prestador("Ok Aprobado", "Gas").guardar();
        prestador("Pendiente", "Gas").validacion("PENDIENTE").guardar();
        prestador("Rechazado", "Gas").validacion("RECHAZADO").guardar();
        prestador("Suspendido", "Gas").cuenta("SUSPENDIDA").guardar();
        prestador("De Baja", "Gas").baja().guardar();

        JsonNode r = buscar("");

        assertThat(nombres(r)).containsExactly("Ok Aprobado");
        assertThat(r.get("totalElementos").asInt()).isEqualTo(1);
        JsonNode p = r.get("contenido").get(0);
        assertThat(p.get("verificado").asBoolean()).isTrue();
        assertThat(p.get("tipoServicio").get("nombre").asText()).isEqualTo("Gas");
        assertThat(p.get("tipoServicio").get("uuid").asText()).isEqualTo(tipoId("Gas").toString());
        assertThat(p.get("zona").asText()).isEqualTo("Centro");
        assertThat(p.get("distanciaKm").isNull()).isTrue();
        assertThat(p.has("id")).isFalse();
    }

    @Test
    void prestadorQueCambioDeRubroQuedaPendienteYNoAparece() throws Exception {
        UUID uuid = prestador("Cambió Rubro", "Gas").guardar();
        assertThat(nombresDe("")).containsExactly("Cambió Rubro");

        // Lo que hace B7 al cambiar de rubro: vuelve a PENDIENTE.
        jdbc.update("UPDATE prestadores SET estado_validacion = 'PENDIENTE', id_tipo_servicio = "
            + "(SELECT id_tipo_servicio FROM tipos_servicio WHERE nombre = 'Plomería') WHERE uuid = ?", uuid);

        assertThat(nombresDe("")).isEmpty();
    }

    // ─── Filtros ────────────────────────────────────────────────────────────

    @Test
    void filtraPorTipoDeServicio() throws Exception {
        prestador("Gasista", "Gas").guardar();
        prestador("Plomero", "Plomería").guardar();

        assertThat(nombresDe("tipoServicioId=" + tipoId("Plomería"))).containsExactly("Plomero");
    }

    @Test
    void qBuscaPorNombreOPorRubroSinDistinguirMayusculas() throws Exception {
        prestador("Pedro Gómez", "Gas").guardar();
        prestador("Ana Ruiz", "Plomería").guardar();
        prestador("Luis Pérez", "Limpieza").guardar();

        assertThat(nombresDe("q=PEDRO")).containsExactly("Pedro Gómez");
        assertThat(nombresDe("q=plomer")).containsExactly("Ana Ruiz");
        assertThat(nombresDe("q=  gas ")).containsExactly("Pedro Gómez");
        // Los acentos sí cuentan (sin extensión unaccent).
        assertThat(nombresDe("q=gomez")).isEmpty();
        assertThat(nombresDe("q=GÓMEZ")).containsExactly("Pedro Gómez");
    }

    @Test
    void qDeUnSoloCaracterSeIgnoraYLosComodinesSonLiterales() throws Exception {
        prestador("Pedro", "Gas").guardar();
        prestador("Ana", "Gas").guardar();

        assertThat(nombresDe("q=z")).containsExactly("Ana", "Pedro");
        assertThat(nombresDe("q=%%")).isEmpty();
        assertThat(nombresDe("q=_e")).isEmpty();
    }

    @Test
    void puntajeMinimoDejaFueraALosSinValoracionSoloSiHayFiltro() throws Exception {
        prestador("Cinco", "Gas").valoracion("5.0").guardar();
        prestador("Cuatro", "Gas").valoracion("4.0").guardar();
        prestador("Tres", "Gas").valoracion("3.5").guardar();
        prestador("Sin Nota", "Gas").guardar();

        assertThat(nombresDe("puntajeMin=4")).containsExactly("Cinco", "Cuatro");
        assertThat(nombresDe("")).containsExactly("Cinco", "Cuatro", "Tres", "Sin Nota");
    }

    @Test
    void diasEnPlazoRepetidoTrabajaAlMenosUno() throws Exception {
        prestador("Lunes", "Gas").guardar(1);
        prestador("Miercoles", "Gas").guardar(3, 5);
        prestador("Domingo", "Gas").guardar(7);
        prestador("Sin Dias", "Gas").guardar();

        assertThat(nombresDe("dias=3")).containsExactly("Miercoles");
        assertThat(nombresDe("dias=1&dias=3")).containsExactlyInAnyOrder("Lunes", "Miercoles");
        assertThat(nombresDe("dias=2")).isEmpty();
    }

    @Test
    void diaDadoDeBajaNoCuenta() throws Exception {
        UUID uuid = prestador("Lunes", "Gas").guardar(1);
        jdbc.update("UPDATE disponibilidad_prestador SET eliminado_en = now() WHERE id_prestador = "
            + "(SELECT id_prestador FROM prestadores WHERE uuid = ?)", uuid);

        assertThat(nombresDe("dias=1")).isEmpty();
    }

    @Test
    void filtrosCombinados() throws Exception {
        prestador("Buen Gasista Lunes", "Gas").valoracion("4.8").guardar(1);
        prestador("Mal Gasista Lunes", "Gas").valoracion("2.0").guardar(1);
        prestador("Buen Plomero Lunes", "Plomería").valoracion("4.8").guardar(1);
        prestador("Buen Gasista Martes", "Gas").valoracion("4.8").guardar(2);

        assertThat(nombresDe("tipoServicioId=" + tipoId("Gas") + "&puntajeMin=4&dias=1&q=buen"))
            .containsExactly("Buen Gasista Lunes");
    }

    // ─── Distancia ──────────────────────────────────────────────────────────

    @Test
    void conCoordenadasSoloVeLosQueCubrenAlClienteYOrdenaPorDistancia() throws Exception {
        prestador("Lejano Cubre", "Gas").punto(-31.75, -60.7, 20).guardar();        // ~13 km, radio 20
        prestador("Cercano", "Gas").punto(-31.64, -60.7, 5).guardar();               // ~0.7 km, radio 5
        prestador("Fuera Del Radio", "Gas").punto(-31.75, -60.7, 10).guardar();      // ~13 km, radio 10
        prestador("Sin Ubicacion", "Gas").guardar();
        prestador("Sin Radio", "Gas").guardar();

        JsonNode r = buscar("lat=" + LAT + "&lng=" + LNG);

        assertThat(nombres(r)).containsExactly("Cercano", "Lejano Cubre");
        double cercano = r.get("contenido").get(0).get("distanciaKm").asDouble();
        double lejano = r.get("contenido").get(1).get("distanciaKm").asDouble();
        assertThat(cercano).isEqualTo(0.7);
        assertThat(lejano).isBetween(13.0, 13.5);
        assertThat(lejano * 10).isEqualTo(Math.rint(lejano * 10)); // un decimal
    }

    @Test
    void ordenValoracionConCoordenadasMantieneElFiltroDeRadio() throws Exception {
        prestador("Cercano Regular", "Gas").punto(-31.64, -60.7, 5).valoracion("3.0").guardar();
        prestador("Lejano Estrella", "Gas").punto(-31.75, -60.7, 20).valoracion("5.0").guardar();
        prestador("Fuera", "Gas").punto(-33.0, -60.7, 5).valoracion("5.0").guardar();

        assertThat(nombresDe("lat=" + LAT + "&lng=" + LNG + "&orden=valoracion"))
            .containsExactly("Lejano Estrella", "Cercano Regular");
    }

    @Test
    void cercaniaSinCoordenadasCaeAValoracion() throws Exception {
        prestador("Bajo", "Gas").valoracion("2.0").guardar();
        prestador("Alto", "Gas").valoracion("4.9").guardar();
        prestador("Sin Nota", "Gas").guardar();

        assertThat(nombresDe("orden=cercania")).containsExactly("Alto", "Bajo", "Sin Nota");
    }

    @Test
    void ordenPorValoracionDesempataPorNombre() throws Exception {
        prestador("Zeta", "Gas").valoracion("4.0").guardar();
        prestador("Alfa", "Gas").valoracion("4.0").guardar();
        prestador("Media", "Gas").valoracion("4.5").guardar();

        assertThat(nombresDe("orden=valoracion")).containsExactly("Media", "Alfa", "Zeta");
    }

    // ─── Paginado ───────────────────────────────────────────────────────────

    @Test
    void paginaYTotales() throws Exception {
        for (int i = 1; i <= 5; i++) {
            prestador("Prestador " + i, "Gas").guardar();
        }

        JsonNode p0 = buscar("size=2&page=0");
        JsonNode p2 = buscar("size=2&page=2");
        JsonNode p9 = buscar("size=2&page=9");

        assertThat(nombres(p0)).containsExactly("Prestador 1", "Prestador 2");
        assertThat(p0.get("totalElementos").asInt()).isEqualTo(5);
        assertThat(p0.get("totalPaginas").asInt()).isEqualTo(3);
        assertThat(p0.get("pagina").asInt()).isZero();
        assertThat(p0.get("tamanio").asInt()).isEqualTo(2);
        assertThat(nombres(p2)).containsExactly("Prestador 5");
        assertThat(p9.get("contenido")).isEmpty();
        assertThat(p9.get("totalElementos").asInt()).isEqualTo(5);
        assertThat(buscar("").get("tamanio").asInt()).isEqualTo(20);
    }

    // ─── Validación y seguridad ─────────────────────────────────────────────

    private void esValidacion(String consulta, String campo) throws Exception {
        mvc.perform(pedido(consulta))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACION"))
            .andExpect(jsonPath("$.errores[0].campo").value(campo));
    }

    @Test
    void parametrosInvalidosDevuelven400ConElCampo() throws Exception {
        esValidacion("orden=precio", "orden");
        esValidacion("puntajeMin=0", "puntajeMin");
        esValidacion("puntajeMin=6", "puntajeMin");
        esValidacion("dias=0", "dias");
        esValidacion("dias=1&dias=8", "dias");
        esValidacion("lat=-31.6", "lat");
        esValidacion("lng=-60.7", "lat");
        esValidacion("lat=91&lng=0", "lat");
        esValidacion("lat=0&lng=181", "lng");
        esValidacion("size=0", "size");
        esValidacion("size=51", "size");
        esValidacion("page=-1", "page");
    }

    @Test
    void parametroConTipoIncorrectoDevuelve400() throws Exception {
        mvc.perform(pedido("lat=abc&lng=1")).andExpect(status().isBadRequest());
        mvc.perform(pedido("tipoServicioId=no-es-uuid")).andExpect(status().isBadRequest());
        mvc.perform(pedido("dias=lunes")).andExpect(status().isBadRequest());
    }

    @Test
    void sinTokenDevuelve401()throws Exception {
        mvc.perform(get(API + "/prestadores").contextPath(API)).andExpect(status().isUnauthorized());
    }
}
