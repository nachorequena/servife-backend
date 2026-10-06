package ar.edu.iessf.servife.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj inyectable: los servicios que dependen de la hora (vencimiento de tokens) lo reciben, así los tests lo adelantan. */
@Configuration
public class RelojConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
