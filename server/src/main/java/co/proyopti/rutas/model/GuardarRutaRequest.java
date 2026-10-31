package co.proyopti.rutas.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record GuardarRutaRequest(@NotNull UUID id, @NotBlank @Size(max = 100) String nombre) {
}
