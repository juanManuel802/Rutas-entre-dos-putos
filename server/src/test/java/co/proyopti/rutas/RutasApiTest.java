package co.proyopti.rutas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** API HTTP real de Spring + H2 + proveedor HTTP local controlado (sin llamadas públicas). */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:rutas-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class RutasApiTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpServer PROVIDER = startProvider();
    static final AtomicInteger CALLS = new AtomicInteger();
    static volatile int providerStatus = 200;
    static volatile String providerResponse;
    static volatile JsonNode providerRequest;
    static final String POINTS = """
            {"origen":{"lat":4.60971,"lon":-74.08175},"destino":{"lat":4.65333,"lon":-74.08365}}
            """;

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("routing.base-url", () -> "http://127.0.0.1:" + PROVIDER.getAddress().getPort());
    }

    @BeforeEach
    void setup() throws IOException {
        jdbc.sql("DELETE FROM rutas").update();
        providerStatus = 200;
        CALLS.set(0);
        try (var fixture = getClass().getResourceAsStream("/valhalla-bogota.json")) {
            providerResponse = new String(fixture.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @AfterAll static void stopProvider() { PROVIDER.stop(0); }

    @Test
    void calculaGuardaYRecuperaElMismoTrazadoSinReconsultarProveedor() throws Exception {
        JsonNode calculated = calculate();
        assertThat(calculated.path("distanciaMetros").decimalValue()).isEqualByComparingTo("7756");
        assertThat(calculated.at("/geometria/type").asText()).isEqualTo("LineString");
        assertThat(calculated.at("/geometria/coordinates/0/0").asDouble()).isEqualTo(-74.081783);
        assertThat(calculated.at("/geometria/coordinates/0/1").asDouble()).isEqualTo(4.610082);
        assertThat(providerRequest.path("costing").asText()).isEqualTo("truck");
        assertThat(providerRequest.has("costing_options")).isFalse();
        assertThat(providerRequest.at("/locations/0/lat").asDouble()).isEqualTo(4.60971);
        assertThat(providerRequest.at("/locations/1/lon").asDouble()).isEqualTo(-74.08365);
        mvc.perform(get("/api/rutas")).andExpect(jsonPath("$.length()").value(0));

        String id = calculated.get("id").asText();
        mvc.perform(get("/api/rutas/" + id)).andExpect(status().isNotFound());
        String save = "{\"id\":\"" + id + "\",\"nombre\":\"  Recolección → Planta  \"}";
        mvc.perform(post("/api/rutas").contentType(APPLICATION_JSON).content(save))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/rutas/" + id))
                .andExpect(jsonPath("$.nombre").value("Recolección → Planta"))
                .andExpect(jsonPath("$.guardadaEn").isNotEmpty());
        JsonNode saved = JSON.readTree(mvc.perform(get("/api/rutas/" + id))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(saved.get("geometria")).isEqualTo(calculated.get("geometria"));
        assertThat(saved.get("origen")).isEqualTo(calculated.get("origen"));
        assertThat(saved.get("destino")).isEqualTo(calculated.get("destino"));
        assertThat(saved.get("distanciaMetros").decimalValue()).isEqualByComparingTo(calculated.get("distanciaMetros").decimalValue());
        mvc.perform(post("/api/rutas").contentType(APPLICATION_JSON).content(save)).andExpect(status().isCreated());
        mvc.perform(get("/api/rutas")).andExpect(jsonPath("$.length()").value(1));
        assertThat(CALLS.get()).isEqualTo(1);
    }

    @Test void rechazaCoordenadasFueraDeRangoYDatosIncompletos() throws Exception {
        for (String invalid : new String[]{POINTS.replace("4.60971", "91"), POINTS.replace("-74.08365", "181"),
                "{}", "{\"origen\":{\"lat\":4,\"lon\":null}}", "{malformed"}) {
            mvc.perform(post("/api/rutas/calcular").contentType(APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").isNotEmpty());
        }
        assertThat(CALLS.get()).isZero();
    }

    @Test void rechazaPuntosIdenticos() throws Exception {
        mvc.perform(post("/api/rutas/calcular").contentType(APPLICATION_JSON)
                .content(POINTS.replace("4.65333", "4.60971").replace("-74.08365", "-74.08175")))
                .andExpect(status().isBadRequest());
        assertThat(CALLS.get()).isZero();
    }

    @Test void noAceptaDistanciaInventadaPorElNavegador() throws Exception {
        String id = calculate().get("id").asText();
        mvc.perform(post("/api/rutas").contentType(APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"nombre\":\"Ruta\",\"distanciaMetros\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/rutas")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test void validaNombreAlGuardar() throws Exception {
        String id = calculate().get("id").asText();
        for (String name : new String[]{"   ", "a".repeat(101)}) {
            mvc.perform(post("/api/rutas").contentType(APPLICATION_JSON)
                    .content(JSON.writeValueAsString(java.util.Map.of("id", id, "nombre", name))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test void informaRutaInexistente() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(get("/api/rutas/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/rutas/no-es-un-uuid")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/rutas").contentType(APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"nombre\":\"Ruta\"}"))
                .andExpect(status().isNotFound());
    }

    @Test void informaCuandoNoHayRuta() throws Exception {
        providerStatus = 400;
        providerResponse = "{\"error_code\":442,\"error\":\"No path\"}";
        mvc.perform(post("/api/rutas/calcular").contentType(APPLICATION_JSON).content(POINTS))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM rutas").query(Integer.class).single()).isZero();
    }

    @Test void informaLimiteDelProveedor() throws Exception {
        providerStatus = 429;
        mvc.perform(post("/api/rutas/calcular").contentType(APPLICATION_JSON).content(POINTS))
                .andExpect(status().isServiceUnavailable());
    }

    @Test void informaFalloDelProveedor() throws Exception {
        providerStatus = 500;
        mvc.perform(post("/api/rutas/calcular").contentType(APPLICATION_JSON).content(POINTS))
                .andExpect(status().isBadGateway());
    }

    @Test void rechazaRespuestaIncompletaDelProveedor() throws Exception {
        providerResponse = "{\"trip\":{\"status\":0}}";
        mvc.perform(post("/api/rutas/calcular").contentType(APPLICATION_JSON).content(POINTS))
                .andExpect(status().isBadGateway());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM rutas").query(Integer.class).single()).isZero();
    }

    @Test void sirveLaWebYElJavascript() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html"));
        mvc.perform(get("/app.js")).andExpect(status().isOk());
        mvc.perform(get("/vendor/leaflet/leaflet.js")).andExpect(status().isOk());
        mvc.perform(get("/favicon.svg")).andExpect(status().isOk());
        mvc.perform(get("/archivo-inexistente.js")).andExpect(status().isNotFound());
    }

    private JsonNode calculate() throws Exception {
        return JSON.readTree(mvc.perform(post("/api/rutas/calcular").contentType(APPLICATION_JSON).content(POINTS))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static HttpServer startProvider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/route", exchange -> {
                CALLS.incrementAndGet();
                providerRequest = JSON.readTree(exchange.getRequestBody());
                byte[] bytes = providerResponse.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(providerStatus, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) { throw new IllegalStateException(ex); }
    }
}
