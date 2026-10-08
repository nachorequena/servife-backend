package ar.edu.iessf.servife.identidad.mapper;

import org.springframework.stereotype.Component;

import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.Cuenta;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.dto.UsuarioResponse;

/** Cuenta (cliente, prestador o gestor) a UsuarioResponse; lo reutilizan registro, /auth/me y el perfil. */
@Component
public class UsuarioMapper {

    public UsuarioResponse aRespuesta(Cuenta cuenta) {
        if (cuenta instanceof Cliente c) {
            return new UsuarioResponse(c.getUuid(), c.getRol(), c.getNombreApellido(), c.getEmail(),
                c.getTelefono(), c.getDireccion(), c.getFecNacimiento(), null);
        }
        if (cuenta instanceof Prestador p) {
            return new UsuarioResponse(p.getUuid(), p.getRol(), p.getNombreApellido(), p.getEmail(),
                p.getTelefono(), p.getDireccion(), p.getFecNacimiento(), p.getEstadoValidacion());
        }
        return new UsuarioResponse(cuenta.getUuid(), cuenta.getRol(), cuenta.getNombreApellido(),
            cuenta.getEmail(), null, null, null, null);
    }
}
