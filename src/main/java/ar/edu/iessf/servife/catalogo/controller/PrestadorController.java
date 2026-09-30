package ar.edu.iessf.servife.catalogo.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.error.NoImplementadoException;

/**
 * Módulo B — Catálogo y búsqueda (dueño: Juan Pablo Saravia).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * Cada método es un stub que responde 501 hasta que se implemente: definí los DTOs en dto/,
 * la lógica en service/ y reemplazá el throw.
 */
@RestController
@RequestMapping("/prestadores")
public class PrestadorController {

    /** B5 · GET /prestadores · CU04 · Solo APROBADOS y no suspendidos. Filtros: tipoServicioId, lat, lng, radioKm, puntajeMin, dias, orden. Sin filtro de precio (D02). Paginado. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<Void> buscar() {
        throw new NoImplementadoException("B5");
    }

    /** B6 · GET /prestadores/{uuid} · CU05 · Ficha del prestador: sello verificado, certificaciones aprobadas, cantidad de servicios realizados (D01, D07). Sin tarifa (D02). */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{uuid}")
    public ResponseEntity<Void> obtener(@PathVariable UUID uuid) {
        throw new NoImplementadoException("B6");
    }

    /** B7 · PUT /prestadores/me/perfil · CU03 · Rubro, zona, radio y descripción. Sin tarifa (D02). */
    @PreAuthorize("hasRole('PRESTADOR')")
    @PutMapping("/me/perfil")
    public ResponseEntity<Void> actualizarMiPerfil() {
        throw new NoImplementadoException("B7");
    }

    /** B8 · PUT /prestadores/me/disponibilidad · D09 · Días de la semana en que trabaja. Reemplaza el set completo. */
    @PreAuthorize("hasRole('PRESTADOR')")
    @PutMapping("/me/disponibilidad")
    public ResponseEntity<Void> reemplazarMiDisponibilidad() {
        throw new NoImplementadoException("B8");
    }
}
