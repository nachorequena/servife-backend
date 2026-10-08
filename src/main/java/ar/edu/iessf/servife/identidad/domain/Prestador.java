package ar.edu.iessf.servife.identidad.domain;

import java.time.LocalDate;

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.common.auditoria.EntidadBase;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.service.Cuentas;
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
 * Cuenta de prestador (tabla prestadores). Acá van las columnas de identidad; las de perfil
 * (zona, lat, lng, radio_km, descripcion, valoracion_promedio) las agrega Catálogo en este mismo archivo.
 */
@Entity
@Table(name = "prestadores")
public class Prestador extends EntidadBase implements Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_prestador")
    private Long id;

    @Column(name = "nombre_apellido", nullable = false)
    private String nombreApellido;

    @Column(name = "fec_nacimiento")
    private LocalDate fecNacimiento;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "contrasenia", nullable = false)
    private String contrasenia;

    @Column(name = "telefono")
    private String telefono;

    @Column(name = "direccion")
    private String direccion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_servicio", nullable = false)
    private TipoServicio tipoServicio;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_validacion", nullable = false)
    private EstadoValidacion estadoValidacion = EstadoValidacion.PENDIENTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_cuenta", nullable = false)
    private EstadoCuenta estadoCuenta = EstadoCuenta.ACTIVA;

    protected Prestador() {
    }

    /** Nace PENDIENTE de validación; el email se guarda normalizado y la contraseña ya hasheada. */
    public Prestador(String nombreApellido, String email, String contraseniaHash, TipoServicio tipoServicio) {
        this.nombreApellido = nombreApellido;
        this.email = Cuentas.normalizar(email);
        this.contrasenia = contraseniaHash;
        this.tipoServicio = tipoServicio;
    }

    @Override
    public Rol getRol() {
        return Rol.PRESTADOR;
    }

    @Override
    public void cambiarContrasenia(String hash) {
        this.contrasenia = hash;
    }

    @Override
    public String getNombreApellido() {
        return nombreApellido;
    }

    public void setNombreApellido(String nombreApellido) {
        this.nombreApellido = nombreApellido;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public String getContrasenia() {
        return contrasenia;
    }

    public LocalDate getFecNacimiento() {
        return fecNacimiento;
    }

    public void setFecNacimiento(LocalDate fecNacimiento) {
        this.fecNacimiento = fecNacimiento;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public TipoServicio getTipoServicio() {
        return tipoServicio;
    }

    public EstadoValidacion getEstadoValidacion() {
        return estadoValidacion;
    }

    public void setEstadoValidacion(EstadoValidacion estadoValidacion) {
        this.estadoValidacion = estadoValidacion;
    }

    @Override
    public EstadoCuenta getEstadoCuenta() {
        return estadoCuenta;
    }

    public void setEstadoCuenta(EstadoCuenta estadoCuenta) {
        this.estadoCuenta = estadoCuenta;
    }
}
