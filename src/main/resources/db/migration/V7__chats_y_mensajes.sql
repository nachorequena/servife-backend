-- V7__chats_y_mensajes.sql · Chat de la solicitud (CU07, D33). Sin consulta previa: cada solicitud tiene un
-- chat, que se crea junto con ella (C1). Solo mensajes de texto entre el cliente y el prestador.
-- Cliente y prestador salen de la solicitud: el chat no los repite.

CREATE TABLE chats (
    id_chat         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    id_solicitud    BIGINT NOT NULL UNIQUE REFERENCES solicitudes_servicio (id_solicitud),
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    eliminado_en    TIMESTAMPTZ
);

-- El emisor es polimórfico (id + rol, sin FK), como el dueño de archivos.
CREATE TABLE mensajes (
    id_mensaje      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    id_chat         BIGINT NOT NULL REFERENCES chats (id_chat),
    rol_emisor      VARCHAR(20) NOT NULL CHECK (rol_emisor IN ('CLIENTE', 'PRESTADOR')),
    id_emisor       BIGINT NOT NULL,
    texto           VARCHAR(1000) NOT NULL CHECK (length(btrim(texto)) > 0),
    leido           BOOLEAN NOT NULL DEFAULT false,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    eliminado_en    TIMESTAMPTZ
);

-- Leer un chat en orden y buscar el último mensaje de cada chat (bandeja).
CREATE INDEX idx_mensajes_chat ON mensajes (id_chat, id_mensaje);

-- Las solicitudes que ya existen también tienen su chat.
INSERT INTO chats (id_solicitud) SELECT id_solicitud FROM solicitudes_servicio;
