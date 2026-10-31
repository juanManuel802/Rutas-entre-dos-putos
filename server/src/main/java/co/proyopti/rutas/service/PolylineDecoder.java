package co.proyopti.rutas.service;

import co.proyopti.rutas.model.Geometria;
import java.util.ArrayList;
import java.util.List;

/** Valhalla entrega polyline6: seis decimales, a diferencia de polyline5. */
final class PolylineDecoder {
    private PolylineDecoder() { }

    static Geometria decode(String encoded) {
        List<List<Double>> points = new ArrayList<>();
        int[] position = {0};
        long lat = 0;
        long lon = 0;
        while (position[0] < encoded.length()) {
            lat += nextDelta(encoded, position);
            lon += nextDelta(encoded, position);
            if (Math.abs(lat) > 90_000_000 || Math.abs(lon) > 180_000_000) {
                throw new IllegalArgumentException("Coordenada inválida del proveedor");
            }
            points.add(List.of(lon / 1_000_000.0, lat / 1_000_000.0));
        }
        if (points.size() < 2) throw new IllegalArgumentException("Trazado incompleto");
        return new Geometria(points);
    }

    private static long nextDelta(String encoded, int[] position) {
        long result = 0;
        int shift = 0;
        int value;
        do {
            if (position[0] >= encoded.length() || shift > 30) {
                throw new IllegalArgumentException("Polyline inválida");
            }
            value = encoded.charAt(position[0]++) - 63;
            if (value < 0 || value > 63) throw new IllegalArgumentException("Polyline inválida");
            result |= (long) (value & 31) << shift;
            shift += 5;
        } while (value >= 32);
        return (result & 1) != 0 ? ~(result >> 1) : result >> 1;
    }
}
