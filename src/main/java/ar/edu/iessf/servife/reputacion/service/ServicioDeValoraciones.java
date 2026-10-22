package ar.edu.iessf.servife.reputacion.service;

import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.catalogo.service.ServicioDeFichaDePrestador;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.paginacion.Paginacion;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.reputacion.dto.ValoracionResponse;
import ar.edu.iessf.servife.reputacion.repository.ValoracionRepository;

/** Valoraciones (CU08): D2 (reseñas del perfil). D1 (valorar) espera la transición FINALIZADA → VALORADA de la porción 2. */
@Service
public class ServicioDeValoraciones {

    private final ValoracionRepository valoraciones;
    private final ServicioDeFichaDePrestador fichas;

    public ServicioDeValoraciones(ValoracionRepository valoraciones, ServicioDeFichaDePrestador fichas) {
        this.valoraciones = valoraciones;
        this.fichas = fichas;
    }

    /** D2. Más recientes primero; solo de prestadores visibles (criterio de B6), si no 404. */
    @Transactional(readOnly = true)
    public Pagina<ValoracionResponse> listarDePrestador(UUID uuidPrestador, Integer page, Integer size) {
        Prestador prestador = fichas.visible(uuidPrestador);
        var pagina = Paginacion.pedir(page, size, Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("uuid")));
        return Pagina.de(valoraciones.delPrestador(prestador, pagina), v -> new ValoracionResponse(v.getUuid(),
            v.getPuntaje(), v.getComentario(), v.getCliente().getNombreApellido(), v.getCreadoEn()));
    }
}
