package ar.edu.iessf.servife.catalogo.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.catalogo.dto.ActualizarPerfilDePrestadorRequest;
import ar.edu.iessf.servife.catalogo.dto.DisponibilidadRequest;
import ar.edu.iessf.servife.catalogo.dto.DisponibilidadResponse;
import ar.edu.iessf.servife.catalogo.dto.PerfilDeServicioResponse;
import ar.edu.iessf.servife.catalogo.service.ServicioDePerfilDePrestador;
import ar.edu.iessf.servife.common.error.NoImplementadoException;
import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import jakarta.validation.Valid;

/**
 * Módulo B — Catálogo y búsqueda (dueño: Juan Pablo Saravia).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * B7, B8 y GET /prestadores/me/perfil están implementados; B5 y B6 siguen como stub (501).
 */
@RestController
@RequestMapping("/prestadores")
public class PrestadorController {

    private final ServicioDePerfilDePrestador perfiles;

    private final UsuarioActual usuarioActual;

    public PrestadorController(ServicioDePerfilDePrestador perfiles, UsuarioActual usuarioActual) {
        this.perfiles = perfiles;
        this.usuarioActual = usuarioActual;
    }

    /** B5 · GET /prestadores · CU04 · Solo APROBADOS y no suspendidos. Filtros: tipoServicioId, lat, lng, radioKm, puntajeMin, dias, orden. Sin filtro de precio (D02). Paginado. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<Void> buscar() {
        throw new NoImplementadoException("B5");
    }

    /** GET /prestadores/me/perfil · CU03 · Perfil de servicio propio, para precargar la pantalla. */
    @PreAuthorize("hasRole('PRESTADOR')")
    @GetMapping("/me/perfil")
    public ResponseEntity<PerfilDeServicioResponse> obtenerMiPerfil() {
        return ResponseEntity.ok(perfiles.obtenerPerfil(usuarioActual.uuid()));
    }

    /** B6 · GET /prestadores/{uuid} · CU05 · Ficha del prestador: sello verificado, certificaciones aprobadas, cantidad de servicios realizados (D01, D07). Sin tarifa (D02). */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{uuid}")
    public ResponseEntity<Void> obtener(@PathVariable UUID uuid) {
        throw new NoImplementadoException("B6");
    }

    /** B7 · PUT /prestadores/me/perfil · CU03 · Rubro, zona, radio y descripción. Sin tarifa (D02). Cambiar de rubro vuelve el estado a PENDIENTE. */
    @PreAuthorize("hasRole('PRESTADOR')")
    @PutMapping("/me/perfil")
    public ResponseEntity<PerfilDeServicioResponse> actualizarMiPerfil(
            @Valid @RequestBody ActualizarPerfilDePrestadorRequest pedido) {
        return ResponseEntity.ok(perfiles.actualizarPerfil(usuarioActual.uuid(), pedido));
    }

    /** B8 · PUT /prestadores/me/disponibilidad · D09 · Días de la semana en que trabaja. Reemplaza el set completo. */
    @PreAuthorize("hasRole('PRESTADOR')")
    @PutMapping("/me/disponibilidad")
    public ResponseEntity<DisponibilidadResponse> reemplazarMiDisponibilidad(
            @Valid @RequestBody DisponibilidadRequest pedido) {
        return ResponseEntity.ok(perfiles.reemplazarDisponibilidad(usuarioActual.uuid(), pedido));
    }
}
