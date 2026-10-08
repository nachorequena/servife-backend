package ar.edu.iessf.servife.solicitudes.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.catalogo.domain.Disponibilidad;
import ar.edu.iessf.servife.catalogo.repository.DisponibilidadRepository;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.gestion.service.Avisos;
import ar.edu.iessf.servife.gestion.service.TipoDeAviso;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;
import ar.edu.iessf.servife.reputacion.domain.Archivo;
import ar.edu.iessf.servife.reputacion.repository.ArchivoRepository;
import ar.edu.iessf.servife.solicitudes.domain.Solicitud;
import ar.edu.iessf.servife.solicitudes.dto.CrearSolicitudRequest;
import ar.edu.iessf.servife.solicitudes.dto.SolicitudResponse;
import ar.edu.iessf.servife.solicitudes.mapper.SolicitudMapper;
import ar.edu.iessf.servife.solicitudes.repository.SolicitudRepository;

/** Solicitudes de servicio: C1 (crear, CU06). Todo en una transacción: solicitud, imágenes y aviso. */
@Service
public class ServicioDeSolicitudes {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter DIA_MES_ANIO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SolicitudRepository solicitudes;
    private final ClienteRepository clientes;
    private final PrestadorRepository prestadores;
    private final DisponibilidadRepository disponibilidad;
    private final ArchivoRepository archivos;
    private final Avisos avisos;
    private final UsuarioActual usuarioActual;
    private final Clock clock;

    public ServicioDeSolicitudes(SolicitudRepository solicitudes, ClienteRepository clientes,
            PrestadorRepository prestadores, DisponibilidadRepository disponibilidad, ArchivoRepository archivos,
            Avisos avisos, UsuarioActual usuarioActual, Clock clock) {
        this.solicitudes = solicitudes;
        this.clientes = clientes;
        this.prestadores = prestadores;
        this.disponibilidad = disponibilidad;
        this.archivos = archivos;
        this.avisos = avisos;
        this.usuarioActual = usuarioActual;
        this.clock = clock;
    }

    /** C1. Valida en orden prestador, fecha, día de trabajo e imágenes; el primer error corta. */
    @Transactional
    public SolicitudResponse crear(CrearSolicitudRequest pedido) {
        Cliente cliente = clientes.findByUuidAndEliminadoEnIsNull(usuarioActual.uuid())
            .orElseThrow(() -> new RecursoNoEncontradoException("No existe."));

        Prestador prestador = prestadores.findByUuidAndEliminadoEnIsNull(pedido.uuidPrestador())
            .filter(p -> p.getEstadoValidacion() == EstadoValidacion.APROBADO
                && p.getEstadoCuenta() == EstadoCuenta.ACTIVA)
            .orElseThrow(() -> new ValidacionException("uuidPrestador", "no está disponible"));

        LocalDate hoy = LocalDate.now(clock.withZone(ARGENTINA));
        if (pedido.fechaDeseada().isBefore(hoy)) {
            throw new ValidacionException("fechaDeseada", "tiene que ser hoy o una fecha futura");
        }
        int dia = pedido.fechaDeseada().getDayOfWeek().getValue();
        boolean trabajaEseDia = disponibilidad.findByPrestadorAndEliminadoEnIsNullOrderByDiaSemana(prestador).stream()
            .map(Disponibilidad::getDiaSemana)
            .anyMatch(d -> d == dia);
        if (!trabajaEseDia) {
            throw new ValidacionException("fechaDeseada", "el prestador no trabaja ese día");
        }

        List<UUID> pedidas = pedido.imagenIds() == null ? List.of() : pedido.imagenIds();
        List<Archivo> imagenes = imagenesValidas(pedidas, cliente);

        Solicitud solicitud = solicitudes.save(new Solicitud(cliente, prestador, prestador.getTipoServicio(),
            pedido.descripcion().trim(), pedido.fechaDeseada(), horaONula(pedido.horaPreferida()),
            pedido.direccion().trim()));
        for (Archivo imagen : imagenes) {
            vincular(solicitud, imagen);
        }

        avisos.avisar(prestador, TipoDeAviso.SOLICITUD_NUEVA, "Nueva solicitud",
            cliente.getNombreApellido() + " te pidió un servicio para el "
                + pedido.fechaDeseada().format(DIA_MES_ANIO) + ".",
            solicitud.getUuid());

        return SolicitudMapper.aRespuesta(solicitud, pedidas, Rol.CLIENTE);
    }

    /** Cada imagen tiene que existir, ser de este cliente y no estar en otra solicitud; sin repetidas. */
    private List<Archivo> imagenesValidas(List<UUID> uuids, Cliente cliente) {
        if (new HashSet<>(uuids).size() != uuids.size()) {
            throw imagenInvalida();
        }
        List<Archivo> resultado = new ArrayList<>();
        for (UUID uuid : uuids) {
            Archivo a = archivos.findByUuidAndEliminadoEnIsNull(uuid).orElseThrow(ServicioDeSolicitudes::imagenInvalida);
            boolean propia = a.getRolPropietario() == Rol.CLIENTE && a.getIdPropietario().equals(cliente.getId());
            if (!propia || solicitudes.imagenEnUso(a.getId())) {
                throw imagenInvalida();
            }
            resultado.add(a);
        }
        return resultado;
    }

    /** El índice único cubre la carrera: si otra solicitud ganó la imagen, es el mismo error de validación. */
    private void vincular(Solicitud solicitud, Archivo imagen) {
        try {
            solicitudes.vincularImagen(solicitud.getId(), imagen.getId());
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("uq_solicitud_imagenes_archivo")) {
                throw imagenInvalida();
            }
            throw e;
        }
    }

    private static ValidacionException imagenInvalida() {
        return new ValidacionException("imagenIds", "alguna imagen no es válida");
    }

    private static String horaONula(String hora) {
        return hora == null || hora.isBlank() ? null : hora.trim();
    }
}
