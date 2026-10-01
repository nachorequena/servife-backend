-- V1: tablas sin decisiones abiertas (servife-ia/.ai/04-database-schema.md).
-- Quedan para migraciones de cada dueño, cuando el equipo las cierre (.ai/06-roadmap.md):
--   chats y mensajes (modelo de la consulta previa, D03), declaración jurada de antecedentes (D01),
--   precio y motivo de cancelación de la solicitud (D02, D07).
-- Nunca editar esta migración una vez aplicada: los cambios van en V2, V3...
-- Todas las tablas: PK BIGINT interna, uuid que viaja a la app, creado_en/actualizado_en/eliminado_en.
-- El fecha_hora del diagrama original es creado_en.

-- ─── Usuarios (módulo A) ────────────────────────────────────────────────────
-- El email es único entre las tres tablas: la base no puede garantizarlo, lo valida el servicio.

CREATE TABLE gestores (
    id_gestor        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    nombre_apellido  VARCHAR(120) NOT NULL,
    email            VARCHAR(254) NOT NULL UNIQUE,
    contrasenia      VARCHAR(100) NOT NULL,
    estado_cuenta    VARCHAR(20)  NOT NULL DEFAULT 'ACTIVA' CHECK (estado_cuenta IN ('ACTIVA', 'SUSPENDIDA')),
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
    eliminado_en     TIMESTAMPTZ
);

CREATE TABLE clientes (
    id_cliente       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    nombre_apellido  VARCHAR(120) NOT NULL,
    fec_nacimiento   DATE,
    email            VARCHAR(254) NOT NULL UNIQUE,
    contrasenia      VARCHAR(100) NOT NULL,
    telefono         VARCHAR(30),
    imagen_perfil    VARCHAR(255),
    direccion        VARCHAR(255),
    estado_cuenta    VARCHAR(20)  NOT NULL DEFAULT 'ACTIVA' CHECK (estado_cuenta IN ('ACTIVA', 'SUSPENDIDA')),
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
    eliminado_en     TIMESTAMPTZ
);

-- ─── Catálogo (módulo B) ────────────────────────────────────────────────────

CREATE TABLE tipos_servicio (
    id_tipo_servicio    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    nombre              VARCHAR(80) NOT NULL UNIQUE,
    icono               VARCHAR(80),
    requiere_matricula  BOOLEAN NOT NULL DEFAULT FALSE,              -- D01
    id_gestor           BIGINT REFERENCES gestores (id_gestor),      -- quién lo creó
    creado_en           TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    eliminado_en        TIMESTAMPTZ
);

CREATE TABLE prestadores (
    id_prestador         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                 UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    nombre_apellido      VARCHAR(120) NOT NULL,
    fec_nacimiento       DATE,
    email                VARCHAR(254) NOT NULL UNIQUE,
    contrasenia          VARCHAR(100) NOT NULL,
    telefono             VARCHAR(30),
    imagen_perfil        VARCHAR(255),
    direccion            VARCHAR(255),
    id_tipo_servicio     BIGINT NOT NULL REFERENCES tipos_servicio (id_tipo_servicio),  -- reemplaza oficio
    valoracion_promedio  NUMERIC(2, 1),
    estado_validacion    VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
                         CHECK (estado_validacion IN ('PENDIENTE', 'APROBADO', 'RECHAZADO')),
    zona                 VARCHAR(120),
    lat                  NUMERIC(9, 6),
    lng                  NUMERIC(9, 6),
    radio_km             INTEGER CHECK (radio_km > 0),
    descripcion          TEXT,
    estado_cuenta        VARCHAR(20) NOT NULL DEFAULT 'ACTIVA' CHECK (estado_cuenta IN ('ACTIVA', 'SUSPENDIDA')),
    creado_en            TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    eliminado_en         TIMESTAMPTZ
);

CREATE INDEX idx_prestadores_busqueda ON prestadores (id_tipo_servicio, estado_validacion, estado_cuenta);

-- D09: días de la semana, 1 = lunes … 7 = domingo.
CREATE TABLE disponibilidad_prestador (
    id_disponibilidad  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid               UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    id_prestador       BIGINT NOT NULL REFERENCES prestadores (id_prestador),
    dia_semana         SMALLINT NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),
    creado_en          TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
    eliminado_en       TIMESTAMPTZ,
    UNIQUE (id_prestador, dia_semana)
);

-- ─── Archivos (módulo D, los usan C y E) ────────────────────────────────────

CREATE TABLE archivos (
    id_archivo       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    ruta             VARCHAR(500) NOT NULL,
    mime             VARCHAR(100) NOT NULL,
    bytes            BIGINT NOT NULL CHECK (bytes > 0),
    id_propietario   BIGINT NOT NULL,
    rol_propietario  VARCHAR(20) NOT NULL CHECK (rol_propietario IN ('CLIENTE', 'PRESTADOR', 'GESTOR')),
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
    eliminado_en     TIMESTAMPTZ
);

