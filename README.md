# servife-backend

API REST de Servife: Java 21 · Spring Boot 3 · PostgreSQL 16 · Flyway.
El contexto del proyecto (reglas, contrato de API, base de datos, roadmap) está en el repo
servife-ia, clonado al lado de este: ver ../servife-ia/README.md.

## Requisitos

- Docker (Docker Desktop en Windows). Alcanza para levantar el stack y para correr los tests.
- Para trabajar desde el IDE sin Docker: JDK 21. Maven no hace falta, se usa el wrapper (./mvnw).

## Comandos

    # Primera vez: copiar las variables y completar JWT_SECRETO y POSTGRES_PASSWORD
    cp .env.example .env

    # Stack local (api + db). API en http://localhost:8080/api/v1
    docker compose up -d --build
    docker compose down

    # Correo de desarrollo: Mailpit atrapa los correos (código de recuperación de contraseña).
    # Bandeja web en http://localhost:8025 (SMTP interno mailpit:1025, la api ya apunta ahí).

    # Primer gestor: un gestor solo lo crea otro gestor, así que el primero sale del .env.
    # Completar GESTOR_INICIAL_EMAIL y GESTOR_INICIAL_CONTRASENIA (y opcional _NOMBRE); se crea al arrancar
    # solo si no hay ningún gestor. Después se pueden dejar vacías.

    # Tests (con JDK 21 instalado). MigracionesTest usa Testcontainers: necesita Docker corriendo.
    ./mvnw test                          # Windows: mvnw.cmd test

    # Tests sin JDK 21 instalado, dentro de un contenedor
    docker run --rm -v "$PWD":/app -v servife-m2:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock \
      -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /app maven:3.9-eclipse-temurin-21 mvn -B test

    # Correr la API desde el IDE o la terminal, con la db del compose levantada
    ./mvnw spring-boot:run               # necesita JWT_SECRETO en el entorno

Swagger UI: http://localhost:8080/api/v1/swagger-ui.html

## Estructura

    src/main/java/ar/edu/iessf/servife/
      config/        seguridad (JWT, roles), CORS, OpenAPI
      common/        error (formato único), paginacion, auditoria (EntidadBase), seguridad (Rol, UsuarioActual)
      identidad/     Módulo A — Pedro Soria
      catalogo/      Módulo B — Juan Pablo Saravia
      solicitudes/   Módulo C — Tomás Ferreyra
      reputacion/    Módulo D — Facundo Bustamante
      gestion/       Módulo E — Ignacio Requena
    src/main/resources/db/migration/   V1__init.sql, V2__...

Cada módulo tiene controller/, service/, repository/, domain/, dto/ y mapper/. Los controllers ya
tienen todos los endpoints del contrato con su @PreAuthorize, y responden 501 NO_IMPLEMENTADO
hasta que el dueño del módulo los implemente.
