-- V4__avisos_e_iconos.sql · Avisos dentro de la app (tipo y solicitud asociada) e íconos de los rubros iniciales.
ALTER TABLE notificaciones ADD COLUMN tipo VARCHAR(40) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE notificaciones ADD COLUMN uuid_solicitud UUID;
UPDATE tipos_servicio SET icono = CASE nombre
  WHEN 'Plomería' THEN 'water' WHEN 'Electricidad' THEN 'flash' WHEN 'Limpieza' THEN 'sparkles'
  WHEN 'Gas' THEN 'flame' WHEN 'Albañilería' THEN 'hammer' WHEN 'Jardinería' THEN 'leaf'
  WHEN 'Reparaciones' THEN 'construct' END WHERE icono IS NULL;

-- Nombre de rubro único sin distinguir mayúsculas (CU13); complementa el UNIQUE exacto de V1.
CREATE UNIQUE INDEX uq_tipos_servicio_nombre_lower ON tipos_servicio (lower(nombre));
