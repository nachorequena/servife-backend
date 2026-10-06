package ar.edu.iessf.servife.common.correo;

/** Envío de correos de texto plano. Los módulos dependen de esta interfaz, no de SMTP. */
public interface EnviadorDeCorreos {

    void enviar(String para, String asunto, String texto);
}
