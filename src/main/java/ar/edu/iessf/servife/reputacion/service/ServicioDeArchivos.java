package ar.edu.iessf.servife.reputacion.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.common.archivos.AlmacenDeArchivos;
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
 * Content-Type ni la extensión que declara el cliente). Ver un archivo: el dueño, o el cliente o
 * prestador de una solicitud no eliminada que lo incluye; cualquier otro recibe 404.
 */
@Service
public class ServicioDeArchivos {

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
        almacen.guardar(archivo.getRuta(), new ByteArrayInputStream(datos));
        return new ArchivoResponse(archivo.getUuid(), mime, datos.length);
    }

    @Transactional(readOnly = true)
    public ArchivoLeido leer(UUID archivoUuid, UUID cuentaUuid, Rol rol) {
        Archivo archivo = archivos.findByUuidAndEliminadoEnIsNull(archivoUuid)
            .orElseThrow(ServicioDeArchivos::noEncontrado);
        Cuenta cuenta = cuentas.buscarPorUuid(cuentaUuid, rol).orElseThrow(ServicioDeArchivos::noEncontrado);
        if (!puedeVer(archivo, cuenta, rol)) {
            throw noEncontrado();
        }
        try (InputStream entrada = almacen.leer(archivo.getRuta())) {
            return new ArchivoLeido(archivo.getMime(), entrada.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean puedeVer(Archivo archivo, Cuenta cuenta, Rol rol) {
        if (archivo.getRolPropietario() == rol && archivo.getIdPropietario().equals(cuenta.getId())) {
            return true;
        }
        return archivos.esParticipanteDeSolicitudConArchivo(archivo.getId(), cuenta.getId(), rol.name());
    }

    private static RecursoNoEncontradoException noEncontrado() {
        return new RecursoNoEncontradoException("No existe el archivo.");
    }

    /** Devuelve el mime según la firma, o null si no es JPEG, PNG ni WEBP. */
    static String detectarMime(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "image/png";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
