package co.proyopti.rutas.controller;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = RutaController.class)
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> known(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("mensaje", ex.getReason()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> invalid(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("mensaje",
                "Revisa los datos: coordenadas válidas, identificador correcto y nombre de 1 a 100 caracteres."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception ex) {
        log.error("Error procesando la solicitud", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("mensaje",
                "No se pudo completar la operación. Intenta nuevamente."));
    }
}
