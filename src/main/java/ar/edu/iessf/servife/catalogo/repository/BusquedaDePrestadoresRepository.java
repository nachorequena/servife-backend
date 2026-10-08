package ar.edu.iessf.servife.catalogo.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import ar.edu.iessf.servife.catalogo.dto.PrestadorEnListaResponse;
import ar.edu.iessf.servife.catalogo.dto.TipoServicioResponse;
import ar.edu.iessf.servife.common.paginacion.Pagina;

/**
 * Consulta nativa de B5. El SQL se arma solo con fragmentos fijos; todo valor del usuario viaja como
 * parámetro con nombre. La distancia es Haversine (radio terrestre 6371 km).
 */
@Repository
public class BusquedaDePrestadoresRepository {

    private static final String DISTANCIA = "6371 * 2 * asin(least(1, sqrt("
        + "power(sin(radians(:lat - p.lat) / 2), 2) + cos(radians(:lat)) * cos(radians(p.lat))"
        + " * power(sin(radians(:lng - p.lng) / 2), 2))))";

    private final NamedParameterJdbcTemplate jdbc;

    public BusquedaDePrestadoresRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Criterios ya validados y normalizados. */
    public record Criterios(String q, UUID tipoServicioId, Double lat, Double lng, Integer puntajeMin,
            List<Integer> dias, boolean ordenarPorCercania, int page, int size) {

        boolean conUbicacion() {
            return lat != null && lng != null;
        }
    }

    public Pagina<PrestadorEnListaResponse> buscar(Criterios c) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = armarWhere(c, params);
        String desde = " FROM prestadores p JOIN tipos_servicio t ON t.id_tipo_servicio = p.id_tipo_servicio" + where;

        Long total = jdbc.queryForObject("SELECT count(*)" + desde, params, Long.class);
        long totalElementos = total == null ? 0 : total;

        String distancia = c.conUbicacion() ? DISTANCIA : "CAST(NULL AS double precision)";
        String orden = c.ordenarPorCercania()
            ? " ORDER BY distancia ASC, p.nombre_apellido, p.uuid"
            : " ORDER BY p.valoracion_promedio DESC NULLS LAST, p.nombre_apellido, p.uuid";
        params.addValue("limite", c.size()).addValue("desplazamiento", (long) c.page() * c.size());

        List<PrestadorEnListaResponse> contenido = jdbc.query(
            "SELECT p.uuid, p.nombre_apellido, p.zona, p.valoracion_promedio, "
                + "t.uuid AS tipo_uuid, t.nombre AS tipo_nombre, t.icono AS tipo_icono, "
                + "t.requiere_matricula AS tipo_matricula, " + distancia + " AS distancia"
                + desde + orden + " LIMIT :limite OFFSET :desplazamiento",
            params, (rs, i) -> {
                double km = rs.getDouble("distancia");
                Double distanciaKm = rs.wasNull() ? null : Math.round(km * 10) / 10.0;
                return new PrestadorEnListaResponse(
                    rs.getObject("uuid", UUID.class),
                    rs.getString("nombre_apellido"),
                    new TipoServicioResponse(rs.getObject("tipo_uuid", UUID.class), rs.getString("tipo_nombre"),
                        rs.getString("tipo_icono"), rs.getBoolean("tipo_matricula")),
                    rs.getString("zona"),
                    rs.getObject("valoracion_promedio", BigDecimal.class),
                    distanciaKm,
                    true);
            });

        int totalPaginas = (int) ((totalElementos + c.size() - 1) / c.size());
        return new Pagina<>(contenido, c.page(), c.size(), totalElementos, totalPaginas);
    }

    private static String armarWhere(Criterios c, MapSqlParameterSource params) {
        List<String> condiciones = new ArrayList<>(List.of(
            "p.estado_validacion = 'APROBADO'", "p.estado_cuenta = 'ACTIVA'", "p.eliminado_en IS NULL"));
        if (c.q() != null) {
            condiciones.add("(p.nombre_apellido ILIKE :q OR t.nombre ILIKE :q)");
            params.addValue("q", "%" + c.q().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (c.tipoServicioId() != null) {
            condiciones.add("t.uuid = :tipoServicioId");
            params.addValue("tipoServicioId", c.tipoServicioId());
        }
        if (c.puntajeMin() != null) {
            condiciones.add("p.valoracion_promedio >= :puntajeMin");
            params.addValue("puntajeMin", c.puntajeMin());
        }
        if (c.dias() != null && !c.dias().isEmpty()) {
            condiciones.add("EXISTS (SELECT 1 FROM disponibilidad_prestador d WHERE d.id_prestador = p.id_prestador"
                + " AND d.eliminado_en IS NULL AND d.dia_semana IN (:dias))");
            params.addValue("dias", c.dias());
        }
        if (c.conUbicacion()) {
            condiciones.add("p.lat IS NOT NULL AND p.lng IS NOT NULL AND p.radio_km IS NOT NULL");
            condiciones.add(DISTANCIA + " <= p.radio_km");
            params.addValue("lat", c.lat()).addValue("lng", c.lng());
        }
        return " WHERE " + String.join(" AND ", condiciones);
    }
}
