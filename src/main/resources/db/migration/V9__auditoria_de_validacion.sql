-- V9__auditoria_de_validacion.sql · Porción 5: quién resolvió la validación de un prestador (E6), cuándo y con qué
-- motivo, y desde cuándo espera revisión (E5 ordena por ahí; cambiar de rubro en B7 lo vuelve a poner en la cola).
ALTER TABLE prestadores ADD COLUMN validado_por BIGINT REFERENCES gestores (id_gestor);
ALTER TABLE prestadores ADD COLUMN validado_en TIMESTAMPTZ;
ALTER TABLE prestadores ADD COLUMN motivo_validacion VARCHAR(255);
ALTER TABLE prestadores ADD COLUMN postulado_en TIMESTAMPTZ NOT NULL DEFAULT now();
UPDATE prestadores SET postulado_en = creado_en;
