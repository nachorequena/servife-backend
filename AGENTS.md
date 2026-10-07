# AGENTS.md — servife-backend

Repo de código de Servife: Java 21 · Spring Boot 3 · PostgreSQL 16 · Flyway.

El contexto del proyecto no está acá. Vive en el repo servife-ia, clonado al lado de este:

    ../servife-ia/CLAUDE.md            índice y reglas que no se negocian (leelo primero)
    ../servife-ia/.agents/AGENTS.md    protocolo: ramas, commits, PR pareado, qué leer
    ../servife-ia/.ai/                 01 a 09

Para este repo, lo más habitual: 05-api-contract.md, 04-database-schema.md, 07-security.md y 08-testing.md §1-§3.

Si ../servife-ia no existe, frená y pedí que lo clonen (ver su README.md). No trabajes sin ese
contexto.

Si tu cambio modifica el estado del proyecto o deja viejo algo de ../servife-ia/.ai/, abrí un PR
pareado en servife-ia con el mismo nombre de rama (../servife-ia/.agents/AGENTS.md, punto 6).
Un cambio de contrato que afecta a servife-frontend se escribe primero en 05-api-contract.md.

Comandos (build, tests, stack local): README.md de este repo.

## Piezas compartidas que ya existen

Antes de escribir una de estas, usá la que está. No la copies a tu módulo.

| Necesitás | Usá |
|---|---|
| Implementar un endpoint | El stub que ya está en <modulo>/controller/: reemplazá el throw de NoImplementadoException |
| Devolver un error de negocio | Lanzar RecursoNoEncontradoException (404), ConflictoException (409, con código) o NegocioException. Nunca armar un ResponseEntity de error: lo hace common/error/ManejadorGlobalDeErrores |
| Responder una lista paginada | common/paginacion/Pagina.de(page, mapeo). Nunca devolver un Page de Spring |
| Una entidad nueva | Extender common/auditoria/EntidadBase (uuid, creado_en, actualizado_en, eliminado_en) y declarar la PK con su nombre (id_<tabla>) |
| Saber quién hace la request | common/seguridad/UsuarioActual (uuid y rol, desde el JWT) |
| Emitir un JWT (módulo A) | El JwtEncoder de config/JwtConfig, header HS256, claims sub = uuid y rol |
| Hashear una contraseña | El PasswordEncoder (BCrypt) de config/SeguridadConfig |
| Buscar una cuenta de cualquiera de los tres roles | identidad/service/Cuentas (buscarPorUuid(uuid, rol), buscarPorEmail, emailRegistrado, normalizar) y la interfaz identidad/domain/Cuenta. Nunca consultes los tres repositorios a mano |
| Revocar la sesión de un usuario (ej. al suspender una cuenta, E4) | ServicioDeTokens.revocarTodos(uuid) |
| Mandar un correo | common/correo/EnviadorDeCorreos |
| Rechazar un campo con 400 | common/error/ValidacionException(campo, detalle): devuelve VALIDACION con un ErrorCampo |
| Validar una contraseña | identidad/service/Contrasenias: la regla (REGLA, MENSAJE) y validarLargo (máximo 72 bytes, límite de BCrypt) |
| La hora actual | El bean Clock de config/RelojConfig; no llames a Instant.now() directo, así los tests la fijan |
| Cambiar el esquema | Una migración nueva V<n>__descripcion.sql, con n = la última de main + 1. Nunca editar V1 |

Los tests de integración con Testcontainers se llaman *Test, no *IT: surefire no corre los *IT.
Tests: cada endpoint lleva test de servicio (Mockito) y de controller (@WebMvcTest). Para el
controller, config/SeguridadYErroresTest muestra cómo levantar la seguridad y simular un JWT con rol.
