package ar.edu.iessf.servife.identidad.service;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.identidad.domain.Gestor;
import ar.edu.iessf.servife.identidad.repository.GestorRepository;

/**
 * Crea el primer gestor al arrancar, desde GESTOR_INICIAL_*, porque un gestor solo lo puede crear
 * otro gestor (D06). Si no hay variables, o ya existe algún gestor, no hace nada. Una configuración
 * inválida falla el arranque; los mensajes nunca incluyen la contraseña.
 */
@Component
public class GestorInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GestorInicial.class);
    private static final String NOMBRE_POR_DEFECTO = "Gestor inicial";
    private static final Pattern REGLA = Pattern.compile(Contrasenias.REGLA);

    private final GestorInicialPropiedades propiedades;
    private final GestorRepository gestores;
    private final Cuentas cuentas;
    private final PasswordEncoder encoder;

    public GestorInicial(GestorInicialPropiedades propiedades, GestorRepository gestores, Cuentas cuentas,
            PasswordEncoder encoder) {
        this.propiedades = propiedades;
        this.gestores = gestores;
        this.cuentas = cuentas;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = propiedades.email();
        String contrasenia = propiedades.contrasenia();
        boolean hayEmail = email != null && !email.isBlank();
        boolean hayContrasenia = contrasenia != null && !contrasenia.isBlank();

        if (!hayEmail && !hayContrasenia) {
            return;
        }
        if (hayEmail != hayContrasenia) {
            throw new IllegalStateException(
                "Gestor inicial mal configurado: GESTOR_INICIAL_EMAIL y GESTOR_INICIAL_CONTRASENIA van juntas");
        }
        if (gestores.count() > 0) {
            return;
        }
        if (!REGLA.matcher(contrasenia).matches() || excedeBytes(contrasenia)) {
            throw new IllegalStateException(
                "GESTOR_INICIAL_CONTRASENIA no es válida: " + Contrasenias.MENSAJE + " (máximo 72 bytes)");
        }
        if (cuentas.emailRegistrado(email)) {
            throw new IllegalStateException("GESTOR_INICIAL_EMAIL (" + Cuentas.normalizar(email)
                + ") ya está registrado como otra cuenta; el email es único entre clientes, prestadores y gestores");
        }

        String nombre = propiedades.nombre() == null || propiedades.nombre().isBlank()
            ? NOMBRE_POR_DEFECTO : propiedades.nombre().trim();
        Gestor gestor = new Gestor(nombre, email, encoder.encode(contrasenia));
        gestores.save(gestor);
        log.info("Gestor inicial creado: {}", gestor.getEmail());
    }

    private static boolean excedeBytes(String contrasenia) {
        try {
            Contrasenias.validarLargo("contrasenia", contrasenia);
            return false;
        } catch (ValidacionException e) {
            return true;
        }
    }
}
