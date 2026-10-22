package ar.edu.iessf.servife.gestion.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.gestion.dto.ConversacionResponse;
import ar.edu.iessf.servife.gestion.service.ServicioDeChats;

/**
 * Módulo E — Mensajería y avisos (porción 4: Pedro Soria). Bandeja Mensajes (D04), según
 * servife-ia/.ai/05-api-contract.md.
 */
@RestController
@RequestMapping("/conversaciones")
public class ConversacionController {

    private final ServicioDeChats chats;

    public ConversacionController(ServicioDeChats chats) {
        this.chats = chats;
    }

    /** E12 · GET /conversaciones · D04 · Los chats de las solicitudes del usuario, con el último mensaje y los no leídos. Solo lectura, no inicia conversaciones. */
    @PreAuthorize("hasAnyRole('CLIENTE', 'PRESTADOR')")
    @GetMapping
    public Pagina<ConversacionResponse> listar(@RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return chats.conversaciones(page, size);
    }
}
