package ar.edu.iessf.servife.common.paginacion;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import ar.edu.iessf.servife.common.error.ValidacionException;

/** Valida page y size de las listas paginadas (tamaño por defecto 20, máximo 50) y arma el Pageable. */
public final class Paginacion {

    public static final int TAMANIO_POR_DEFECTO = 20;
    public static final int TAMANIO_MAXIMO = 50;

    private Paginacion() {
    }

    /** page y size nulos toman 0 y 20; fuera de rango da 400 con el campo que falló. */
    public static Pageable pedir(Integer page, Integer size, Sort orden) {
        int tamanio = size == null ? TAMANIO_POR_DEFECTO : size;
        if (tamanio < 1 || tamanio > TAMANIO_MAXIMO) {
            throw new ValidacionException("size", "El tamaño de página debe estar entre 1 y " + TAMANIO_MAXIMO + ".");
        }
        int pagina = page == null ? 0 : page;
        if (pagina < 0) {
            throw new ValidacionException("page", "La página no puede ser negativa.");
        }
        return PageRequest.of(pagina, tamanio, orden);
    }
}
