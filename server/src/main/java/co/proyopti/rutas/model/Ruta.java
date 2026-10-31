package co.proyopti.rutas.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Ruta(UUID id, String nombre, Punto origen, Punto destino,
                   BigDecimal distanciaMetros, double duracionSegundos,
                   Geometria geometria, String proveedor, String perfil,
                   Instant calculadaEn, Instant guardadaEn) {
}
