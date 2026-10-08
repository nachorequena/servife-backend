package ar.edu.iessf.servife.catalogo.domain;

import ar.edu.iessf.servife.common.auditoria.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Rubro de servicio (tabla tipos_servicio): Gas, Electricidad, Plomería... */
@Entity
@Table(name = "tipos_servicio")
public class TipoServicio extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_servicio")
    private Long id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "icono")
    private String icono;

    /** D01: los prestadores de este rubro deben cargar matrícula para ser validados. */
    @Column(name = "requiere_matricula", nullable = false)
    private boolean requiereMatricula;

    protected TipoServicio() {
    }

    public String getNombre() {
        return nombre;
    }

    public String getIcono() {
        return icono;
    }

    public boolean isRequiereMatricula() {
        return requiereMatricula;
    }
}
