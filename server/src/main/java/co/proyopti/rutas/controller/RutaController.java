package co.proyopti.rutas.controller;

import co.proyopti.rutas.model.CalcularRutaRequest;
import co.proyopti.rutas.model.GuardarRutaRequest;
import co.proyopti.rutas.model.Ruta;
import co.proyopti.rutas.service.RutaService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Web service REST consumido desde frontend/app.js mediante fetch(). */
@RestController
@RequestMapping("/api/rutas")
public class RutaController {
    private final RutaService service;

    public RutaController(RutaService service) { this.service = service; }

    @PostMapping("/calcular")
    public Ruta calcular(@Valid @RequestBody CalcularRutaRequest request) {
        return service.calcular(request);
    }

    @PostMapping
    public ResponseEntity<Ruta> guardar(@Valid @RequestBody GuardarRutaRequest request) {
        Ruta saved = service.guardar(request);
        return ResponseEntity.created(URI.create("/api/rutas/" + saved.id())).body(saved);
    }

    @GetMapping
    public List<Ruta> listar() { return service.listar(); }

    @GetMapping("/{id}")
    public Ruta buscar(@PathVariable UUID id) { return service.buscar(id); }
}
