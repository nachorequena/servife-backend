package ar.edu.iessf.servife.reputacion.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.error.NoImplementadoException;

/**
 * Módulo D — Reputación y trabajos (dueño: Facundo Bustamante).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * Cada método es un stub que responde 501 hasta que se implemente: definí los DTOs en dto/,
 * la lógica en service/ y reemplazá el throw.
 */
@RestController
@RequestMapping("/archivos")
public class ArchivoController {

    /** D7 · POST /archivos · CU03, CU06, CU10 · Sube una imagen y devuelve su uuid. Valida tipo y tamaño (413). Tipos y tamaño sin definir (.ai/07-security.md). */
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<Void> subir() {
        throw new NoImplementadoException("D7");
    }
}
