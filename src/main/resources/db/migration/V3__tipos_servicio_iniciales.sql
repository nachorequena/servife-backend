-- V3__tipos_servicio_iniciales.sql · Rubros de CLAUDE.md. Electricidad sin matrícula hasta que se decida (06-roadmap).
INSERT INTO tipos_servicio (nombre, requiere_matricula) VALUES
  ('Plomería', FALSE), ('Electricidad', FALSE), ('Limpieza', FALSE), ('Gas', TRUE),
  ('Albañilería', FALSE), ('Jardinería', FALSE), ('Reparaciones', FALSE);
