package ar.edu.iessf.servife.gestion.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.error.NoImplementadoException;

/**
 * Módulo E — Gestión, validación y mensajería (dueño: Ignacio Requena).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * Cada método es un stub que responde 501 hasta que se implemente: definí los DTOs en dto/,
 * la lógica en service/ y reemplazá el throw.
 */
@RestController
@RequestMapping("/chats")
public class MensajeController {

    /** E1 · GET /chats/{uuid}/mensajes · CU07 · Mensajes desde ?desde=. La app consulta cada 8 s con la pantalla abierta (D08). */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{uuid}/mensajes")
    public ResponseEntity<Void> listar(@PathVariable UUID uuid) {
        throw new NoImplementadoException("E1");
    }

    /** E2 · POST /chats/{uuid}/mensajes · CU07 · Chat de solicitud habilitado desde PENDIENTE (D03). Hasta qué estado se puede escribir: sin definir (.ai/06-roadmap.md). */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{uuid}/mensajes")
    public ResponseEntity<Void> enviar(@PathVariable UUID uuid) {
        throw new NoImplementadoException("E2");
    }
}