-- ─── Solicitudes (módulo C) ─────────────────────────────────────────────────
-- Estados según .ai/02-context.md. Las transiciones las valida el servicio, no la base.

CREATE TABLE solicitudes_servicio (
    id_solicitud      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid              UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    id_cliente        BIGINT NOT NULL REFERENCES clientes (id_cliente),
    id_prestador      BIGINT NOT NULL REFERENCES prestadores (id_prestador),
    id_tipo_servicio  BIGINT NOT NULL REFERENCES tipos_servicio (id_tipo_servicio),
    descripcion       TEXT NOT NULL,
    fecha_deseada     DATE,
    hora_preferida    VARCHAR(40),
    direccion         VARCHAR(255),
    estado            VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
                      CHECK (estado IN ('PENDIENTE', 'ACEPTADA', 'RECHAZADA', 'CANCELADA',
                                        'EN_CURSO', 'FINALIZADA', 'VALORADA')),
    creado_en         TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
    eliminado_en      TIMESTAMPTZ
);

CREATE INDEX idx_solicitudes_cliente ON solicitudes_servicio (id_cliente, estado);
CREATE INDEX idx_solicitudes_prestador ON solicitudes_servicio (id_prestador, estado);

CREATE TABLE solicitud_imagenes (
    id_solicitud  BIGINT NOT NULL REFERENCES solicitudes_servicio (id_solicitud),
    id_archivo    BIGINT NOT NULL REFERENCES archivos (id_archivo),
    PRIMARY KEY (id_solicitud, id_archivo)
);

-- ─── Reputación y trabajos (módulo D) ───────────────────────────────────────

CREATE TABLE valoraciones (
    id_valoracion   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    id_solicitud    BIGINT NOT NULL UNIQUE REFERENCES solicitudes_servicio (id_solicitud),  -- una sola vez
    id_cliente      BIGINT NOT NULL REFERENCES clientes (id_cliente),
    puntaje         INTEGER NOT NULL CHECK (puntaje BETWEEN 1 AND 5),
    comentario      TEXT,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    eliminado_en    TIMESTAMPTZ
);

-- "Trabajos realizados". Las imágenes van en publicacion_imagenes.
CREATE TABLE publicaciones (
    id_publicacion  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    id_prestador    BIGINT NOT NULL REFERENCES prestadores (id_prestador),
    id_gestor       BIGINT REFERENCES gestores (id_gestor),
    id_solicitud    BIGINT REFERENCES solicitudes_servicio (id_solicitud),  -- con solicitud muestra estrellas (D07)
    titulo          VARCHAR(120) NOT NULL,
    descripcion     TEXT,
    fecha_trabajo   DATE,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    eliminado_en    TIMESTAMPTZ
);

CREATE TABLE publicacion_imagenes (
    id_publicacion  BIGINT NOT NULL REFERENCES publicaciones (id_publicacion),
    id_archivo      BIGINT NOT NULL REFERENCES archivos (id_archivo),
    PRIMARY KEY (id_publicacion, id_archivo)
);

-- ─── Gestión y notificaciones (módulo E) ────────────────────────────────────

-- La ruta apunta a almacenamiento fuera de la base. Se borran al cerrar el expediente (Ley 25.326).
CREATE TABLE documentos_validacion (
    id_documento    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    id_prestador    BIGINT NOT NULL REFERENCES prestadores (id_prestador),
    tipo            VARCHAR(20) NOT NULL CHECK (tipo IN ('DNI_FRENTE', 'DNI_DORSO', 'CERTIFICACION')),  -- D01
    ruta            VARCHAR(500) NOT NULL,
    estado          VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO')),
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),  -- fecha_carga
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    eliminado_en    TIMESTAMPTZ
);

CREATE TABLE dispositivos (
    id_dispositivo   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    id_usuario       BIGINT NOT NULL,
    rol_usuario      VARCHAR(20) NOT NULL CHECK (rol_usuario IN ('CLIENTE', 'PRESTADOR', 'GESTOR')),
    expo_push_token  VARCHAR(255) NOT NULL UNIQUE,
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
    eliminado_en     TIMESTAMPTZ
);

CREATE TABLE notificaciones (
    id_notificacion  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    id_usuario       BIGINT NOT NULL,
    rol_usuario      VARCHAR(20) NOT NULL CHECK (rol_usuario IN ('CLIENTE', 'PRESTADOR', 'GESTOR')),
    titulo           VARCHAR(120) NOT NULL,
    cuerpo           TEXT,
    leida            BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now(),  -- fecha_hora
    actualizado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
    eliminado_en     TIMESTAMPTZ
);

CREATE INDEX idx_notificaciones_usuario ON notificaciones (id_usuario, rol_usuario, leida);
