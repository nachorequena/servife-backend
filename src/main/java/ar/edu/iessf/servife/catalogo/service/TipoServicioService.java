package ar.edu.iessf.servife.catalogo.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.catalogo.dto.TipoServicioResponse;
import ar.edu.iessf.servife.catalogo.repository.TipoServicioRepository;

@Service
public class TipoServicioService {

    private final TipoServicioRepository repositorio;

    public TipoServicioService(TipoServicioRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Tipos de servicio no dados de baja, ordenados por nombre. */
    @Transactional(readOnly = true)
    public List<TipoServicioResponse> listarActivos() {
        return repositorio.findByEliminadoEnIsNullOrderByNombre().stream()
            .map(t -> new TipoServicioResponse(t.getUuid(), t.getNombre(), t.getIcono(), t.isRequiereMatricula()))
            .toList();
    }
}
