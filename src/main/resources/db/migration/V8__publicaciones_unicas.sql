-- V8__publicaciones_unicas.sql · Una imagen va en una sola publicación (como V6 para solicitudes) y una
-- solicitud se publica una sola vez mientras la publicación no esté dada de baja. Cubren la carrera de
-- dos D4/D5 simultáneos.
CREATE UNIQUE INDEX uq_publicacion_imagenes_archivo ON publicacion_imagenes (id_archivo);
CREATE UNIQUE INDEX uq_publicaciones_solicitud ON publicaciones (id_solicitud)
    WHERE id_solicitud IS NOT NULL AND eliminado_en IS NULL;
