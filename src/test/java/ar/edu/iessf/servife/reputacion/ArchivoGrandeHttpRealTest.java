package ar.edu.iessf.servife.reputacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.service.ServicioDeTokens;

/** El 413 real: Tomcat con el límite multipart activo y un cliente HTTP de verdad (no MockMvc). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
class ArchivoGrandeHttpRealTest {

    @TempDir
    static Path directorio;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("servife.archivos.directorio", () -> directorio.toString());
    }

    @LocalServerPort int puerto;
    @Autowired ClienteRepository clientes;
    @Autowired ServicioDeTokens tokens;

    @Test
    void sieteMegabytesRespondenElJson413() {
        Cliente ana = clientes.save(new Cliente("Ana", UUID.randomUUID() + "@mail.com", "hash"));
        String token = tokens.emitir(ana.getUuid(), Rol.CLIENTE).accessToken();
        byte[] grande = new byte[7 * 1024 * 1024];
        LinkedMultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("archivo", new ByteArrayResource(grande) {
            @Override
            public String getFilename() {
                return "grande.png";
            }
        });

        var respuesta = RestClient.create("http://localhost:" + puerto + "/api/v1").post().uri("/archivos")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(cuerpo)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> { })
            .toEntity(String.class);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(413);
        assertThat(respuesta.getBody()).contains("ARCHIVO_DEMASIADO_GRANDE");
    }
}
