package ar.edu.iessf.servife.identidad.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;
import ar.edu.iessf.servife.identidad.domain.Prestador;

public interface PrestadorRepository extends JpaRepository<Prestador, Long> {

    /** El email llega ya normalizado (Cuentas.normalizar). */
    Optional<Prestador> findByEmailAndEliminadoEnIsNull(String email);

    /** Incluye las cuentas dadas de baja: el email no se libera. */
    boolean existsByEmail(String email);

    Optional<Prestador> findByUuidAndEliminadoEnIsNull(UUID uuid);

    /** Si hay prestadores no dados de baja (de cualquier estado) con ese tipo de servicio. */
    boolean existsByTipoServicioAndEliminadoEnIsNull(TipoServicio tipoServicio);

    /** Prestadores no dados de baja en ese estado de validación (E5), con el rubro ya cargado. */
    @EntityGraph(attributePaths = "tipoServicio")
    Page<Prestador> findByEstadoValidacionAndEliminadoEnIsNull(EstadoValidacion estado, Pageable pagina);

    /**
     * Cambia el estado de validación solo si el prestador sigue PENDIENTE y no está dado de baja. Una
     * sola sentencia atómica: si dos gestores deciden a la vez, solo uno recibe 1.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Prestador p set p.estadoValidacion = :nuevo, p.actualizadoEn = :ahora "
        + "where p.id = :id and p.estadoValidacion = ar.edu.iessf.servife.identidad.domain.EstadoValidacion.PENDIENTE "
        + "and p.eliminadoEn is null")
    int resolverSiPendiente(@Param("id") Long id, @Param("nuevo") EstadoValidacion nuevo, @Param("ahora") Instant ahora);
}
