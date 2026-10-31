package co.proyopti.rutas.service;

import co.proyopti.rutas.model.CalcularRutaRequest;
import co.proyopti.rutas.model.Ruta;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ValhallaClient {
    private final RestClient client;

    public ValhallaClient(@Value("${routing.base-url}") String baseUrl,
                          @Value("${routing.timeout-seconds}") int timeoutSeconds) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("User-Agent", "ProyOpti-route-prototype/0.1")
                .defaultHeader("X-Client-Id", "proyopti-local-prototype")
                .build();
    }

    public Ruta calcular(CalcularRutaRequest request) {
        // Esta es toda la información enviada al servicio de rutas.
        // No se solicitan ni se envían dimensiones o peso del camión.
        var payload = Map.of(
                "locations", List.of(request.origen(), request.destino()),
                "costing", "truck",
                "units", "kilometers",
                "shape_format", "polyline6",
                "directions_type", "none");
        try {
            JsonNode response = client.post().uri("/route")
                    .contentType(MediaType.APPLICATION_JSON).body(payload)
                    .retrieve().body(JsonNode.class);
            if (response == null) throw new IllegalArgumentException("Respuesta vacía");
            JsonNode trip = response.path("trip");
            JsonNode summary = trip.path("summary");
            if (trip.path("status").asInt(-1) != 0 || trip.path("legs").size() != 1
                    || !"kilometers".equals(trip.path("units").asText())
                    || !summary.path("length").isNumber() || !summary.path("time").isNumber()) {
                throw new IllegalArgumentException("Respuesta incompleta");
            }
            BigDecimal meters = summary.get("length").decimalValue().multiply(BigDecimal.valueOf(1000));
            double seconds = summary.get("time").asDouble();
            if (meters.signum() < 0 || !Double.isFinite(seconds) || seconds < 0) {
                throw new IllegalArgumentException("Medidas inválidas");
            }
            var geometry = PolylineDecoder.decode(trip.path("legs").get(0).path("shape").asText());
            return new Ruta(UUID.randomUUID(), null, request.origen(), request.destino(),
                    meters, seconds, geometry, "Valhalla", "truck", Instant.now(), null);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 400) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No se encontró una ruta para esos puntos. Selecciona puntos cercanos a una vía.");
            }
            if (status == 429) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "El servicio de rutas está ocupado. Espera un momento y vuelve a intentar.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El servicio de rutas no pudo responder.");
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo conectar con el servicio de rutas. Revisa la conexión e intenta de nuevo.");
        } catch (RestClientException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "El servicio de rutas devolvió una respuesta inválida.");
        }
    }
}
