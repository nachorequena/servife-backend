package ar.edu.iessf.servife.gestion.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import ar.edu.iessf.servife.gestion.dto.ConteoPorNombre;
import ar.edu.iessf.servife.gestion.dto.ConteoPorServicio;
import ar.edu.iessf.servife.gestion.dto.MetricasResponse;

/**
 * Conteos de E9 en una sola consulta, sin cargar entidades de otras porciones. Solicitudes y prestadores
 * dados de baja no cuentan; las validaciones pendientes son las mismas que lista E5 (cualquier estado de cuenta).
 *
 * <p>Los agregados por período (E9b para el gestor, E10 para el prestador) filtran por la fecha de creación
 * de la solicitud en hora de Argentina. {@code idPrestador} null = todos los prestadores (vista del gestor).
 */
@Repository
public class MetricasRepository {

    private static final String CONTEOS = "SELECT"
        + " (SELECT count(*) FROM solicitudes_servicio s WHERE s.estado IN ('ACEPTADA', 'EN_CURSO') AND s.eliminado_en IS NULL)"
        + " AS servicios_activos,"
        + " (SELECT count(*) FROM solicitudes_servicio s WHERE s.estado = 'PENDIENTE' AND s.eliminado_en IS NULL)"
        + " AS solicitudes_pendientes,"
        + " (SELECT count(*) FROM prestadores p WHERE p.estado_validacion = 'PENDIENTE' AND p.eliminado_en IS NULL)"
        + " AS validaciones_pendientes";

    /** Fecha local de creación de la solicitud. */
    private static final String FECHA = "(s.creado_en AT TIME ZONE 'America/Argentina/Buenos_Aires')";

    /** Solicitudes vigentes del período, opcionalmente de un solo prestador. Parámetros: desde, hasta, id, id. */
    private static final String DEL_PERIODO = " s.eliminado_en IS NULL AND " + FECHA + "::date BETWEEN ? AND ?"
        + " AND (CAST(? AS BIGINT) IS NULL OR s.id_prestador = ?)";

    private static final String REALIZADA = "s.estado IN ('FINALIZADA', 'VALORADA')";

    private final JdbcTemplate jdbc;

    public MetricasRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MetricasResponse contar() {
        return jdbc.queryForObject(CONTEOS, (rs, i) -> new MetricasResponse(rs.getLong("servicios_activos"),
            rs.getLong("solicitudes_pendientes"), rs.getLong("validaciones_pendientes")));
    }

    public long totalSolicitudes(LocalDate desde, LocalDate hasta, Long idPrestador) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM solicitudes_servicio s WHERE" + DEL_PERIODO, Long.class,
            desde, hasta, idPrestador, idPrestador);
        return total == null ? 0 : total;
    }

    /** Por tipo de servicio de la solicitud (no el rubro actual del prestador), de mayor a menor. */
    public List<ConteoPorServicio> porServicio(LocalDate desde, LocalDate hasta, Long idPrestador) {
        return jdbc.query("SELECT t.nombre, count(*) AS solicitudes, count(*) FILTER (WHERE " + REALIZADA + ") AS realizados"
            + " FROM solicitudes_servicio s JOIN tipos_servicio t ON t.id_tipo_servicio = s.id_tipo_servicio"
            + " WHERE" + DEL_PERIODO + " GROUP BY t.nombre ORDER BY solicitudes DESC, t.nombre",
            (rs, i) -> new ConteoPorServicio(rs.getString("nombre"), rs.getLong("solicitudes"), rs.getLong("realizados")),
            desde, hasta, idPrestador, idPrestador);
    }

    /** Solo los estados que tienen solicitudes; el orden lo pone el servicio. */
    public List<ConteoPorNombre> porEstado(LocalDate desde, LocalDate hasta, Long idPrestador) {
        return jdbc.query("SELECT s.estado AS nombre, count(*) AS cantidad FROM solicitudes_servicio s"
            + " WHERE" + DEL_PERIODO + " GROUP BY s.estado",
            (rs, i) -> new ConteoPorNombre(rs.getString("nombre"), rs.getLong("cantidad")),
            desde, hasta, idPrestador, idPrestador);
    }

    /** Clave yyyy-MM; los meses sin solicitudes no vienen (los completa el servicio). */
    public List<ConteoPorNombre> porMes(LocalDate desde, LocalDate hasta, Long idPrestador) {
        return jdbc.query("SELECT to_char(" + FECHA + ", 'YYYY-MM') AS nombre, count(*) AS cantidad"
            + " FROM solicitudes_servicio s WHERE" + DEL_PERIODO + " GROUP BY 1",
            (rs, i) -> new ConteoPorNombre(rs.getString("nombre"), rs.getLong("cantidad")),
            desde, hasta, idPrestador, idPrestador);
    }

    /** Los prestadores vigentes con más trabajos realizados en el período. */
    public List<ConteoPorNombre> topPrestadores(LocalDate desde, LocalDate hasta, int limite) {
        return jdbc.query("SELECT p.nombre_apellido AS nombre, count(*) AS cantidad"
            + " FROM solicitudes_servicio s JOIN prestadores p ON p.id_prestador = s.id_prestador"
            + " WHERE" + DEL_PERIODO + " AND " + REALIZADA + " AND p.eliminado_en IS NULL"
            + " GROUP BY p.id_prestador, p.nombre_apellido ORDER BY cantidad DESC, p.nombre_apellido LIMIT ?",
            (rs, i) -> new ConteoPorNombre(rs.getString("nombre"), rs.getLong("cantidad")),
            desde, hasta, null, null, limite);
    }
}
