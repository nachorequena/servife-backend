package ar.edu.iessf.servife.gestion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * E16 · Dashboard del prestador autenticado para un período (fecha de creación de la solicitud, hora de
 * Argentina). porCliente: los 5 clientes con más solicitudes; porServicio: por el tipo de servicio de cada
 * solicitud, así el historial sobrevive a un cambio de rubro. valoracionPromedio: la del perfil (null si no tiene).
 */
public record MetricasDePrestadorResponse(LocalDate desde, LocalDate hasta, long totalSolicitudes,
        List<ConteoPorNombre> porEstado, List<ConteoPorNombre> porCliente, List<ConteoPorServicio> porServicio,
        List<ConteoPorMes> porMes, BigDecimal valoracionPromedio) {
}
