package ar.edu.iessf.servife.gestion.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/prestadores/me/documentos")
public class DocumentoController {

    /** E7 · POST /prestadores/me/documentos · CU14 · DNI_FRENTE, DNI_DORSO, CERTIFICACION (D01). Se guardan fuera de la base. */
    @PreAuthorize("hasRole('PRESTADOR')")
    @PostMapping
    public ResponseEntity<Void> subir() {
        throw new NoImplementadoException("E7");
    }
}
