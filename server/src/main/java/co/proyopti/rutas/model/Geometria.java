package co.proyopti.rutas.model;

import java.util.List;

/** GeoJSON usa [longitud, latitud], en ese orden. */
public record Geometria(String type, List<List<Double>> coordinates) {
    public Geometria(List<List<Double>> coordinates) {
        this("LineString", coordinates);
    }
}
