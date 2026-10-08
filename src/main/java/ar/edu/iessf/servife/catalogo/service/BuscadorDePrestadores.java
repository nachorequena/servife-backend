package ar.edu.iessf.servife.catalogo.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.catalogo.dto.FiltrosDeBusqueda;
import ar.edu.iessf.servife.catalogo.dto.PrestadorEnListaResponse;
import ar.edu.iessf.servife.catalogo.repository.BusquedaDePrestadoresRepository;
import ar.edu.iessf.servife.catalogo.repository.BusquedaDePrestadoresRepository.Criterios;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.paginacion.Pagina;

/** B5 · CU04: valida y normaliza los filtros y delega la consulta. */
@Service
public class BuscadorDePrestadores {

    static final int TAMANIO_POR_DEFECTO = 20;
    static final int TAMANIO_MAXIMO = 50;
    private static final int LARGO_MINIMO_Q = 2;

    private final BusquedaDePrestadoresRepository repositorio;

    public BuscadorDePrestadores(BusquedaDePrestadoresRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public Pagina<PrestadorEnListaResponse> buscar(FiltrosDeBusqueda f) {
        String orden = f.orden() == null ? null : f.orden().trim().toLowerCase();
        if (orden != null && !orden.equals("cercania") && !orden.equals("valoracion")) {
            throw new ValidacionException("orden", "Elegí cercania o valoracion.");
        }
        if ((f.lat() == null) != (f.lng() == null)) {
            throw new ValidacionException("lat", "Enviá lat y lng juntos.");
        }
        if (f.lat() != null && !(f.lat() >= -90 && f.lat() <= 90)) {
            throw new ValidacionException("lat", "La latitud debe estar entre -90 y 90.");
        }
        if (f.lng() != null && !(f.lng() >= -180 && f.lng() <= 180)) {
            throw new ValidacionException("lng", "La longitud debe estar entre -180 y 180.");
        }
        if (f.puntajeMin() != null && (f.puntajeMin() < 1 || f.puntajeMin() > 5)) {
            throw new ValidacionException("puntajeMin", "El puntaje mínimo debe estar entre 1 y 5.");
        }
        List<Integer> dias = f.dias() == null ? List.of() : f.dias().stream().distinct().toList();
        if (dias.stream().anyMatch(d -> d == null || d < 1 || d > 7)) {
            throw new ValidacionException("dias", "Cada día debe estar entre 1 (lunes) y 7 (domingo).");
        }
        int size = f.size() == null ? TAMANIO_POR_DEFECTO : f.size();
        if (size < 1 || size > TAMANIO_MAXIMO) {
            throw new ValidacionException("size", "El tamaño de página debe estar entre 1 y " + TAMANIO_MAXIMO + ".");
        }
        int page = f.page() == null ? 0 : f.page();
        if (page < 0) {
            throw new ValidacionException("page", "La página no puede ser negativa.");
        }
        String q = f.q() == null ? null : f.q().trim();
        if (q != null && q.length() < LARGO_MINIMO_Q) {
            q = null;
        }
        // Sin coordenadas no hay distancia: "cercania" cae a valoración.
        boolean porCercania = f.lat() != null && !"valoracion".equals(orden);

        return repositorio.buscar(new Criterios(q, f.tipoServicioId(), f.lat(), f.lng(), f.puntajeMin(), dias,
            porCercania, page, size));
    }
}
