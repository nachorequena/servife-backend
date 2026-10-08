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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.catalogo.domain.Disponibilidad;
import ar.edu.iessf.servife.catalogo.repository.DisponibilidadRepository;
import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.formato.Dinero;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.paginacion.Paginacion;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.gestion.service.Avisos;
import ar.edu.iessf.servife.gestion.service.TipoDeAviso;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.Cuenta;
import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;
import ar.edu.iessf.servife.reputacion.domain.Archivo;
import ar.edu.iessf.servife.reputacion.repository.ArchivoRepository;
import ar.edu.iessf.servife.solicitudes.domain.AccionSobreSolicitud;
import ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud;
import ar.edu.iessf.servife.solicitudes.domain.MaquinaDeEstados;
import ar.edu.iessf.servife.solicitudes.domain.Solicitud;
import ar.edu.iessf.servife.solicitudes.dto.CambioDeEstadoRequest;
import ar.edu.iessf.servife.solicitudes.dto.CrearSolicitudRequest;
import ar.edu.iessf.servife.solicitudes.dto.SolicitudEnListaResponse;
import ar.edu.iessf.servife.solicitudes.dto.SolicitudResponse;
import ar.edu.iessf.servife.solicitudes.mapper.SolicitudMapper;
import ar.edu.iessf.servife.solicitudes.repository.SolicitudRepository;

/** Solicitudes de servicio: C1 (crear, CU06), C2 (listar), C3 (ver) y C4 (cambiar estado).
 * C1 corre en una sola transacción: solicitud, imágenes y aviso. */
@Service
public class ServicioDeSolicitudes {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final int LARGO_DESCRIPCION_EN_LISTA = 140;
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

        LocalDate hoy = hoyEnArgentina();
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

