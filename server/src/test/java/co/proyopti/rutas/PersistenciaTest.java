package co.proyopti.rutas;

import co.proyopti.rutas.model.Geometria;
import co.proyopti.rutas.model.Punto;
import co.proyopti.rutas.model.Ruta;
import co.proyopti.rutas.repository.RutaRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

class PersistenciaTest {
    @TempDir Path directory;

    @Test void conservaRutaAlCerrarYVolverAIniciarSpringBoot() {
        UUID id = UUID.randomUUID();
        Geometria shape = new Geometria(List.of(List.of(-74.08, 4.61), List.of(-74.09, 4.65)));
        try (var first = start()) {
            var repository = first.getBean(RutaRepository.class);
            repository.insertarBorrador(new Ruta(id, null, new Punto(4.61, -74.08), new Punto(4.65, -74.09),
                    new BigDecimal("7756.123"), 1300, shape, "Valhalla", "truck", Instant.now(), null));
            repository.guardar(id, "Ruta persistida");
        }
        try (var restarted = start()) {
            var routes = restarted.getBean(RutaRepository.class).listarGuardadas();
            assertThat(routes).hasSize(1);
            assertThat(routes.getFirst().id()).isEqualTo(id);
            assertThat(routes.getFirst().nombre()).isEqualTo("Ruta persistida");
            assertThat(routes.getFirst().distanciaMetros()).isEqualByComparingTo("7756.123");
            assertThat(routes.getFirst().geometria()).isEqualTo(shape);
            assertThat(routes.getFirst().origen()).isEqualTo(new Punto(4.61, -74.08));
            assertThat(routes.getFirst().guardadaEn()).isNotNull();
        }
    }

    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(RutasApplication.class).web(WebApplicationType.NONE)
                .run("--spring.datasource.url=jdbc:h2:file:" + directory.resolve("rutas") + ";DB_CLOSE_ON_EXIT=FALSE",
                        "--spring.main.banner-mode=off", "--logging.level.root=WARN");
    }
}
