-- Sprint 3: motivo (al rechazar o cancelar), quién canceló y precio acordado por chat.
ALTER TABLE solicitudes_servicio ADD COLUMN motivo VARCHAR(255);
ALTER TABLE solicitudes_servicio ADD COLUMN cancelada_por VARCHAR(20) CHECK (cancelada_por IN ('CLIENTE', 'PRESTADOR'));
ALTER TABLE solicitudes_servicio ADD COLUMN precio_acordado BIGINT CHECK (precio_acordado >= 0);  -- centavos (D02)
