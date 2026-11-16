package ar.edu.iessf.servife.gestion.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * E9b · Gráficos del dashboard del gestor para un período (por fecha de creación de la solicitud, hora de
 * Argentina). Solicitudes dadas de baja no cuentan. topPrestadores: los 5 con más trabajos realizados.
 */
public record MetricasDetalleResponse(LocalDate desde, LocalDate hasta, long totalSolicitudes,
        List<ConteoPorServicio> porServicio, List<ConteoPorNombre> porEstado, List<ConteoPorMes> porMes,
        List<ConteoPorNombre> topPrestadores) {
}
