package ar.edu.iessf.servife.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita @Scheduled (por ahora, la limpieza de imágenes huérfanas). */
@Configuration
@EnableScheduling
public class ProgramacionConfig {
}
