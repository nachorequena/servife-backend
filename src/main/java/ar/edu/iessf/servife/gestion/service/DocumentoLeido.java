package ar.edu.iessf.servife.gestion.service;

import java.io.InputStream;

/** Bytes de un documento para responder en streaming (E8b); quien lo consume cierra el stream. */
public record DocumentoLeido(String mime, long bytes, InputStream datos) {
}
