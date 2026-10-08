package ar.edu.iessf.servife.identidad.domain;

import java.math.BigDecimal;
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
 * Cuenta de prestador (tabla prestadores): columnas de identidad y de perfil de servicio
 * (zona, lat, lng, radio_km, descripcion; valoracion_promedio es solo lectura).
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

    @Column(name = "zona")
    private String zona;

    @Column(name = "lat", precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(name = "lng", precision = 9, scale = 6)
    private BigDecimal lng;

    @Column(name = "radio_km")
    private Integer radioKm;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    /** Solo lectura: lo calcula el módulo de valoraciones. */
    @Column(name = "valoracion_promedio", precision = 2, scale = 1, insertable = false, updatable = false)
    private BigDecimal valoracionPromedio;

    @Override
    public Long getId() {
        return id;
    }

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

    /**
     * Cambia el rubro. Si es distinto al actual, el prestador vuelve a PENDIENTE (sea cual sea su
     * estado de validación): se revisa de nuevo con el rubro nuevo.
     */
    public void cambiarTipoServicio(TipoServicio nuevo) {
        if (!nuevo.getUuid().equals(tipoServicio.getUuid())) {
            this.tipoServicio = nuevo;
            this.estadoValidacion = EstadoValidacion.PENDIENTE;
        }
    }

    /** Zona, punto (lat/lng), radio y descripción ya normalizados por el servicio. */
    public void actualizarPerfil(String zona, BigDecimal lat, BigDecimal lng, Integer radioKm, String descripcion) {
        this.zona = zona;
        this.lat = lat;
        this.lng = lng;
        this.radioKm = radioKm;
        this.descripcion = descripcion;
    }

    public String getZona() {
        return zona;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public Integer getRadioKm() {
        return radioKm;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public BigDecimal getValoracionPromedio() {
        return valoracionPromedio;
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
