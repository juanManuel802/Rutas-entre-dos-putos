package co.proyopti.rutas.repository;

import co.proyopti.rutas.model.Geometria;
import co.proyopti.rutas.model.Punto;
import co.proyopti.rutas.model.Ruta;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RutaRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public RutaRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void insertarBorrador(Ruta ruta) {
        jdbc.sql("""
                INSERT INTO rutas (id, origen_lat, origen_lon, destino_lat, destino_lon,
                    distancia_metros, duracion_segundos, geometria, proveedor, perfil, calculada_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(ruta.id(), ruta.origen().lat(), ruta.origen().lon(), ruta.destino().lat(),
                        ruta.destino().lon(), ruta.distanciaMetros(), ruta.duracionSegundos(),
                        serializar(ruta.geometria()), ruta.proveedor(), ruta.perfil(),
                        ruta.calculadaEn().atOffset(ZoneOffset.UTC)).update();
    }

    public boolean guardar(UUID id, String nombre) {
        // Guardar es idempotente: conserva la ruta calculada y evita duplicados por doble clic.
        return jdbc.sql("UPDATE rutas SET nombre = ?, guardada_en = COALESCE(guardada_en, ?) WHERE id = ?")
                .params(nombre, Instant.now().atOffset(ZoneOffset.UTC), id).update() == 1;
    }

    public Optional<Ruta> buscarGuardada(UUID id) {
        return jdbc.sql("SELECT * FROM rutas WHERE id = ? AND guardada_en IS NOT NULL")
                .param(id).query(this::mapear).optional();
    }

    public List<Ruta> listarGuardadas() {
        return jdbc.sql("SELECT * FROM rutas WHERE guardada_en IS NOT NULL ORDER BY guardada_en DESC")
                .query(this::mapear).list();
    }

    public void limpiarBorradores(Instant anterioresA) {
        jdbc.sql("DELETE FROM rutas WHERE guardada_en IS NULL AND calculada_en < ?")
                .param(anterioresA.atOffset(ZoneOffset.UTC)).update();
    }

    private Ruta mapear(ResultSet rs, int row) throws SQLException {
        try {
            OffsetDateTime saved = rs.getObject("guardada_en", OffsetDateTime.class);
            return new Ruta(rs.getObject("id", UUID.class), rs.getString("nombre"),
                    new Punto(rs.getDouble("origen_lat"), rs.getDouble("origen_lon")),
                    new Punto(rs.getDouble("destino_lat"), rs.getDouble("destino_lon")),
                    rs.getBigDecimal("distancia_metros"), rs.getDouble("duracion_segundos"),
                    json.readValue(rs.getString("geometria"), Geometria.class),
                    rs.getString("proveedor"), rs.getString("perfil"),
                    rs.getObject("calculada_en", OffsetDateTime.class).toInstant(),
                    saved == null ? null : saved.toInstant());
        } catch (JsonProcessingException ex) {
            throw new SQLException("No se pudo leer el trazado guardado", ex);
        }
    }

    private String serializar(Geometria geometry) {
        try {
            return json.writeValueAsString(geometry);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo guardar el trazado", ex);
        }
    }
}
