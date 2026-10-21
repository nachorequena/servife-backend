package ar.edu.iessf.servife.gestion.repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.gestion.dto.ContraparteResponse;
import ar.edu.iessf.servife.gestion.dto.ConversacionResponse;
import ar.edu.iessf.servife.gestion.dto.UltimoMensajeResponse;
import ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud;

/**
 * Consulta nativa de E12: los chats del usuario con su contraparte, el último mensaje y cuántos mensajes de la
 * otra parte siguen sin leer. Más recientes primero (último mensaje o, si no hay, creación del chat). El SQL
 * solo cambia por el rol (fragmentos fijos); el id del usuario viaja como parámetro.
 */
@Repository
public class BandejaDeMensajesRepository {

    private static final String JOINS = """
         FROM chats c
         JOIN solicitudes_servicio s ON s.id_solicitud = c.id_solicitud
         JOIN tipos_servicio t ON t.id_tipo_servicio = s.id_tipo_servicio
         JOIN clientes cl ON cl.id_cliente = s.id_cliente
         JOIN prestadores p ON p.id_prestador = s.id_prestador
        """;

    private static final String WHERE = """
         WHERE c.eliminado_en IS NULL AND s.eliminado_en IS NULL AND %s = :idUsuario
        """;

    private static final String SELECT = """
        SELECT c.uuid AS uuid_chat, s.uuid AS uuid_solicitud, s.estado, t.nombre AS tipo_servicio,
               %s.uuid AS uuid_contraparte, %s.nombre_apellido AS nombre_contraparte,
               um.texto AS ultimo_texto, um.creado_en AS ultimo_creado_en, um.rol_emisor AS ultimo_rol,
               (SELECT count(*) FROM mensajes m
                 WHERE m.id_chat = c.id_chat AND m.eliminado_en IS NULL AND m.leido = false
                   AND m.rol_emisor <> :rol) AS no_leidos
        """;

    private static final String ULTIMO_MENSAJE = """
         LEFT JOIN LATERAL (
             SELECT m.texto, m.creado_en, m.rol_emisor FROM mensajes m
             WHERE m.id_chat = c.id_chat AND m.eliminado_en IS NULL
             ORDER BY m.id_mensaje DESC LIMIT 1) um ON true
        """;

    private final NamedParameterJdbcTemplate jdbc;

    public BandejaDeMensajesRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Pagina<ConversacionResponse> listar(Long idUsuario, Rol rol, int page, int size) {
        boolean esCliente = rol == Rol.CLIENTE;
        String where = WHERE.formatted(esCliente ? "s.id_cliente" : "s.id_prestador");
        String contraparte = esCliente ? "p" : "cl";
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("idUsuario", idUsuario)
            .addValue("rol", rol.name());

        Long total = jdbc.queryForObject("SELECT count(*)" + JOINS + where, params, Long.class);
        long totalElementos = total == null ? 0 : total;

        params.addValue("limite", size).addValue("desplazamiento", (long) page * size);
        List<ConversacionResponse> contenido = jdbc.query(
            SELECT.formatted(contraparte, contraparte) + JOINS + ULTIMO_MENSAJE + where
                + " ORDER BY COALESCE(um.creado_en, c.creado_en) DESC, c.id_chat DESC LIMIT :limite OFFSET :desplazamiento",
            params, (rs, i) -> {
                Timestamp ultimoCreadoEn = rs.getTimestamp("ultimo_creado_en");
                UltimoMensajeResponse ultimo = ultimoCreadoEn == null ? null
                    : new UltimoMensajeResponse(rs.getString("ultimo_texto"), ultimoCreadoEn.toInstant(),
                        rol.name().equals(rs.getString("ultimo_rol")));
                return new ConversacionResponse(
                    rs.getObject("uuid_chat", UUID.class),
                    rs.getObject("uuid_solicitud", UUID.class),
                    EstadoSolicitud.valueOf(rs.getString("estado")),
                    rs.getString("tipo_servicio"),
                    new ContraparteResponse(rs.getObject("uuid_contraparte", UUID.class),
                        rs.getString("nombre_contraparte")),
                    ultimo,
                    rs.getLong("no_leidos"));
            });

        int totalPaginas = (int) ((totalElementos + size - 1) / size);
        return new Pagina<>(contenido, page, size, totalElementos, totalPaginas);
    }
}
