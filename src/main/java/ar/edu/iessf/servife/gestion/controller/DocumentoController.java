package ar.edu.iessf.servife.gestion.controller;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.gestion.domain.TipoDeDocumento;
import ar.edu.iessf.servife.gestion.dto.DocumentoResponse;
import ar.edu.iessf.servife.gestion.dto.ExpedienteResponse;
import ar.edu.iessf.servife.gestion.service.ServicioDeDocumentos;

/**
 * Módulo E — Expediente de validación del prestador (dueño: Ignacio Requena) · CU14, D01.
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo. Los bytes nunca vuelven
 * al prestador: solo el gestor los ve (E8b, AdminValidacionController).
 */
@PreAuthorize("hasRole('PRESTADOR')")
@RestController
@RequestMapping("/prestadores/me/documentos")
public class DocumentoController {

    private final ServicioDeDocumentos servicio;
    private final UsuarioActual usuario;

    public DocumentoController(ServicioDeDocumentos servicio, UsuarioActual usuario) {
        this.servicio = servicio;
        this.usuario = usuario;
    }

    /**
     * E7 · POST /prestadores/me/documentos · CU14 · Multipart: tipo (DNI_FRENTE, DNI_DORSO, CERTIFICACION),
     * archivo (JPEG, PNG o WEBP, hasta 5 MB) y declaracionJurada=true. Se guardan fuera de la base.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoResponse subir(@RequestParam("tipo") TipoDeDocumento tipo, @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(name = "declaracionJurada", defaultValue = "false") boolean declaracionJurada) {
        byte[] datos;
        try {
            datos = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return servicio.subir(usuario.uuid(), tipo, declaracionJurada, datos);
    }

    /** E7b · GET /prestadores/me/documentos · CU14 · Lo que ya cargó y si aceptó la declaración jurada. Sin bytes. */
    @GetMapping
    public ExpedienteResponse listar() {
        return servicio.expedientePropio(usuario.uuid());
    }
}
