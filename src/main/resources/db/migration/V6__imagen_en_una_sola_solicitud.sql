-- V6__imagen_en_una_sola_solicitud.sql · Una imagen solo puede estar adjunta a una solicitud (evita la carrera de dos C1 con la misma imagen).
CREATE UNIQUE INDEX uq_solicitud_imagenes_archivo ON solicitud_imagenes (id_archivo);
