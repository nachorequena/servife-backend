package ar.edu.iessf.servife.reputacion.service;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ar.edu.iessf.servife.common.archivos.AlmacenDeArchivos;
import ar.edu.iessf.servife.common.archivos.FirmaDeImagen;
import ar.edu.iessf.servife.common.archivos.ArchivoFaltanteException;
import ar.edu.iessf.servife.common.error.NegocioException;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.Cuenta;
import ar.edu.iessf.servife.identidad.service.Cuentas;
import ar.edu.iessf.servife.reputacion.domain.Archivo;
import ar.edu.iessf.servife.reputacion.dto.ArchivoResponse;
import ar.edu.iessf.servife.reputacion.repository.ArchivoRepository;

/**
 * D7 y lectura protegida de imágenes. El tipo se decide por la firma de los bytes (no por el
 * Content-Type ni la extensión que declara el cliente). Ver un archivo: el dueño; el cliente o
 * prestador de una solicitud no eliminada que lo incluye; o cualquier usuario autenticado si está en
 * una publicación visible (D3). Cualquier otro recibe 404.
 */
@Service
public class ServicioDeArchivos {

    private static final Logger log = LoggerFactory.getLogger(ServicioDeArchivos.class);

    static final long MAXIMO_BYTES = 5L * 1024 * 1024;

    private final ArchivoRepository archivos;
    private final AlmacenDeArchivos almacen;
    private final Cuentas cuentas;

    public ServicioDeArchivos(ArchivoRepository archivos, AlmacenDeArchivos almacen, Cuentas cuentas) {
        this.archivos = archivos;
        this.almacen = almacen;
        this.cuentas = cuentas;
    }

    @Transactional
    public ArchivoResponse subir(UUID cuentaUuid, Rol rol, byte[] datos) {
        if (datos.length == 0) {
            throw new ValidacionException("archivo", "está vacío");
        }
        if (datos.length > MAXIMO_BYTES) {
            throw new NegocioException(HttpStatus.PAYLOAD_TOO_LARGE, "ARCHIVO_DEMASIADO_GRANDE",
                "El archivo supera el tamaño máximo.");
        }
        String mime = detectarMime(datos);
        if (mime == null) {
            throw new ValidacionException("archivo", "tiene que ser una imagen JPEG, PNG o WEBP");
        }
        Cuenta duenio = cuentas.buscarPorUuid(cuentaUuid, rol)
            .orElseThrow(() -> new RecursoNoEncontradoException("No existe la cuenta."));

        // Si escribir en disco falla, la excepción revierte la fila: no queda un registro sin bytes.
        Archivo archivo = archivos.saveAndFlush(new Archivo(mime, datos.length, duenio.getId(), rol));
        String nombre = archivo.getRuta();
        almacen.guardar(nombre, new ByteArrayInputStream(datos));
        // Si la transacción termina en rollback (p. ej. falla el commit), no quedan bytes huérfanos.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int estado) {
                if (estado == STATUS_ROLLED_BACK) {
                    almacen.borrar(nombre);
                }
            }
        });
        return new ArchivoResponse(archivo.getUuid(), mime, datos.length);
    }

    /** Abre el archivo para responderlo en streaming; el stream lo cierra quien lo consume. */
    @Transactional(readOnly = true)
    public ArchivoLeido leer(UUID archivoUuid, UUID cuentaUuid, Rol rol) {
        Archivo archivo = archivos.findByUuidAndEliminadoEnIsNull(archivoUuid)
            .orElseThrow(ServicioDeArchivos::noEncontrado);
        Cuenta cuenta = cuentas.buscarPorUuid(cuentaUuid, rol).orElseThrow(ServicioDeArchivos::noEncontrado);
        if (!puedeVer(archivo, cuenta, rol)) {
            throw noEncontrado();
        }
        try {
            return new ArchivoLeido(archivo.getMime(), archivo.getBytes(), almacen.leer(archivo.getRuta()));
        } catch (ArchivoFaltanteException e) {
            log.warn("El archivo {} existe en la base pero no en el almacén", archivoUuid);
            throw noEncontrado();
        }
    }

    private boolean puedeVer(Archivo archivo, Cuenta cuenta, Rol rol) {
        if (archivo.getRolPropietario() == rol && archivo.getIdPropietario().equals(cuenta.getId())) {
            return true;
        }
        return archivos.esParticipanteDeSolicitudConArchivo(archivo.getId(), cuenta.getId(), rol.name())
            || archivos.estaEnPublicacionVisible(archivo.getId());
    }

    private static RecursoNoEncontradoException noEncontrado() {
        return new RecursoNoEncontradoException("No existe el archivo.");
    }

    /** Devuelve el mime según la firma, o null si no es JPEG, PNG ni WEBP (ver FirmaDeImagen). */
    static String detectarMime(byte[] d) {
        return FirmaDeImagen.mime(d);
    }
}
