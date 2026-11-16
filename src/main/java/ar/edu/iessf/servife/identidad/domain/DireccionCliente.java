package ar.edu.iessf.servife.identidad.domain;

import ar.edu.iessf.servife.common.auditoria.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Una dirección guardada de un cliente (tabla direcciones_cliente). La principal se copia en clientes.direccion. */
@Entity
@Table(name = "direcciones_cliente")
public class DireccionCliente extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_direccion_cliente")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente", nullable = false, updatable = false)
    private Cliente cliente;

    @Column(name = "alias", nullable = false)
    private String alias;

    @Column(name = "direccion", nullable = false)
    private String direccion;

    @Column(name = "principal", nullable = false)
    private boolean principal;

    protected DireccionCliente() {
    }

    public DireccionCliente(Cliente cliente, String alias, String direccion) {
        this.cliente = cliente;
        this.alias = alias;
        this.direccion = direccion;
    }

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public String getAlias() {
        return alias;
    }

    public String getDireccion() {
        return direccion;
    }

    public boolean isPrincipal() {
        return principal;
    }

    public void editar(String alias, String direccion) {
        this.alias = alias;
        this.direccion = direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public void setPrincipal(boolean principal) {
        this.principal = principal;
    }
}
