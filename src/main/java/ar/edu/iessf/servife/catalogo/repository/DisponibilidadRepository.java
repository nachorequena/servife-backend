package ar.edu.iessf.servife.catalogo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.iessf.servife.catalogo.domain.Disponibilidad;
import ar.edu.iessf.servife.identidad.domain.Prestador;

public interface DisponibilidadRepository extends JpaRepository<Disponibilidad, Long> {

    List<Disponibilidad> findByPrestadorAndEliminadoEnIsNullOrderByDiaSemana(Prestador prestador);

    /** Borrado físico (B8 reemplaza el set completo); se ejecuta ya mismo para no chocar con el UNIQUE al reinsertar. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Disponibilidad d where d.prestador = :prestador")
    void borrarDelPrestador(@Param("prestador") Prestador prestador);
}
