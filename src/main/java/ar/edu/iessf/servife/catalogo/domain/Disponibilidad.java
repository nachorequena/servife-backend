package ar.edu.iessf.servife.catalogo.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import ar.edu.iessf.servife.common.auditoria.EntidadBase;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Un día de la semana en que trabaja el prestador (tabla disponibilidad_prestador, D09): 1 = lunes ... 7 = domingo. */
@Entity
@Table(name = "disponibilidad_prestador")
public class Disponibilidad extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_disponibilidad")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_prestador", nullable = false)
    private Prestador prestador;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "dia_semana", nullable = false)
    private Integer diaSemana;

    protected Disponibilidad() {
    }

    public Disponibilidad(Prestador prestador, int diaSemana) {
        this.prestador = prestador;
        this.diaSemana = diaSemana;
    }

    public int getDiaSemana() {
        return diaSemana;
    }
}
