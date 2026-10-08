package ar.edu.iessf.servife.identidad.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.iessf.servife.identidad.domain.Gestor;

public interface GestorRepository extends JpaRepository<Gestor, Long> {

    /** El email llega ya normalizado (Cuentas.normalizar). */
    Optional<Gestor> findByEmailAndEliminadoEnIsNull(String email);

    /** Incluye las cuentas dadas de baja: el email no se libera. */
    boolean existsByEmail(String email);

    Optional<Gestor> findByUuidAndEliminadoEnIsNull(UUID uuid);
}
