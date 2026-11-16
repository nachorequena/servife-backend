package ar.edu.iessf.servife.identidad.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.DireccionCliente;

public interface DireccionClienteRepository extends JpaRepository<DireccionCliente, Long> {

    /** Vigentes: la principal primero, después de la más nueva a la más vieja. */
    List<DireccionCliente> findByClienteAndEliminadoEnIsNullOrderByPrincipalDescCreadoEnDesc(Cliente cliente);

    Optional<DireccionCliente> findByClienteAndPrincipalTrueAndEliminadoEnIsNull(Cliente cliente);

    /** Filtra por dueño: la de otro cliente no aparece (404). */
    Optional<DireccionCliente> findByUuidAndClienteAndEliminadoEnIsNull(UUID uuid, Cliente cliente);
}
