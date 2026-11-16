-- V11: varias direcciones por cliente, para elegir una al pedir un servicio.
-- clientes.direccion se mantiene como copia de la principal (A4 y A6 no cambian).
-- La solicitud sigue guardando la dirección como texto: es la foto del momento, no una FK.

CREATE TABLE direcciones_cliente (
    id_direccion_cliente  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                  UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    id_cliente            BIGINT NOT NULL REFERENCES clientes (id_cliente),
    alias                 VARCHAR(40)  NOT NULL,
    direccion             VARCHAR(255) NOT NULL,
    principal             BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en             TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    eliminado_en          TIMESTAMPTZ
);

CREATE INDEX idx_direcciones_cliente ON direcciones_cliente (id_cliente) WHERE eliminado_en IS NULL;

-- Una sola principal vigente por cliente.
CREATE UNIQUE INDEX uq_direccion_principal ON direcciones_cliente (id_cliente)
    WHERE principal AND eliminado_en IS NULL;

-- La dirección que ya tenía cada cliente pasa a ser su principal.
INSERT INTO direcciones_cliente (id_cliente, alias, direccion, principal)
SELECT id_cliente, 'Casa', direccion, TRUE
FROM clientes
WHERE direccion IS NOT NULL AND btrim(direccion) <> '' AND eliminado_en IS NULL;
