package ar.edu.iessf.servife.reputacion.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.iessf.servife.reputacion.domain.Archivo;

public interface ArchivoRepository extends JpaRepository<Archivo, Long> {

    Optional<Archivo> findByUuidAndEliminadoEnIsNull(UUID uuid);

    /** Si la cuenta es el cliente o el prestador de alguna solicitud no eliminada que incluye el archivo. */
    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM solicitud_imagenes si
            JOIN solicitudes_servicio s ON s.id_solicitud = si.id_solicitud
            WHERE si.id_archivo = :idArchivo AND s.eliminado_en IS NULL
              AND ((:rol = 'CLIENTE' AND s.id_cliente = :idCuenta)
                OR (:rol = 'PRESTADOR' AND s.id_prestador = :idCuenta)))
        """, nativeQuery = true)
    boolean esParticipanteDeSolicitudConArchivo(@Param("idArchivo") Long idArchivo,
        @Param("idCuenta") Long idCuenta, @Param("rol") String rol);

    /**
     * Si el archivo es una imagen de una publicación no eliminada (D3, "Trabajos realizados") de un prestador
     * visible: mismo criterio que B6 (APROBADO, cuenta ACTIVA, no dado de baja y con el rubro activo).
     */
    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM publicacion_imagenes pi
            JOIN publicaciones pu ON pu.id_publicacion = pi.id_publicacion
            JOIN prestadores p ON p.id_prestador = pu.id_prestador
            JOIN tipos_servicio t ON t.id_tipo_servicio = p.id_tipo_servicio
            WHERE pi.id_archivo = :idArchivo AND pu.eliminado_en IS NULL
              AND p.estado_validacion = 'APROBADO' AND p.estado_cuenta = 'ACTIVA' AND p.eliminado_en IS NULL
              AND t.eliminado_en IS NULL)
        """, nativeQuery = true)
    boolean estaEnPublicacionVisible(@Param("idArchivo") Long idArchivo);
}
