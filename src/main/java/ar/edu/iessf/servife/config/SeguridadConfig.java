package ar.edu.iessf.servife.config;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import ar.edu.iessf.servife.common.error.EscritorDeErrores;
import ar.edu.iessf.servife.identidad.service.Cuentas;

/**
 * Un solo login para los tres roles; el rol viaja en el token (.ai/07-security.md).
 * Además del filtro de acá, cada endpoint lleva su @PreAuthorize.
 */
@Configuration
@EnableMethodSecurity
public class SeguridadConfig {

    private static final String[] PUBLICOS = {
        "/auth/registro", "/auth/login", "/auth/refresh", "/auth/recuperar",
        "/auth/recuperar/confirmar"
    };

    private static final String[] DOCUMENTACION = {
        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain filtroDeSeguridad(HttpSecurity http, EscritorDeErrores escritorDeErrores,
            ObjectProvider<Cuentas> cuentas) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> { })
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, PUBLICOS).permitAll()
                .requestMatchers(HttpMethod.GET, "/tipos-servicio").permitAll()
                .requestMatchers(DOCUMENTACION).permitAll()
                .requestMatchers("/admin/**").hasRole("GESTOR")
                .anyRequest().authenticated())
            .oauth2ResourceServer(rs -> rs
                .jwt(jwt -> jwt.jwtAuthenticationConverter(convertidorDeRol()))
                .authenticationEntryPoint(escritorDeErrores::noAutenticado)
                .accessDeniedHandler(escritorDeErrores::accesoDenegado))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(escritorDeErrores::noAutenticado)
                .accessDeniedHandler(escritorDeErrores::accesoDenegado))
            // Una cuenta suspendida o dada de baja pierde la sesión en el acto, no cuando vence el token (E4, E15).
            .addFilterAfter(new FiltroDeCuentaActiva(cuentas, escritorDeErrores), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /** Convierte el claim "rol" del token en la autoridad ROLE_<rol> que usa @PreAuthorize. */
    @Bean
    public JwtAuthenticationConverter convertidorDeRol() {
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(jwt -> {
            String rol = jwt.getClaimAsString(JwtConfig.CLAIM_ROL);
            return rol == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + rol));
        });
        return convertidor;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
