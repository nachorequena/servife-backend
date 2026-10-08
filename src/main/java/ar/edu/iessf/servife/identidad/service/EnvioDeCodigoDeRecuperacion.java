package ar.edu.iessf.servife.identidad.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import ar.edu.iessf.servife.common.correo.EnviadorDeCorreos;

/**
 * Envía el código por correo después del commit y fuera del hilo del pedido, así A8 responde igual
 * (204 y mismo tiempo) exista o no la cuenta y una falla de SMTP no se filtra al cliente.
 * Si el envío falla solo registra la clase de la excepción: nunca el código ni el cuerpo del correo.
 */
@Component
public class EnvioDeCodigoDeRecuperacion {

    static final String ASUNTO = "Tu código de ServiFe";

    private static final Logger log = LoggerFactory.getLogger(EnvioDeCodigoDeRecuperacion.class);

    private final EnviadorDeCorreos correos;

    public EnvioDeCodigoDeRecuperacion(EnviadorDeCorreos correos) {
        this.correos = correos;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enviar(CodigoDeRecuperacionEmitido evento) {
        try {
            correos.enviar(evento.email(), ASUNTO, "Tu código para recuperar la contraseña es " + evento.codigo()
                + ". Vence en " + evento.vigenciaEnMinutos() + " minutos. Si no lo pediste, ignorá este correo.");
        } catch (RuntimeException e) {
            log.warn("No se pudo enviar el correo de recuperación ({})", e.getClass().getSimpleName());
        }
    }
}
