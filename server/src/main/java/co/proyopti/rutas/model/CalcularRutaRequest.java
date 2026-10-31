package co.proyopti.rutas.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CalcularRutaRequest(@NotNull @Valid Punto origen, @NotNull @Valid Punto destino) {
}
