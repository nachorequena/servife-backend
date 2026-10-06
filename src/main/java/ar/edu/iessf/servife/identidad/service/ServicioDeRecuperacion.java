package ar.edu.iessf.servife.identidad.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.common.correo.EnviadorDeCorreos;
import ar.edu.iessf.servife.common.error.NegocioException;
import ar.edu.iessf.servife.identidad.domain.CodigoRecuperacion;
import ar.edu.iessf.servife.identidad.domain.Cuenta;
import ar.edu.iessf.servife.identidad.dto.ConfirmarRecuperacionRequest;
import ar.edu.iessf.servife.identidad.dto.RecuperarRequest;
import ar.edu.iessf.servife.identidad.repository.CodigoRecuperacionRepository;

/**
 * A8 y A9 · CU02. Código de 6 dígitos (SecureRandom), guardado solo como SHA-256, que vence a los 15
 * minutos y tolera 5 intentos fallidos. Nunca loguea el código ni la contraseña.
 */
@Service
public class ServicioDeRecuperacion {

    static final Duration VIGENCIA = Duration.ofMinutes(15);
    static final String ASUNTO = "Tu código de ServiFe";

    private final SecureRandom azar = new SecureRandom();
    private final Cuentas cuentas;
    private final CodigoRecuperacionRepository codigos;
    private final PasswordEncoder passwordEncoder;
    private final ServicioDeTokens tokens;
    private final EnviadorDeCorreos correos;
    private final Clock reloj;

    public ServicioDeRecuperacion(Cuentas cuentas, CodigoRecuperacionRepository codigos,
        PasswordEncoder passwordEncoder, ServicioDeTokens tokens, EnviadorDeCorreos correos, Clock reloj) {
        this.cuentas = cuentas;
        this.codigos = codigos;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.correos = correos;
        this.reloj = reloj;
    }

    /**
     * A8. Si el email tiene cuenta, invalida los códigos anteriores y envía uno nuevo. Con un email
     * inexistente o dado de baja no guarda ni envía nada: el llamador siempre responde 204.
     */
    @Transactional
    public void solicitar(RecuperarRequest pedido) {
        String email = Cuentas.normalizar(pedido.email());
        if (cuentas.buscarPorEmail(email).isEmpty()) {
            return;
        }
        Instant ahora = reloj.instant();
        codigos.invalidarPendientes(email, ahora);
        String codigo = "%06d".formatted(azar.nextInt(1_000_000));
        codigos.save(new CodigoRecuperacion(email, Hashes.sha256Hex(codigo), ahora, ahora.plus(VIGENCIA)));
        correos.enviar(email, ASUNTO, "Tu código para recuperar la contraseña es " + codigo
            + ". Vence en " + VIGENCIA.toMinutes() + " minutos. Si no lo pediste, ignorá este correo.");
    }

    /**
     * A9. Compara el hash del código recibido con el del último código sin usar. Un código incorrecto
     * suma un intento y se persiste aunque el pedido falle (noRollbackFor). Con éxito cambia la
     * contraseña, marca el código como usado y revoca todos los refresh.
     */
    @Transactional(noRollbackFor = NegocioException.class)
    public void confirmar(ConfirmarRecuperacionRequest pedido) {
        Contrasenias.validarLargo("contraseniaNueva", pedido.contraseniaNueva());
        String email = Cuentas.normalizar(pedido.email());
        Instant ahora = reloj.instant();

        CodigoRecuperacion guardado = codigos.findFirstByEmailAndUsadoEnIsNullOrderByIdDesc(email)
            .filter(c -> c.estaVigente(ahora))
            .orElseThrow(ServicioDeRecuperacion::codigoInvalido);
        if (!guardado.getCodigoHash().equals(Hashes.sha256Hex(pedido.codigo()))) {
            guardado.registrarIntentoFallido();
            codigos.saveAndFlush(guardado);
            throw codigoInvalido();
        }
        Cuenta cuenta = cuentas.buscarPorEmail(email).orElseThrow(ServicioDeRecuperacion::codigoInvalido);

        cuenta.cambiarContrasenia(passwordEncoder.encode(pedido.contraseniaNueva()));
        guardado.marcarUsado(ahora);
        tokens.revocarTodos(cuenta.getUuid());
    }

    private static NegocioException codigoInvalido() {
        return new NegocioException(HttpStatus.BAD_REQUEST, "CODIGO_INVALIDO",
            "El código no es válido o venció. Pedí uno nuevo.");
    }
}
