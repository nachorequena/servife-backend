package ar.edu.iessf.servife.gestion.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * E14 · CU17 · Lo que el gestor puede editar de cualquier usuario (decisión de la porción 5): nombre,
 * email y teléfono. El teléfono omitido o en blanco se borra; los gestores no tienen teléfono.
 */
public record ActualizarUsuarioAdminRequest(
    @NotBlank(message = "es obligatorio") @Size(max = 120, message = "es demasiado largo") String nombreApellido,
    @NotBlank(message = "es obligatorio") @Email(message = "no es un correo válido") @Size(max = 254, message = "es demasiado largo") String email,
    @Size(max = 30, message = "es demasiado largo") String telefono) {

    public ActualizarUsuarioAdminRequest {
        email = email == null ? null : email.strip();
    }
}
