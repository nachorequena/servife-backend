package ar.edu.iessf.servife.catalogo.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.catalogo.dto.FiltrosDeBusqueda;
import ar.edu.iessf.servife.catalogo.dto.PrestadorEnListaResponse;
import ar.edu.iessf.servife.catalogo.repository.BusquedaDePrestadoresRepository;
import ar.edu.iessf.servife.catalogo.repository.BusquedaDePrestadoresRepository.Criterios;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.paginacion.Paginacion;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.Cuenta;
import ar.edu.iessf.servife.identidad.service.Cuentas;

/** B5 · CU04: valida y normaliza los filtros y delega la consulta. */
@Service
public class BuscadorDePrestadores {

    private static final int LARGO_MINIMO_Q = 2;

    private final BusquedaDePrestadoresRepository repositorio;
    private final Cuentas cuentas;

    public BuscadorDePrestadores(BusquedaDePrestadoresRepository repositorio, Cuentas cuentas) {
        this.repositorio = repositorio;
        this.cuentas = cuentas;
    }

    /** Lo que ve un usuario: un cliente no se encuentra a sí mismo como prestador (mismo email, D43). */
    @Transactional(readOnly = true)
    public Pagina<PrestadorEnListaResponse> buscar(FiltrosDeBusqueda f, UUID uuidUsuario, Rol rol) {
        String emailExcluido = rol == Rol.CLIENTE
            ? cuentas.buscarPorUuid(uuidUsuario, rol).map(Cuenta::getEmail).orElse(null)
            : null;
        return buscar(f, emailExcluido);
    }

    @Transactional(readOnly = true)
    public Pagina<PrestadorEnListaResponse> buscar(FiltrosDeBusqueda f) {
        return buscar(f, null);
    }

    private Pagina<PrestadorEnListaResponse> buscar(FiltrosDeBusqueda f, String emailExcluido) {
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
        Pageable pagina = Paginacion.pedir(f.page(), f.size(), Sort.unsorted());
        String q = f.q() == null ? null : f.q().trim();
        if (q != null && q.length() < LARGO_MINIMO_Q) {
            q = null;
        }
        // Sin coordenadas no hay distancia: "cercania" cae a valoración.
        boolean porCercania = f.lat() != null && !"valoracion".equals(orden);

        return repositorio.buscar(new Criterios(q, f.tipoServicioId(), f.lat(), f.lng(), f.puntajeMin(), dias,
            porCercania, emailExcluido, pagina.getPageNumber(), pagina.getPageSize()));
    }
}
