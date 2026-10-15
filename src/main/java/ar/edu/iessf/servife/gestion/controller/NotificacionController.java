package ar.edu.iessf.servife.gestion.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.paginacion.Paginacion;
import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.gestion.dto.AvisoResponse;
import ar.edu.iessf.servife.gestion.dto.ContadorDeAvisosResponse;
import ar.edu.iessf.servife.gestion.service.Avisos;

/**
 * E11 · Avisos dentro de la app (porción 4: Pedro Soria). Endpoints según
 * servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo. Siempre del usuario del token.
 */
@RestController
@RequestMapping("/notificaciones")
public class NotificacionController {

    private final Avisos avisos;
    private final UsuarioActual usuarioActual;

    public NotificacionController(Avisos avisos, UsuarioActual usuarioActual) {
        this.avisos = avisos;
        this.usuarioActual = usuarioActual;
    }

    /** E11 · GET /notificaciones · Historial de avisos del usuario, los más nuevos primero. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Pagina<AvisoResponse> listar(@RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Pageable pagina = Paginacion.pedir(page, size, Sort.unsorted());
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
