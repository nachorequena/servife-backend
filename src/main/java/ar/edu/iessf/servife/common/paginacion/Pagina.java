package ar.edu.iessf.servife.common.paginacion;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Respuesta paginada de la API (.ai/05-api-contract.md):
 * { "contenido": [], "pagina": 0, "tamanio": 20, "totalElementos": 0, "totalPaginas": 0 }.
 * Nunca devolver un Page de Spring directo: su JSON no respeta este formato.
 */
public record Pagina<T>(List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas) {

    /** Convierte un Page de entidades en Pagina de DTOs, mapeando cada elemento. */
    public static <E, T> Pagina<T> de(Page<E> page, Function<E, T> mapeo) {
        return new Pagina<>(page.getContent().stream().map(mapeo).toList(),
            page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
