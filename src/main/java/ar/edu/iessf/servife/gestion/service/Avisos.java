package ar.edu.iessf.servife.gestion.service;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.gestion.domain.Aviso;
import ar.edu.iessf.servife.gestion.dto.AvisoResponse;
import ar.edu.iessf.servife.gestion.repository.AvisoRepository;
import ar.edu.iessf.servife.identidad.domain.Cuenta;
import ar.edu.iessf.servife.identidad.service.Cuentas;

/**
 * Avisos dentro de la app (E11). Los módulos de catálogo/gestión y solicitudes llaman a
 * {@link #avisar}, que participa de la transacción del llamador: si esta se revierte, el aviso también.
 * Listar, marcar y contar solo ven los avisos del usuario del token.
 */
@Service
public class Avisos {

    static final int LARGO_MAXIMO_TITULO = 120;

    private final AvisoRepository repositorio;
    private final Cuentas cuentas;

    public Avisos(AvisoRepository repositorio, Cuentas cuentas) {
        this.repositorio = repositorio;
        this.cuentas = cuentas;
    }

    /** Crea un aviso no leído para el destinatario, en la transacción actual. El título se recorta a 120. */
    @Transactional
    public void avisar(Cuenta destinatario, TipoDeAviso tipo, String titulo, String cuerpo, UUID uuidSolicitud) {
        String tituloAjustado = titulo.length() > LARGO_MAXIMO_TITULO ? titulo.substring(0, LARGO_MAXIMO_TITULO) : titulo;
        repositorio.save(new Aviso(destinatario.getId(), destinatario.getRol().name(), tipo.name(),
            tituloAjustado, cuerpo, uuidSolicitud));
    }

    /** Avisos del usuario, los más nuevos primero. */
    @Transactional(readOnly = true)
    public Pagina<AvisoResponse> listar(UUID uuid, Rol rol, Pageable pagina) {
        Long idUsuario = idDe(uuid, rol);
        return Pagina.de(repositorio.findByIdUsuarioAndRolUsuarioAndEliminadoEnIsNullOrderByCreadoEnDescIdDesc(
            idUsuario, rol.name(), pagina), Avisos::aRespuesta);
    }

    /** Marca como leído un aviso propio; uno ajeno o inexistente da 404. */
    @Transactional
    public void marcarLeido(UUID uuid, Rol rol, UUID uuidAviso) {
        Long idUsuario = idDe(uuid, rol);
        Aviso aviso = repositorio.findByUuidAndIdUsuarioAndRolUsuarioAndEliminadoEnIsNull(uuidAviso, idUsuario, rol.name())
            .orElseThrow(() -> new RecursoNoEncontradoException("No existe."));
        aviso.marcarLeido();
    }

    @Transactional(readOnly = true)
    public long contarNoLeidos(UUID uuid, Rol rol) {
        return repositorio.countByIdUsuarioAndRolUsuarioAndLeidaFalseAndEliminadoEnIsNull(idDe(uuid, rol), rol.name());
    }

    private Long idDe(UUID uuid, Rol rol) {
        return cuentas.buscarPorUuid(uuid, rol)
            .orElseThrow(() -> new RecursoNoEncontradoException("No existe."))
            .getId();
    }

    private static AvisoResponse aRespuesta(Aviso a) {
        return new AvisoResponse(a.getUuid(), a.getTipo(), a.getTitulo(), a.getCuerpo(), a.getUuidSolicitud(),
            a.isLeida(), a.getCreadoEn());
    }
}
