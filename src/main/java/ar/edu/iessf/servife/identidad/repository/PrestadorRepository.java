package ar.edu.iessf.servife.identidad.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.iessf.servife.identidad.domain.Prestador;

public interface PrestadorRepository extends JpaRepository<Prestador, Long> {

    /** El email llega ya normalizado (Cuentas.normalizar). */
    Optional<Prestador> findByEmailAndEliminadoEnIsNull(String email);

    /** Incluye las cuentas dadas de baja: el email no se libera. */
    boolean existsByEmail(String email);

    Optional<Prestador> findByUuidAndEliminadoEnIsNull(UUID uuid);
}
