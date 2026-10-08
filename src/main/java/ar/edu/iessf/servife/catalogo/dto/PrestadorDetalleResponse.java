package ar.edu.iessf.servife.catalogo.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Ficha del prestador (B6 · CU05). Nunca expone ids BIGINT ni tarifa (D02). {@code dias}: 1 (lunes) a 7 (domingo). */
public record PrestadorDetalleResponse(UUID uuid, String nombreApellido, TipoServicioResponse tipoServicio,
        String zona, String descripcion, List<Integer> dias, BigDecimal valoracionPromedio,
        long serviciosRealizados, boolean verificado) {
}
