package ar.edu.iessf.servife.catalogo.dto;

import java.util.List;
import java.util.UUID;

/**
 * Parámetros de GET /prestadores (B5). Todos son opcionales; la validación de rangos la hace
 * BuscadorDePrestadores. {@code dias} llega como clave repetida: dias=1&dias=3.
 */
public record FiltrosDeBusqueda(String q, UUID tipoServicioId, Double lat, Double lng, Integer puntajeMin,
        List<Integer> dias, String orden, Integer page, Integer size) {
}
