-- V2__identidad_tokens.sql · Módulo A. Refresh opaco con rotación y códigos de recuperación (A8/A9).
CREATE TABLE refresh_tokens (
    id_refresh_token  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_hash        CHAR(64)    NOT NULL UNIQUE,          -- SHA-256 hex; el token nunca se guarda
    uuid_usuario      UUID        NOT NULL,
    rol_usuario       VARCHAR(20) NOT NULL CHECK (rol_usuario IN ('CLIENTE', 'PRESTADOR', 'GESTOR')),
    expira_en         TIMESTAMPTZ NOT NULL,
    revocado_en       TIMESTAMPTZ,
    creado_en         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_usuario ON refresh_tokens (uuid_usuario);

CREATE TABLE codigos_recuperacion (
    id_codigo     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    codigo_hash   CHAR(64)     NOT NULL,                    -- SHA-256 hex del código de 6 dígitos
    intentos      INTEGER      NOT NULL DEFAULT 0,
    expira_en     TIMESTAMPTZ  NOT NULL,
    usado_en      TIMESTAMPTZ,
    creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_codigos_recuperacion_email ON codigos_recuperacion (email);
