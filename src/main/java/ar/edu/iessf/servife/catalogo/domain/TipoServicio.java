package ar.edu.iessf.servife.catalogo.domain;

import ar.edu.iessf.servife.common.auditoria.EntidadBase;
import ar.edu.iessf.servife.identidad.domain.Gestor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    /** Gestor que lo creó (o lo reactivó); opcional porque los de la semilla no tienen. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_gestor")
    private Gestor gestor;

    protected TipoServicio() {
    }

    public TipoServicio(String nombre, String icono, boolean requiereMatricula, Gestor gestor) {
        this.nombre = nombre;
        this.icono = icono;
        this.requiereMatricula = requiereMatricula;
        this.gestor = gestor;
    }

    public void renombrar(String nombre) {
        this.nombre = nombre;
    }

    public void cambiarIcono(String icono) {
        this.icono = icono;
    }

    public void cambiarRequiereMatricula(boolean requiereMatricula) {
        this.requiereMatricula = requiereMatricula;
    }

    /** Vuelve a dar de alta un tipo dado de baja, con los datos nuevos, a nombre del gestor que lo reactiva. */
    public void reactivar(String nombre, String icono, boolean requiereMatricula, Gestor gestor) {
        restaurar();
        renombrar(nombre);
        cambiarIcono(icono);
        cambiarRequiereMatricula(requiereMatricula);
        this.gestor = gestor;
    }

    public Long getId() {
        return id;
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
