package ar.edu.iessf.servife.gestion.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.paginacion.Paginacion;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.gestion.domain.Chat;
import ar.edu.iessf.servife.gestion.domain.Mensaje;
import ar.edu.iessf.servife.gestion.dto.ChatResponse;
import ar.edu.iessf.servife.gestion.dto.ContraparteResponse;
import ar.edu.iessf.servife.gestion.dto.ConversacionResponse;
import ar.edu.iessf.servife.gestion.dto.MensajeResponse;
import ar.edu.iessf.servife.gestion.repository.BandejaDeMensajesRepository;
import ar.edu.iessf.servife.gestion.repository.ChatRepository;
import ar.edu.iessf.servife.gestion.repository.MensajeRepository;
import ar.edu.iessf.servife.identidad.domain.Cuenta;
import ar.edu.iessf.servife.identidad.service.Cuentas;
import ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud;
import ar.edu.iessf.servife.solicitudes.domain.Solicitud;

/**
 * Chat de la solicitud (CU07, D33): E12 (bandeja), E1 (leer) y E2 (escribir). Sin consulta previa: cada
 * solicitud tiene su chat, que crea C1 llamando a {@link #crearPara}. Solo el cliente y el prestador de la
 * solicitud ven el chat (cualquier otro, 404). Se escribe en PENDIENTE, ACEPTADA y EN_CURSO; en los demás
 * estados el chat queda de solo lectura (409 CHAT_CERRADO). Sin avisos por mensaje: la bandeja muestra los
 * no leídos y la app consulta cada 8 s con el chat abierto (D08).
 */
@Service
public class ServicioDeChats {

    static final int LARGO_MAXIMO_TEXTO = 1000;

    /** Estados en los que se puede escribir (D33). */
    static final Set<EstadoSolicitud> ESCRIBIBLES =
        EnumSet.of(EstadoSolicitud.PENDIENTE, EstadoSolicitud.ACEPTADA, EstadoSolicitud.EN_CURSO);

    private final ChatRepository chats;
    private final MensajeRepository mensajes;
    private final BandejaDeMensajesRepository bandeja;
    private final Cuentas cuentas;
    private final UsuarioActual usuarioActual;

    public ServicioDeChats(ChatRepository chats, MensajeRepository mensajes, BandejaDeMensajesRepository bandeja,
            Cuentas cuentas, UsuarioActual usuarioActual) {
        this.chats = chats;
        this.mensajes = mensajes;
        this.bandeja = bandeja;
        this.cuentas = cuentas;
        this.usuarioActual = usuarioActual;
    }

    /** Crea el chat de una solicitud recién guardada, en la transacción de C1. Devuelve su uuid. */
    @Transactional
    public UUID crearPara(Solicitud solicitud) {
        return chats.save(new Chat(solicitud)).getUuid();
    }

    /** El uuid del chat de una solicitud (C3 lo devuelve como uuidChat), o null si no tiene. */
    @Transactional(readOnly = true)
    public UUID uuidDelChat(Solicitud solicitud) {
        return chats.uuidDeLaSolicitud(solicitud.getId()).orElse(null);
    }

    /** E12. Los chats del usuario, los de actividad más reciente primero. */
    @Transactional(readOnly = true)
    public Pagina<ConversacionResponse> conversaciones(Integer page, Integer size) {
        Pageable pagina = Paginacion.pedir(page, size, Sort.unsorted());
        Rol rol = usuarioActual.rol();
        Cuenta yo = cuentas.buscarPorUuid(usuarioActual.uuid(), rol)
            .orElseThrow(() -> new RecursoNoEncontradoException("No existe."));
        return bandeja.listar(yo.getId(), rol, pagina.getPageNumber(), pagina.getPageSize());
    }

    /**
     * E1. Los mensajes del chat, o solo los posteriores al mensaje {@code desde}. Marca como leídos los que
     * escribió la otra parte.
     */
    @Transactional
    public ChatResponse mensajes(UUID uuidChat, UUID desde) {
        Rol rol = usuarioActual.rol();
        Chat chat = parteDe(uuidChat, rol);
        Solicitud s = chat.getSolicitud();
        List<Mensaje> lista;
        if (desde == null) {
            mensajes.marcarLeidos(chat, rol);
            lista = mensajes.findByChatAndEliminadoEnIsNullOrderByIdAsc(chat);
        } else {
            Mensaje ultimo = mensajes.findByUuidAndChatAndEliminadoEnIsNull(desde, chat)
                .orElseThrow(() -> new ValidacionException("desde", "no es un mensaje de este chat"));
            mensajes.marcarLeidos(chat, rol);
            lista = mensajes.findByChatAndIdGreaterThanAndEliminadoEnIsNullOrderByIdAsc(chat, ultimo.getId());
        }
        return new ChatResponse(chat.getUuid(), s.getUuid(), s.getEstado(), ESCRIBIBLES.contains(s.getEstado()),
            contraparte(s, rol), lista.stream().map(m -> aRespuesta(m, rol)).toList());
    }

    /** E2. Guarda un mensaje del usuario en el chat, si la solicitud todavía está abierta. */
    @Transactional
    public MensajeResponse enviar(UUID uuidChat, String texto) {
        Rol rol = usuarioActual.rol();
        Chat chat = parteDe(uuidChat, rol);
        Solicitud s = chat.getSolicitud();
        if (!ESCRIBIBLES.contains(s.getEstado())) {
            throw new ConflictoException("CHAT_CERRADO",
                "El chat está cerrado: la solicitud está " + s.getEstado().etiqueta() + ".");
        }
        String recortado = texto.trim();
        if (recortado.isEmpty()) {
            throw new ValidacionException("texto", "es obligatorio");
        }
        if (recortado.length() > LARGO_MAXIMO_TEXTO) {
            throw new ValidacionException("texto", "tiene que tener como máximo 1000 caracteres");
        }
        Cuenta yo = rol == Rol.CLIENTE ? s.getCliente() : s.getPrestador();
        return aRespuesta(mensajes.save(new Mensaje(chat, rol, yo.getId(), recortado)), rol);
    }

    /** El chat, si el usuario es el cliente o el prestador de su solicitud; si no, 404. */
    private Chat parteDe(UUID uuidChat, Rol rol) {
        UUID yo = usuarioActual.uuid();
        return chats.findByUuidAndEliminadoEnIsNull(uuidChat)
            .filter(c -> !c.getSolicitud().estaEliminado())
            .filter(c -> rol == Rol.CLIENTE && yo.equals(c.getSolicitud().getCliente().getUuid())
                || rol == Rol.PRESTADOR && yo.equals(c.getSolicitud().getPrestador().getUuid()))
            .orElseThrow(() -> new RecursoNoEncontradoException("No existe."));
    }

    private static ContraparteResponse contraparte(Solicitud s, Rol rol) {
        Cuenta otra = rol == Rol.CLIENTE ? s.getPrestador() : s.getCliente();
        return new ContraparteResponse(otra.getUuid(), otra.getNombreApellido());
    }

    private static MensajeResponse aRespuesta(Mensaje m, Rol rol) {
        return new MensajeResponse(m.getUuid(), m.getTexto(), m.getRolEmisor() == rol, m.getCreadoEn());
    }
}
