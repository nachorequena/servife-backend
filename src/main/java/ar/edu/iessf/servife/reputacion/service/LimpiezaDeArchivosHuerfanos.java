package ar.edu.iessf.servife.reputacion.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.iessf.servife.common.archivos.AlmacenDeArchivos;

/**
 * Borra las imágenes subidas que nadie adjuntó: la app sube cada imagen apenas se elige, así que si el
 * usuario la quita, abandona el formulario o C1 falla, quedan sin uso. Una imagen es huérfana si tiene
 * más de 24 h y no está en ninguna tabla que referencie archivos. Si se agrega una tabla nueva que los
 * referencia (adjuntos del chat, por ejemplo), hay que sumarla a BORRAR_LOTE o sus imágenes se borran.
 *
 * Primero se borran las filas (un DELETE condicional por lote) y recién después del commit los bytes: si
 * el commit falla no se pierde nada, y una imagen que se adjunta justo en ese momento hace fallar el lote
 * por la FK (se reintenta en la próxima pasada) en vez de quedar adjunta y sin bytes.
 */
@Component
public class LimpiezaDeArchivosHuerfanos {

    private static final Logger log = LoggerFactory.getLogger(LimpiezaDeArchivosHuerfanos.class);

    static final Duration ANTIGUEDAD_MINIMA = Duration.ofHours(24);
    static final int LOTE = 200;

    private static final String BORRAR_LOTE = """
        DELETE FROM archivos
        WHERE id_archivo IN (
            SELECT a.id_archivo FROM archivos a
            WHERE a.creado_en < :limite
              AND NOT EXISTS (SELECT 1 FROM solicitud_imagenes si WHERE si.id_archivo = a.id_archivo)
              AND NOT EXISTS (SELECT 1 FROM publicacion_imagenes pi WHERE pi.id_archivo = a.id_archivo)
            ORDER BY a.id_archivo
            LIMIT :lote)
        RETURNING ruta
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final AlmacenDeArchivos almacen;
    private final Clock reloj;

    public LimpiezaDeArchivosHuerfanos(NamedParameterJdbcTemplate jdbc, TransactionTemplate transaccion,
            AlmacenDeArchivos almacen, Clock reloj) {
        this.jdbc = jdbc;
        this.transaccion = transaccion;
        this.almacen = almacen;
        this.reloj = reloj;
    }

    /** Una pasada por hora; la primera, unos minutos después de arrancar. */
    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT1H")
    public void limpiarPeriodicamente() {
        try {
            int borrados = borrarHuerfanos();
            if (borrados > 0) {
                log.info("Se borraron {} imágenes huérfanas", borrados);
            }
        } catch (DataAccessException e) {
            log.warn("No se pudieron borrar las imágenes huérfanas; se reintenta en la próxima pasada", e);
        }
    }

    /** Borra todas las huérfanas, de a lotes. Devuelve cuántas borró. */
    public int borrarHuerfanos() {
        Timestamp limite = Timestamp.from(reloj.instant().minus(ANTIGUEDAD_MINIMA));
        int total = 0;
        List<String> rutas;
        do {
            rutas = transaccion.execute(estado ->
                jdbc.queryForList(BORRAR_LOTE, Map.of("limite", limite, "lote", LOTE), String.class));
            rutas.forEach(almacen::borrar);
            total += rutas.size();
        } while (rutas.size() == LOTE);
        return total;
    }
}
