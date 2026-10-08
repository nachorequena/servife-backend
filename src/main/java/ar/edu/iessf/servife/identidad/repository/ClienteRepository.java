package ar.edu.iessf.servife.identidad.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.iessf.servife.identidad.domain.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    /** El email llega ya normalizado (Cuentas.normalizar). */
    Optional<Cliente> findByEmailAndEliminadoEnIsNull(String email);

    /** Incluye las cuentas dadas de baja: el email no se libera. */
    boolean existsByEmail(String email);

    Optional<Cliente> findByUuidAndEliminadoEnIsNull(UUID uuid);
}
