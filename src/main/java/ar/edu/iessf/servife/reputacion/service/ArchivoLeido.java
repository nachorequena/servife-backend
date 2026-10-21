package ar.edu.iessf.servife.reputacion.service;

import java.io.InputStream;

/**
 * Archivo listo para responder: su tipo guardado, el largo y el stream abierto del almacén. Lo cierra quien
 * escribe la respuesta (el controller lo envuelve en un InputStreamResource), así no se carga entero en memoria.
 */
public record ArchivoLeido(String mime, long bytes, InputStream datos) {
}
