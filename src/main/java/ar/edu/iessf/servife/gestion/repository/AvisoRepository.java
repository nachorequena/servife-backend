package ar.edu.iessf.servife.gestion.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.iessf.servife.gestion.domain.Aviso;

public interface AvisoRepository extends JpaRepository<Aviso, Long> {

    /** Los más nuevos primero; el id desempata los creados en el mismo instante. */
    Page<Aviso> findByIdUsuarioAndRolUsuarioAndEliminadoEnIsNullOrderByCreadoEnDescIdDesc(
        Long idUsuario, String rolUsuario, Pageable pagina);

    Optional<Aviso> findByUuidAndIdUsuarioAndRolUsuarioAndEliminadoEnIsNull(UUID uuid, Long idUsuario, String rolUsuario);

    long countByIdUsuarioAndRolUsuarioAndLeidaFalseAndEliminadoEnIsNull(Long idUsuario, String rolUsuario);
}
