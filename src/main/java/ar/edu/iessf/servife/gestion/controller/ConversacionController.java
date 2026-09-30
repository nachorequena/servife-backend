package ar.edu.iessf.servife.gestion.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/conversaciones")
public class ConversacionController {

    /** E12 · GET /conversaciones · D04 · Bandeja Mensajes: consultas previas y chats de solicitudes. Solo lectura, no inicia conversaciones. */
    @PreAuthorize("hasAnyRole('CLIENTE', 'PRESTADOR')")
    @GetMapping
    public ResponseEntity<Void> listar() {
        throw new NoImplementadoException("E12");
    }
}
