package ar.edu.iessf.servife.gestion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import ar.edu.iessf.servife.common.correo.EnviadorDeCorreos;
import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;

/**
 * Avisa por correo al usuario que el gestor suspendió o reactivó su cuenta (E4, CU12), después del commit
 * y fuera del hilo del pedido, como EnvioDeCodigoDeRecuperacion: si el SMTP falla, la suspensión queda
 * hecha y solo se registra la clase de la excepción.
 */
@Component
public class EnvioDeAvisoDeEstadoDeCuenta {

    static final String ASUNTO_SUSPENDIDA = "Tu cuenta de ServiFe fue suspendida";
    static final String ASUNTO_REACTIVADA = "Tu cuenta de ServiFe está activa de nuevo";

    private static final Logger log = LoggerFactory.getLogger(EnvioDeAvisoDeEstadoDeCuenta.class);

    private final EnviadorDeCorreos correos;

    public EnvioDeAvisoDeEstadoDeCuenta(EnviadorDeCorreos correos) {
        this.correos = correos;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enviar(EstadoDeCuentaCambiado evento) {
        boolean suspendida = evento.estado() == EstadoCuenta.SUSPENDIDA;
        String motivo = evento.motivo() == null ? "" : " Motivo: " + conPunto(evento.motivo());
        String cuerpo = suspendida
            ? "Hola, " + evento.nombreApellido() + ". Suspendimos tu cuenta de ServiFe." + motivo
                + " Mientras siga suspendida no vas a poder ingresar."
            : "Hola, " + evento.nombreApellido() + ". Tu cuenta de ServiFe está activa de nuevo y ya podés ingresar." + motivo;
        try {
            correos.enviar(evento.email(), suspendida ? ASUNTO_SUSPENDIDA : ASUNTO_REACTIVADA, cuerpo);
        } catch (RuntimeException e) {
            log.warn("No se pudo enviar el correo de cambio de estado de cuenta ({})", e.getClass().getSimpleName());
        }
    }

    private static String conPunto(String texto) {
        return texto.matches(".*[.!?]$") ? texto : texto + ".";
    }
}
