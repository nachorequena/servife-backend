package ar.edu.iessf.servife.gestion.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.iessf.servife.gestion.domain.DocumentoValidacion;
import ar.edu.iessf.servife.gestion.domain.TipoDeDocumento;

public interface DocumentoValidacionRepository extends JpaRepository<DocumentoValidacion, Long> {

    /** Documentos vigentes del prestador, los más viejos primero. */
    List<DocumentoValidacion> findByIdPrestadorAndEliminadoEnIsNullOrderByCreadoEnAscIdAsc(Long idPrestador);

    List<DocumentoValidacion> findByIdPrestadorAndTipoAndEliminadoEnIsNull(Long idPrestador, TipoDeDocumento tipo);

    Optional<DocumentoValidacion> findByUuidAndIdPrestadorAndEliminadoEnIsNull(UUID uuid, Long idPrestador);
}
