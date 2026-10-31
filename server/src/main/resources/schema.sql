CREATE TABLE IF NOT EXISTS rutas (
    id UUID PRIMARY KEY,
    nombre VARCHAR(100),
    origen_lat DOUBLE PRECISION NOT NULL,
    origen_lon DOUBLE PRECISION NOT NULL,
    destino_lat DOUBLE PRECISION NOT NULL,
    destino_lon DOUBLE PRECISION NOT NULL,
    distancia_metros DECIMAL(18, 3) NOT NULL CHECK (distancia_metros >= 0),
    duracion_segundos DOUBLE PRECISION NOT NULL CHECK (duracion_segundos >= 0),
    geometria CLOB NOT NULL,
    proveedor VARCHAR(40) NOT NULL,
    perfil VARCHAR(20) NOT NULL,
    calculada_en TIMESTAMP WITH TIME ZONE NOT NULL,
    guardada_en TIMESTAMP WITH TIME ZONE
);
