package ar.edu.iessf.servife.gestion.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import ar.edu.iessf.servife.gestion.dto.MetricasResponse;

/**
 * Conteos de E9 en una sola consulta, sin cargar entidades de otras porciones. Solicitudes y prestadores
 * dados de baja no cuentan; las validaciones pendientes son las mismas que lista E5 (cualquier estado de cuenta).
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

    private final JdbcTemplate jdbc;

    public MetricasRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MetricasResponse contar() {
        return jdbc.queryForObject(CONTEOS, (rs, i) -> new MetricasResponse(rs.getLong("servicios_activos"),
            rs.getLong("solicitudes_pendientes"), rs.getLong("validaciones_pendientes")));
    }
}
