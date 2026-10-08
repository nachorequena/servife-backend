package ar.edu.iessf.servife.catalogo.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;

public interface TipoServicioRepository extends JpaRepository<TipoServicio, Long> {

    List<TipoServicio> findByEliminadoEnIsNullOrderByNombre();

    Optional<TipoServicio> findByUuidAndEliminadoEnIsNull(UUID uuid);

    /** Incluye los dados de baja: el nombre no se libera (UNIQUE global en la base). */
    Optional<TipoServicio> findByNombreIgnoreCase(String nombre);
}
