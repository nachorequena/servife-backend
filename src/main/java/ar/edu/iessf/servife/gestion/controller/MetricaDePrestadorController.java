package ar.edu.iessf.servife.gestion.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.gestion.dto.MetricasDePrestadorResponse;
import ar.edu.iessf.servife.gestion.service.ServicioDeMetricasDePrestador;

/** Módulo E — Dashboard del prestador. Vive acá y no en PrestadorController para no mezclar catálogo con métricas. */
@RestController
public class MetricaDePrestadorController {

    private final ServicioDeMetricasDePrestador servicio;
    private final UsuarioActual usuarioActual;

    public MetricaDePrestadorController(ServicioDeMetricasDePrestador servicio, UsuarioActual usuarioActual) {
        this.servicio = servicio;
        this.usuarioActual = usuarioActual;
    }

    /** E16 · GET /prestadores/me/metricas · Sus solicitudes por estado, cliente, servicio y mes, y su valoración. Sin fechas: últimos 6 meses. */
    @PreAuthorize("hasRole('PRESTADOR')")
    @GetMapping("/prestadores/me/metricas")
    public MetricasDePrestadorResponse obtener(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.obtener(usuarioActual.uuid(), desde, hasta);
    }
}
