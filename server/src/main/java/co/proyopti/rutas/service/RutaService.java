package co.proyopti.rutas.service;

import co.proyopti.rutas.model.CalcularRutaRequest;
import co.proyopti.rutas.model.GuardarRutaRequest;
import co.proyopti.rutas.model.Ruta;
import co.proyopti.rutas.repository.RutaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RutaService {
    private final ValhallaClient provider;
    private final RutaRepository repository;

    public RutaService(ValhallaClient provider, RutaRepository repository) {
        this.provider = provider;
        this.repository = repository;
    }

    public Ruta calcular(CalcularRutaRequest request) {
        if (request.origen().equals(request.destino())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El origen y el destino deben ser diferentes.");
        }
        Ruta ruta = provider.calcular(request);
        repository.limpiarBorradores(Instant.now().minus(24, ChronoUnit.HOURS));
        repository.insertarBorrador(ruta);
        return ruta;
    }

    @Transactional
    public Ruta guardar(GuardarRutaRequest request) {
        if (!repository.guardar(request.id(), request.nombre().strip())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "El cálculo ya no está disponible. Calcula la ruta nuevamente.");
        }
        return buscar(request.id());
    }

    public List<Ruta> listar() { return repository.listarGuardadas(); }

    public Ruta buscar(UUID id) {
        return repository.buscarGuardada(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la ruta guardada."));
    }
}
