package ar.edu.iessf.servife.reputacion.dto;

import java.util.UUID;

/** D7: el archivo subido. La app lo referencia por uuid y lo lee con GET /archivos/{uuid}. */
public record ArchivoResponse(UUID uuid, String mime, long bytes) {
}
