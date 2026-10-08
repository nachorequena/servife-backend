package ar.edu.iessf.servife.reputacion.service;

/** Contenido de un archivo listo para responder: su tipo guardado y los bytes. */
public record ArchivoLeido(String mime, byte[] datos) {
}
