package ar.edu.iessf.servife.common.correo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Envía por SMTP (Mailpit en desarrollo, ver docker-compose.yml). No registra el contenido de los correos. */
@Component
public class EnviadorSmtp implements EnviadorDeCorreos {

    private final JavaMailSender mailSender;
    private final String remitente;

    public EnviadorSmtp(JavaMailSender mailSender, @Value("${servife.correo.remitente}") String remitente) {
        this.mailSender = mailSender;
        this.remitente = remitente;
    }

    @Override
    public void enviar(String para, String asunto, String texto) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(para);
        mensaje.setSubject(asunto);
        mensaje.setText(texto);
        mailSender.send(mensaje);
    }
}
