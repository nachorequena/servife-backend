-- V10__expediente_de_validacion.sql · Porción 5, D01: declaración jurada de antecedentes y lo que falta en
-- documentos_validacion para servir los archivos (mime y tamaño).
ALTER TABLE prestadores ADD COLUMN declaracion_jurada_en TIMESTAMPTZ;
ALTER TABLE documentos_validacion ADD COLUMN mime VARCHAR(30) NOT NULL;
ALTER TABLE documentos_validacion ADD COLUMN bytes BIGINT NOT NULL CHECK (bytes > 0);
-- Un solo DNI frente y un solo DNI dorso vigentes por prestador: dos cargas simultáneas no dejan dos.
CREATE UNIQUE INDEX uq_documentos_dni_vigente ON documentos_validacion (id_prestador, tipo)
    WHERE eliminado_en IS NULL AND tipo IN ('DNI_FRENTE', 'DNI_DORSO');
CREATE INDEX ix_documentos_prestador ON documentos_validacion (id_prestador) WHERE eliminado_en IS NULL;
