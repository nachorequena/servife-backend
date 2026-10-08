package ar.edu.iessf.servife.catalogo.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Tarjeta de prestador en la búsqueda (B5). {@code distanciaKm} es null si el pedido no trae lat/lng. */
public record PrestadorEnListaResponse(UUID uuid, String nombreApellido, TipoServicioResponse tipoServicio,
        String zona, BigDecimal valoracionPromedio, Double distanciaKm, boolean verificado) {
}
