package ar.edu.iessf.servife.reputacion.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.reputacion.domain.Publicacion;
import ar.edu.iessf.servife.solicitudes.domain.Solicitud;

public interface PublicacionRepository extends JpaRepository<Publicacion, Long> {

    /** Una imagen de una publicación, para armar varias tarjetas con una sola consulta. */
    interface ImagenDePublicacion {
        Long getIdPublicacion();

        UUID getUuid();
    }

    @EntityGraph(attributePaths = {"prestador", "solicitud"})
    Optional<Publicacion> findByUuidAndEliminadoEnIsNull(UUID uuid);

    /** D3 y D8: las publicaciones no dadas de baja del prestador, con la solicitud ya cargada. */
    @EntityGraph(attributePaths = "solicitud")
    Page<Publicacion> findByPrestadorAndEliminadoEnIsNull(Prestador prestador, Pageable pagina);

    /** Si la solicitud ya está publicada en otra publicación no dada de baja (para crear, id = null). */
    @Query("select count(p) > 0 from Publicacion p where p.solicitud = :solicitud and p.eliminadoEn is null "
        + "and (:id is null or p.id <> :id)")
    boolean solicitudPublicadaEnOtra(@Param("solicitud") Solicitud solicitud, @Param("id") Long idPublicacion);

    /** uuid de las imágenes de esas publicaciones, cada una en el orden en que se subieron. */
    @Query(value = "SELECT pi.id_publicacion AS idPublicacion, a.uuid AS uuid FROM publicacion_imagenes pi "
        + "JOIN archivos a ON a.id_archivo = pi.id_archivo WHERE pi.id_publicacion IN (:ids) ORDER BY a.id_archivo",
        nativeQuery = true)
    List<ImagenDePublicacion> imagenesDe(@Param("ids") Collection<Long> idsDePublicaciones);

    /** Si la imagen ya está en alguna publicación (dada de baja o no). */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM publicacion_imagenes WHERE id_archivo = :idArchivo)", nativeQuery = true)
    boolean imagenEnUso(@Param("idArchivo") Long idArchivo);

    @Modifying
    @Query(value = "INSERT INTO publicacion_imagenes (id_publicacion, id_archivo) VALUES (:idPublicacion, :idArchivo)",
        nativeQuery = true)
    void vincularImagen(@Param("idPublicacion") Long idPublicacion, @Param("idArchivo") Long idArchivo);

    /** D5: suelta todas las imágenes de la publicación antes de vincular las nuevas. */
    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM publicacion_imagenes WHERE id_publicacion = :idPublicacion", nativeQuery = true)
    void desvincularImagenes(@Param("idPublicacion") Long idPublicacion);
}
