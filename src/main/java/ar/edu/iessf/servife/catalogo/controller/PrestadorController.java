package ar.edu.iessf.servife.catalogo.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.catalogo.dto.ActualizarPerfilDePrestadorRequest;
import ar.edu.iessf.servife.catalogo.dto.DisponibilidadRequest;
import ar.edu.iessf.servife.catalogo.dto.DisponibilidadResponse;
import ar.edu.iessf.servife.catalogo.dto.PerfilDeServicioResponse;
import ar.edu.iessf.servife.catalogo.dto.FiltrosDeBusqueda;
import ar.edu.iessf.servife.catalogo.dto.PrestadorDetalleResponse;
import ar.edu.iessf.servife.catalogo.dto.PrestadorEnListaResponse;
import ar.edu.iessf.servife.catalogo.service.BuscadorDePrestadores;
import ar.edu.iessf.servife.catalogo.service.ServicioDeFichaDePrestador;
import ar.edu.iessf.servife.catalogo.service.ServicioDePerfilDePrestador;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import jakarta.validation.Valid;

/**
 * Módulo B — Catálogo y búsqueda (dueño: Juan Pablo Saravia).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * B5, B6, B7, B8 y GET /prestadores/me/perfil están implementados.
 */
@RestController
@RequestMapping("/prestadores")
public class PrestadorController {

    private final ServicioDePerfilDePrestador perfiles;

    private final BuscadorDePrestadores buscador;

    private final ServicioDeFichaDePrestador ficha;

    private final UsuarioActual usuarioActual;

    public PrestadorController(ServicioDePerfilDePrestador perfiles, BuscadorDePrestadores buscador, ServicioDeFichaDePrestador ficha,
            UsuarioActual usuarioActual) {
        this.perfiles = perfiles;
        this.buscador = buscador;
        this.ficha = ficha;
        this.usuarioActual = usuarioActual;
    }

    /** B5 · GET /prestadores · CU04 · Solo APROBADOS, ACTIVOS y no dados de baja. Filtros: q, tipoServicioId, lat+lng, puntajeMin, dias, orden. Sin filtro de precio (D02). Paginado. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<Pagina<PrestadorEnListaResponse>> buscar(@ModelAttribute FiltrosDeBusqueda filtros) {
        return ResponseEntity.ok(buscador.buscar(filtros));
    }

    /** GET /prestadores/me/perfil · CU03 · Perfil de servicio propio, para precargar la pantalla. */
    @PreAuthorize("hasRole('PRESTADOR')")
    @GetMapping("/me/perfil")
    public ResponseEntity<PerfilDeServicioResponse> obtenerMiPerfil() {
        return ResponseEntity.ok(perfiles.obtenerPerfil(usuarioActual.uuid()));
    }

    /** B6 · GET /prestadores/{uuid} · CU05 · Ficha del prestador: sello verificado, días, valoración y cantidad de servicios realizados (D01, D07). Sin tarifa (D02). 404 si no está aprobado, activo y vigente. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{uuid}")
    public ResponseEntity<PrestadorDetalleResponse> obtener(@PathVariable UUID uuid) {
        return ResponseEntity.ok(ficha.obtener(uuid));
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
