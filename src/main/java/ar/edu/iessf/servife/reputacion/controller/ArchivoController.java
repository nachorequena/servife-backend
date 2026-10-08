package ar.edu.iessf.servife.reputacion.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.reputacion.dto.ArchivoResponse;
import ar.edu.iessf.servife.reputacion.service.ArchivoLeido;
import ar.edu.iessf.servife.reputacion.service.ServicioDeArchivos;

/**
 * Módulo D — Reputación y trabajos (dueño: Facundo Bustamante).
 * Endpoints según servife-ia/.ai/05-api-contract.md. D7 (subida) y la lectura protegida
 * GET /archivos/{uuid} están implementados.
 */
@RestController
@RequestMapping("/archivos")
public class ArchivoController {

    private final ServicioDeArchivos servicio;
    private final UsuarioActual usuario;

    public ArchivoController(ServicioDeArchivos servicio, UsuarioActual usuario) {
        this.servicio = servicio;
        this.usuario = usuario;
    }

    /** D7 · POST /archivos · CU03, CU06, CU10 · Sube una imagen (JPEG, PNG o WEBP, hasta 5 MB) y devuelve su uuid. */
    @PreAuthorize("isAuthenticated()")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArchivoResponse> subir(@RequestParam("archivo") MultipartFile archivo) {
        byte[] datos;
        try {
            datos = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ArchivoResponse creado = servicio.subir(usuario.uuid(), usuario.rol(), datos);
        return ResponseEntity.created(
            ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}").buildAndExpand(creado.uuid()).toUri())
            .body(creado);
    }

    /** GET /archivos/{uuid} · Bytes de la imagen. Solo el dueño o un participante de una solicitud que la incluye; si no, 404. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{uuid}")
    public ResponseEntity<byte[]> leer(@PathVariable UUID uuid) {
        ArchivoLeido leido = servicio.leer(uuid, usuario.uuid(), usuario.rol());
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(leido.mime()))
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
            .body(leido.datos());
    }
}
