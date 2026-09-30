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
@PreAuthorize("hasRole('GESTOR')")
@RestController
@RequestMapping("/admin/metricas")
public class MetricaController {

    /** E9 · GET /admin/metricas · CU12 · Contadores del dashboard: servicios activos y validaciones pendientes (D05). */
    @GetMapping
    public ResponseEntity<Void> obtener() {
        throw new NoImplementadoException("E9");
    }
}
