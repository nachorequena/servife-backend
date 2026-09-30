package ar.edu.iessf.servife.gestion.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@RequestMapping("/admin")
public class AdminValidacionController {

    /** E5 · GET /admin/validaciones · CU14 · Expedientes pendientes. */
    @GetMapping("/validaciones")
    public ResponseEntity<Void> listarPendientes() {
        throw new NoImplementadoException("E5");
    }

    /** E6 · PATCH /admin/prestadores/{uuid}/validacion · CU14 · Aprueba o rechaza con motivo. 409 si el tipo de servicio requiere matrícula y no hay certificación aprobada (D01). */
    @PatchMapping("/prestadores/{uuid}/validacion")
    public ResponseEntity<Void> validar(@PathVariable UUID uuid) {
        throw new NoImplementadoException("E6");
    }

    /** E8 · GET /admin/prestadores/{uuid}/documentos · CU14 · Descarga controlada del expediente. Nunca por URL pública (.ai/07-security.md). */
    @GetMapping("/prestadores/{uuid}/documentos")
    public ResponseEntity<Void> obtenerDocumentos(@PathVariable UUID uuid) {
        throw new NoImplementadoException("E8");
    }
}
