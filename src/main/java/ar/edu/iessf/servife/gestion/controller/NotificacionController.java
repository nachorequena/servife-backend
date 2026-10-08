package ar.edu.iessf.servife.gestion.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.gestion.dto.AvisoResponse;
import ar.edu.iessf.servife.gestion.dto.ContadorDeAvisosResponse;
import ar.edu.iessf.servife.gestion.service.Avisos;

/**
 * Módulo E — Gestión, validación y mensajería (dueño: Ignacio Requena).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * E11 (avisos dentro de la app) está implementado; el resto de los controllers del módulo
 * siguen siendo stubs que responden 501.
 */
@RestController
@RequestMapping("/notificaciones")
public class NotificacionController {

    private static final int TAMANIO_POR_DEFECTO = 20;
    private static final int TAMANIO_MAXIMO = 50;

    private final Avisos avisos;
    private final UsuarioActual usuarioActual;

    public NotificacionController(Avisos avisos, UsuarioActual usuarioActual) {
        this.avisos = avisos;
        this.usuarioActual = usuarioActual;
    }

    /** E11 · GET /notificaciones · Historial de avisos del usuario, los más nuevos primero. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Pagina<AvisoResponse> listar(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANIO_POR_DEFECTO) int size) {
        PageRequest pagina = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), TAMANIO_MAXIMO));
        return avisos.listar(usuarioActual.uuid(), usuarioActual.rol(), pagina);
    }

    /** E11 · GET /notificaciones/no-leidas · Cantidad de avisos sin leer. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/no-leidas")
    public ContadorDeAvisosResponse contarNoLeidos() {
        return new ContadorDeAvisosResponse(avisos.contarNoLeidos(usuarioActual.uuid(), usuarioActual.rol()));
    }

    /** E11 · PATCH /notificaciones/{uuid}/leida · Marca un aviso propio como leído. */
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{uuid}/leida")
    public ResponseEntity<Void> marcarLeida(@PathVariable UUID uuid) {
        avisos.marcarLeido(usuarioActual.uuid(), usuarioActual.rol(), uuid);
        return ResponseEntity.noContent().build();
    }
}