        // mismo orden que C3 (por id_archivo), no el del pedido
        return SolicitudMapper.aRespuesta(solicitud, solicitudes.uuidsDeImagenes(solicitud.getId()), Rol.CLIENTE, hoy);
    }

    private LocalDate hoyEnArgentina() {
        return LocalDate.now(clock.withZone(ARGENTINA));
    }

    /**
     * C2. Las solicitudes del usuario (cliente o prestador), más recientes primero; sin estados pedidos
     * devuelve todos. Las partes y el tipo vienen en la misma consulta (sin N+1).
     */
    @Transactional(readOnly = true)
    public Pagina<SolicitudEnListaResponse> listarMias(List<EstadoSolicitud> estados, Integer page, Integer size) {
        Rol rol = usuarioActual.rol();
        List<EstadoSolicitud> filtro = estados == null || estados.isEmpty() ? List.of(EstadoSolicitud.values()) : estados;
        Pageable pagina = Paginacion.pedir(page, size, Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("uuid")));
        Page<Solicitud> resultado = rol == Rol.CLIENTE
            ? solicitudes.findByClienteAndEstadoInAndEliminadoEnIsNull(clienteActual(), filtro, pagina)
            : solicitudes.findByPrestadorAndEstadoInAndEliminadoEnIsNull(prestadorActual(), filtro, pagina);
        return Pagina.de(resultado, s -> enLista(s, rol));
    }

    /** C3. Si no sos parte de la solicitud (o está dada de baja), 404. */
    @Transactional(readOnly = true)
    public SolicitudResponse obtener(UUID uuid) {
        Rol rol = usuarioActual.rol();
        Solicitud s = parteDe(uuid, rol);
        return SolicitudMapper.aRespuesta(s, solicitudes.uuidsDeImagenes(s.getId()), rol, hoyEnArgentina());
    }

    /**
     * C4. Una sola transacción: valida la transición (MaquinaDeEstados) y las reglas de fecha y extras,
     * aplica el UPDATE condicional y avisa a la contraparte. Si otro cambió la solicitud entre la lectura
     * y el UPDATE (0 filas), 409 TRANSICION_INVALIDA y no se avisa nada.
     */
    @Transactional
    public SolicitudResponse cambiarEstado(UUID uuid, CambioDeEstadoRequest pedido) {
        Rol rol = usuarioActual.rol();
        Solicitud s = parteDe(uuid, rol);
        AccionSobreSolicitud accion = pedido.accion();
        EstadoSolicitud origen = s.getEstado();
        EstadoSolicitud destino = MaquinaDeEstados.destino(origen, accion, rol);

        if (accion == AccionSobreSolicitud.INICIAR && s.getFechaDeseada() != null
                && hoyEnArgentina().isBefore(s.getFechaDeseada())) {
            throw new ConflictoException("TODAVIA_NO_ES_LA_FECHA", "Todavía no llegó la fecha del trabajo.");
        }
        if (pedido.precioAcordado() != null && accion != AccionSobreSolicitud.ACEPTAR) {
            throw new ValidacionException("precioAcordado", "solo se puede indicar al aceptar");
        }
        String motivo = pedido.motivo() == null || pedido.motivo().isBlank() ? null : pedido.motivo().trim();
        if (motivo != null
                && accion != AccionSobreSolicitud.RECHAZAR && accion != AccionSobreSolicitud.CANCELAR) {
            throw new ValidacionException("motivo", "solo se puede indicar al rechazar o cancelar");
        }

        int filas = solicitudes.cambiarEstado(s.getId(), origen, destino, motivo,
            accion == AccionSobreSolicitud.CANCELAR ? rol : null, pedido.precioAcordado(), clock.instant());
        if (filas == 0) {
            throw new ConflictoException("TRANSICION_INVALIDA", "La solicitud cambió mientras tanto. Recargala.");
        }

        // el UPDATE limpió el contexto: se recarga para responder y para avisar con los datos vigentes
        Solicitud actual = solicitudes.findDetalleByUuidAndEliminadoEnIsNull(uuid).orElseThrow();
        avisarCambio(actual, destino, rol);
        return SolicitudMapper.aRespuesta(actual, solicitudes.uuidsDeImagenes(actual.getId()), rol, hoyEnArgentina());
    }

    /** Aviso a la otra parte: quién actuó, qué hizo, y motivo o precio si hay. */
    private void avisarCambio(Solicitud s, EstadoSolicitud destino, Rol actor) {
        boolean actuoElCliente = actor == Rol.CLIENTE;
        Cuenta quien = actuoElCliente ? s.getCliente() : s.getPrestador();
        Cuenta destinatario = actuoElCliente ? s.getPrestador() : s.getCliente();
        TipoDeAviso tipo;
        String titulo;
        String verbo;
        switch (destino) {
            case ACEPTADA -> { tipo = TipoDeAviso.SOLICITUD_ACEPTADA; titulo = "Aceptaron tu solicitud"; verbo = "aceptó"; }
            case RECHAZADA -> { tipo = TipoDeAviso.SOLICITUD_RECHAZADA; titulo = "Rechazaron tu solicitud"; verbo = "rechazó"; }
            case EN_CURSO -> { tipo = TipoDeAviso.SOLICITUD_EN_CURSO; titulo = "Empezó el trabajo"; verbo = "inició"; }
            case FINALIZADA -> { tipo = TipoDeAviso.SOLICITUD_FINALIZADA; titulo = "Terminó el trabajo"; verbo = "finalizó"; }
            case CANCELADA -> { tipo = TipoDeAviso.SOLICITUD_CANCELADA; titulo = "Cancelaron la solicitud"; verbo = "canceló"; }
            default -> throw new IllegalStateException("Sin aviso para " + destino);
        }
        StringBuilder cuerpo = new StringBuilder(quien.getNombreApellido()).append(' ').append(verbo)
            .append(" la solicitud");
        if (s.getFechaDeseada() != null) {
            cuerpo.append(" del ").append(s.getFechaDeseada().format(DIA_MES_ANIO));
        }
        cuerpo.append('.');
        if (s.getMotivo() != null && (destino == EstadoSolicitud.RECHAZADA || destino == EstadoSolicitud.CANCELADA)) {
            cuerpo.append(" Motivo: ").append(s.getMotivo());
        }
        if (destino == EstadoSolicitud.ACEPTADA && s.getPrecioAcordado() != null) {
            cuerpo.append(" Precio acordado: ").append(Dinero.pesos(s.getPrecioAcordado()));
        }
        avisos.avisar(destinatario, tipo, titulo, cuerpo.toString(), s.getUuid());
    }

    /** La solicitud si el usuario es parte de ella; si no existe, está dada de baja o es ajena, 404. */
    private Solicitud parteDe(UUID uuid, Rol rol) {
        UUID yo = usuarioActual.uuid();
        return solicitudes.findDetalleByUuidAndEliminadoEnIsNull(uuid)
            .filter(x -> rol == Rol.CLIENTE && yo.equals(x.getCliente().getUuid())
                || rol == Rol.PRESTADOR && yo.equals(x.getPrestador().getUuid()))
            .orElseThrow(() -> new RecursoNoEncontradoException("No existe."));
    }

    private SolicitudEnListaResponse enLista(Solicitud s, Rol rol) {
        String descripcion = s.getDescripcion();
        // se corta por caracteres Unicode, no por unidades UTF-16: un emoji no queda partido
        if (descripcion.codePointCount(0, descripcion.length()) > LARGO_DESCRIPCION_EN_LISTA) {
            descripcion = descripcion.substring(0, descripcion.offsetByCodePoints(0, LARGO_DESCRIPCION_EN_LISTA)) + "…";
        }
        return new SolicitudEnListaResponse(s.getUuid(), s.getEstado(), SolicitudMapper.contraparte(s, rol),
            SolicitudMapper.tipo(s.getTipoServicio()),
            s.getFechaDeseada(), s.getHoraPreferida(), descripcion, s.getDireccion(), s.getCreadoEn());
    }

    private Cliente clienteActual() {
        return clientes.findByUuidAndEliminadoEnIsNull(usuarioActual.uuid())
            .orElseThrow(() -> new RecursoNoEncontradoException("No existe."));
    }

    private Prestador prestadorActual() {
        return prestadores.findByUuidAndEliminadoEnIsNull(usuarioActual.uuid())
            .orElseThrow(() -> new RecursoNoEncontradoException("No existe."));
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
