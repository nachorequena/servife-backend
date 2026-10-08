package ar.edu.iessf.servife.solicitudes.domain;

import java.time.LocalDate;

import org.hibernate.annotations.DynamicUpdate;

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.common.auditoria.EntidadBase;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Pedido de un cliente a un prestador (tabla solicitudes_servicio). Los cambios de estado los decide
 * MaquinaDeEstados y se aplican con un UPDATE condicional del repositorio (C4); la entidad no los muta. DynamicUpdate: el UPDATE incluye solo lo modificado.
 */
@Entity
@Table(name = "solicitudes_servicio")
@DynamicUpdate
public class Solicitud extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente", nullable = false, updatable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_prestador", nullable = false, updatable = false)
    private Prestador prestador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_servicio", nullable = false, updatable = false)
    private TipoServicio tipoServicio;

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    @Column(name = "fecha_deseada")
    private LocalDate fechaDeseada;

    @Column(name = "hora_preferida")
    private String horaPreferida;

    @Column(name = "direccion")
    private String direccion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;

    /** Motivo opcional al rechazar o cancelar. */
    @Column(name = "motivo")
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelada_por")
    private Rol canceladaPor;

    /** En centavos, lo carga el prestador al aceptar (D02). */
    @Column(name = "precio_acordado")
    private Long precioAcordado;

    protected Solicitud() {
    }

    public Solicitud(Cliente cliente, Prestador prestador, TipoServicio tipoServicio, String descripcion,
            LocalDate fechaDeseada, String horaPreferida, String direccion) {
        this.cliente = cliente;
        this.prestador = prestador;
        this.tipoServicio = tipoServicio;
        this.descripcion = descripcion;
        this.fechaDeseada = fechaDeseada;
        this.horaPreferida = horaPreferida;
        this.direccion = direccion;
    }

    /** Solo para armar escenarios de prueba: en producción el estado cambia únicamente por el UPDATE condicional de C4. */
    void restaurar(EstadoSolicitud estado, String motivo, Rol canceladaPor, Long precioAcordado) {
        this.estado = estado;
        this.motivo = motivo;
        this.canceladaPor = canceladaPor;
        this.precioAcordado = precioAcordado;
    }

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public Prestador getPrestador() {
        return prestador;
    }

    public TipoServicio getTipoServicio() {
        return tipoServicio;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public LocalDate getFechaDeseada() {
        return fechaDeseada;
    }

    public String getHoraPreferida() {
        return horaPreferida;
    }

    public String getDireccion() {
        return direccion;
    }

    public EstadoSolicitud getEstado() {
        return estado;
    }

    public String getMotivo() {
        return motivo;
    }

    public Rol getCanceladaPor() {
        return canceladaPor;
    }

    public Long getPrecioAcordado() {
        return precioAcordado;
    }
}
