package ar.edu.iessf.servife.solicitudes.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.catalogo.dto.DisponibilidadResponse;
import ar.edu.iessf.servife.catalogo.service.ServicioDePerfilDePrestador;

/**
 * Módulo C — Solicitudes (dueño: Tomás Ferreyra).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * C5 está implementado (la lógica vive en el servicio de perfil de prestador de Catálogo).
 */
@RestController
@RequestMapping("/prestadores")
public class DisponibilidadController {

    private final ServicioDePerfilDePrestador perfiles;

    public DisponibilidadController(ServicioDePerfilDePrestador perfiles) {
        this.perfiles = perfiles;
    }

    /** C5 · GET /prestadores/{uuid}/disponibilidad · CU05, CU06 · Días en que trabaja el prestador (D09). */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{uuid}/disponibilidad")
    public ResponseEntity<DisponibilidadResponse> obtenerDisponibilidad(@PathVariable UUID uuid) {
        return ResponseEntity.ok(perfiles.obtenerDisponibilidad(uuid));
    }
}
