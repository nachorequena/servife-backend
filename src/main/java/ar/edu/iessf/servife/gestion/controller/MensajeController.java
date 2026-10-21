package ar.edu.iessf.servife.gestion.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.gestion.dto.ChatResponse;
import ar.edu.iessf.servife.gestion.dto.EnviarMensajeRequest;
import ar.edu.iessf.servife.gestion.dto.MensajeResponse;
import ar.edu.iessf.servife.gestion.service.ServicioDeChats;
import jakarta.validation.Valid;

/**
 * Módulo E — Mensajería y avisos (porción 4: Pedro Soria). Chat de la solicitud (CU07, D33), según
 * servife-ia/.ai/05-api-contract.md. Solo el cliente y el prestador de la solicitud; el gestor no tiene chat.
 */
@RestController
@RequestMapping("/chats")
public class MensajeController {

    private final ServicioDeChats chats;

    public MensajeController(ServicioDeChats chats) {
        this.chats = chats;
    }

    /** E1 · GET /chats/{uuid}/mensajes · CU07 · Todo el chat, o solo lo posterior a ?desde=. La app consulta cada 8 s con la pantalla abierta (D08). */
    @PreAuthorize("hasAnyRole('CLIENTE', 'PRESTADOR')")
    @GetMapping("/{uuid}/mensajes")
    public ChatResponse listar(@PathVariable UUID uuid, @RequestParam(required = false) UUID desde) {
        return chats.mensajes(uuid, desde);
    }

    /** E2 · POST /chats/{uuid}/mensajes · CU07 · Se escribe con la solicitud en PENDIENTE, ACEPTADA o EN_CURSO (D33); si no, 409 CHAT_CERRADO. */
    @PreAuthorize("hasAnyRole('CLIENTE', 'PRESTADOR')")
    @PostMapping("/{uuid}/mensajes")
    public ResponseEntity<MensajeResponse> enviar(@PathVariable UUID uuid, @Valid @RequestBody EnviarMensajeRequest pedido) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chats.enviar(uuid, pedido.texto()));
    }
}
