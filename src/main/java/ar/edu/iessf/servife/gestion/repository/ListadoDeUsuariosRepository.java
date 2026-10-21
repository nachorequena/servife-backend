package ar.edu.iessf.servife.gestion.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.gestion.dto.UsuarioAdminResponse;
import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;

/**
 * Consulta nativa de E3: las tres tablas de cuentas en un UNION ALL, sin las dadas de baja. Como en
 * BusquedaDePrestadoresRepository, el SQL se arma solo con fragmentos fijos y todo valor del usuario
 * viaja como parámetro con nombre. Más nuevos primero.
 */
@Repository
public class ListadoDeUsuariosRepository {

    private static final String CUENTAS = "("
        + "SELECT c.uuid, 'CLIENTE' AS rol, c.nombre_apellido, c.email, c.telefono, c.estado_cuenta,"
        + " CAST(NULL AS VARCHAR) AS estado_validacion, CAST(NULL AS VARCHAR) AS tipo_servicio, c.creado_en"
        + " FROM clientes c WHERE c.eliminado_en IS NULL"
        + " UNION ALL "
        + "SELECT p.uuid, 'PRESTADOR', p.nombre_apellido, p.email, p.telefono, p.estado_cuenta,"
        + " p.estado_validacion, t.nombre, p.creado_en"
        + " FROM prestadores p JOIN tipos_servicio t ON t.id_tipo_servicio = p.id_tipo_servicio WHERE p.eliminado_en IS NULL"
        + " UNION ALL "
        + "SELECT g.uuid, 'GESTOR', g.nombre_apellido, g.email, CAST(NULL AS VARCHAR), g.estado_cuenta,"
        + " CAST(NULL AS VARCHAR), CAST(NULL AS VARCHAR), g.creado_en"
        + " FROM gestores g WHERE g.eliminado_en IS NULL"
        + ") u";

    private final NamedParameterJdbcTemplate jdbc;

    public ListadoDeUsuariosRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Criterios ya validados; texto recortado o null. */
    public record Criterios(Rol rol, EstadoCuenta estado, String texto, int page, int size) {
    }

    public Pagina<UsuarioAdminResponse> listar(Criterios c) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String desde = " FROM " + CUENTAS + armarWhere(c, params);

        Long total = jdbc.queryForObject("SELECT count(*)" + desde, params, Long.class);
        long totalElementos = total == null ? 0 : total;
        params.addValue("limite", c.size()).addValue("desplazamiento", (long) c.page() * c.size());

        List<UsuarioAdminResponse> contenido = jdbc.query(
            "SELECT u.*" + desde + " ORDER BY u.creado_en DESC, u.uuid LIMIT :limite OFFSET :desplazamiento",
            params, (rs, i) -> aRespuesta(rs));

        int totalPaginas = (int) ((totalElementos + c.size() - 1) / c.size());
        return new Pagina<>(contenido, c.page(), c.size(), totalElementos, totalPaginas);
    }

    private static String armarWhere(Criterios c, MapSqlParameterSource params) {
        List<String> condiciones = new ArrayList<>();
        if (c.rol() != null) {
            condiciones.add("u.rol = :rol");
            params.addValue("rol", c.rol().name());
        }
        if (c.estado() != null) {
            condiciones.add("u.estado_cuenta = :estado");
            params.addValue("estado", c.estado().name());
        }
        if (c.texto() != null) {
            String patron = "%" + c.texto().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            UUID exacto = comoUuid(c.texto());
            condiciones.add(exacto == null
                ? "(u.nombre_apellido ILIKE :texto OR u.email ILIKE :texto)"
                : "(u.nombre_apellido ILIKE :texto OR u.email ILIKE :texto OR u.uuid = :uuid)");
            params.addValue("texto", patron);
            if (exacto != null) {
                params.addValue("uuid", exacto);
            }
        }
        return condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
    }

    /** CU12 permite buscar por ID: el único que viaja a la app es el uuid. */
    private static UUID comoUuid(String texto) {
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UsuarioAdminResponse aRespuesta(ResultSet rs) throws SQLException {
        String validacion = rs.getString("estado_validacion");
        Timestamp creado = rs.getTimestamp("creado_en");
        return new UsuarioAdminResponse(
            rs.getObject("uuid", UUID.class),
            Rol.valueOf(rs.getString("rol")),
            rs.getString("nombre_apellido"),
            rs.getString("email"),
            rs.getString("telefono"),
            EstadoCuenta.valueOf(rs.getString("estado_cuenta")),
            validacion == null ? null : EstadoValidacion.valueOf(validacion),
            rs.getString("tipo_servicio"),
            creado == null ? null : creado.toInstant());
    }
}
