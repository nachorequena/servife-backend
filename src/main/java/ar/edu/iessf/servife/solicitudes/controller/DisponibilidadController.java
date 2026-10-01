package ar.edu.iessf.servife.solicitudes.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.error.NoImplementadoException;

/**
 * Módulo C — Solicitudes (dueño: Tomás Ferreyra).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * Cada método es un stub que responde 501 hasta que se implemente: definí los DTOs en dto/,
 * la lógica en service/ y reemplazá el throw.
 */
@RestController
@RequestMapping("/prestadores")
public class DisponibilidadController {

    /** C5 · GET /prestadores/{uuid}/disponibilidad · CU05, CU06 · Días en que trabaja el prestador (D09). */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{uuid}/disponibilidad")
    public ResponseEntity<Void> obtenerDisponibilidad(@PathVariable UUID uuid) {
        throw new NoImplementadoException("C5");
    }
}
