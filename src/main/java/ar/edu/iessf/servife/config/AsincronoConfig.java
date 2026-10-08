package ar.edu.iessf.servife.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Habilita @Async (por ahora, el envío de correos fuera del hilo del pedido). */
@Configuration
@EnableAsync
public class AsincronoConfig {
}
