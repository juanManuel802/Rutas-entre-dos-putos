# Prueba de rutas de transporte

Backend **Java 21 + Spring Boot 3.5**, web service REST y frontend **HTML, CSS y JavaScript**.
Permite seleccionar dos puntos, calcular una ruta por carretera para camión, ver su distancia,
guardarla y volver a abrir el mismo recorrido.

## Ejecutar

Requisito: **JDK 21 o superior compatible con Spring Boot 3.5**. No necesitas instalar Maven
ni un servidor de base de datos. El Maven Wrapper descarga Maven y las dependencias la primera vez.

Desde la raíz del proyecto:

```bash
cd server
./mvnw spring-boot:run
```

En Windows: `mvnw.cmd spring-boot:run` desde `server`.

Abre **http://localhost:8080**. Spring Boot sirve tanto la web como la API en el mismo puerto.
No abras `frontend/index.html` directamente como archivo ni mediante otro servidor.

1. Haz clic en el mapa para marcar A (origen) y después B (destino).
2. Pulsa **Calcular ruta**. Se dibuja el recorrido y aparece la distancia en kilómetros.
3. Escribe un nombre y pulsa **Guardar ruta**.
4. Selecciona el recorrido en **Rutas guardadas** para recuperar sus puntos, trazado y distancia.

También puedes introducir las coordenadas manualmente o arrastrar los marcadores.
Cambiar un punto invalida el cálculo visible y permite calcular un nuevo recorrido.

Ejemplo reproducible en Bogotá:

| Punto | Latitud | Longitud |
| --- | ---: | ---: |
| A | 4.609710 | -74.081750 |
| B | 4.653330 | -74.083650 |

## Organización MVC sencilla

```text
frontend/
  index.html                 Vista
  styles.css                 Estilos
  app.js                     Mapa, eventos y fetch al web service
  vendor/leaflet/             Biblioteca de mapas y licencia, servidas localmente
server/
  src/main/java/co/proyopti/rutas/
    controller/              Endpoints REST y respuestas de error
    model/                   Punto, ruta, geometría y solicitudes JSON
    service/                 Lógica de rutas y cliente HTTP de Valhalla
    repository/              Consultas SQL y persistencia
  src/main/resources/
    application.properties   Puerto, base de datos y URL del proveedor
    schema.sql               Tabla de rutas
  src/test/                  Pruebas de API, proveedor y persistencia
  data/                      Base H2 local; se crea al ejecutar, ignorada por Git
```

Flujo: **vista → controlador REST → servicio → API externa / repositorio H2**.
El `pom.xml` empaqueta `../frontend` dentro del JAR. No hay herramientas de compilación de JavaScript.

## Uso de la API externa

`service/ValhallaClient.java` contiene la única llamada a la API externa:

```http
POST https://valhalla1.openstreetmap.de/route
Content-Type: application/json
```

```json
{
  "locations": [
    {"lat": 4.60971, "lon": -74.08175},
    {"lat": 4.65333, "lon": -74.08365}
  ],
  "costing": "truck",
  "units": "kilometers",
  "shape_format": "polyline6",
  "directions_type": "none"
}
```

Solo se piden al usuario **dos puntos**. No se recopilan ni envían peso, dimensiones o tipo de residuo.
Valhalla usa sus parámetros predeterminados del perfil `truck`; por tanto, el resultado es una
estimación con ese perfil, sin personalización para un vehículo concreto. La ruta sugerida no es
necesariamente la de menor distancia: se guarda la distancia del recorrido elegido por el proveedor.

La respuesta trae la longitud en kilómetros, duración en segundos y una línea codificada.
Java convierte la distancia a **metros** y la línea `polyline6` a **GeoJSON LineString**.
GeoJSON usa `[longitud, latitud]`; los campos de los puntos usan nombres explícitos `lat` y `lon`.
Los marcadores conservan las coordenadas seleccionadas. El trazado sigue la red vial accesible
que el proveedor encuentra cerca de esos puntos; no incluye una conexión fuera de carretera.

El servidor público de Valhalla se usa para esta prueba local de bajo volumen y no requiere clave.
Tiene límites de uso y depende de internet. Para el software real se puede configurar otra instancia
de Valhalla mediante `ROUTING_BASE_URL`, sin cambiar la vista ni los endpoints propios.
El mapa base usa teselas de OpenStreetMap. Abrir rutas guardadas no vuelve a consultar Valhalla,
aunque el fondo del mapa necesita internet.

Referencias oficiales:

- [Valhalla: API de rutas y perfil truck](https://valhalla.github.io/valhalla/api/route/api-reference/)
- [Servidor público y política de uso](https://github.com/valhalla/valhalla#demo-server)
- [Decodificación polyline6](https://valhalla.github.io/valhalla/api/decoding/)
- [Política de teselas de OpenStreetMap](https://operations.osmfoundation.org/policies/tiles/)
- [Leaflet](https://leafletjs.com/)

## Web service propio

| Método | URL | Función |
| --- | --- | --- |
| POST | `/api/rutas/calcular` | Consultar Valhalla y devolver una ruta calculada |
| POST | `/api/rutas` | Guardar el cálculo por identificador y nombre |
| GET | `/api/rutas` | Listar rutas guardadas, más recientes primero |
| GET | `/api/rutas/{id}` | Recuperar una ruta guardada completa |

Calcular:

```bash
curl -sS http://localhost:8080/api/rutas/calcular \
  -H 'Content-Type: application/json' \
  -d '{"origen":{"lat":4.60971,"lon":-74.08175},"destino":{"lat":4.65333,"lon":-74.08365}}'
```

La respuesta incluye `id`, `origen`, `destino`, `distanciaMetros`, `duracionSegundos`, `geometria`,
`proveedor`, `perfil`, `calculadaEn` y `guardadaEn` (inicialmente `null`).

Guardar, sustituyendo el identificador por el recibido:

```bash
curl -sS http://localhost:8080/api/rutas \
  -H 'Content-Type: application/json' \
  -d '{"id":"IDENTIFICADOR-DEL-CALCULO","nombre":"Recolección → Planta"}'

curl -sS http://localhost:8080/api/rutas
curl -sS http://localhost:8080/api/rutas/IDENTIFICADOR-DE-LA-RUTA
```

El backend retiene cada cálculo como borrador. **Guardar confirma ese mismo cálculo**:
no acepta distancias o trazados enviados por el navegador ni consulta otra vez al proveedor.
Guardar repetidamente el mismo identificador no duplica la ruta. Los borradores no se listan;
los que superan 24 horas se limpian durante cálculos posteriores. Las rutas guardadas se conservan.

Errores: `400` datos inválidos, `404` ruta inexistente, `422` el proveedor no pudo calcular el
recorrido solicitado, `502` fallo o respuesta inválida del proveedor, `503` conexión no disponible
o límite del proveedor. El cuerpo contiene `{"mensaje":"Descripción en español"}`.

## Persistencia y uso posterior en el modelo

H2 crea `server/data/rutas.mv.db` al ejecutar desde `server`. Los datos sobreviven a reinicios.
La ruta del archivo es relativa al directorio desde el que se inicia Java; para fijarla, configura
`RUTAS_DB_PATH` con una ruta absoluta, sin la extensión `.mv.db`.

Se guardan los dos puntos, el nombre, la geometría, la distancia en metros, la duración,
el perfil/proveedor y las fechas de cálculo y guardado. El frontend convierte metros a kilómetros
solo para mostrar. La distancia almacenada se podrá reutilizar en una matriz de costos según
las unidades y la tarifa que se definan posteriormente. El cálculo de costos aún no forma parte de esta prueba.

Configuración opcional:

```bash
PORT=8081 RUTAS_DB_PATH=/ruta/absoluta/datos/rutas ./mvnw spring-boot:run
```

El servidor escucha en `127.0.0.1` por defecto para esta prueba local sin autenticación.
`SERVER_ADDRESS` permite cambiar la interfaz. No habilitarlo como servicio público sin añadir
autenticación y controles de uso adecuados al software definitivo.

## Pruebas

```bash
cd server
./mvnw verify
```

Las pruebas automatizadas usan un servidor HTTP local que reproduce una respuesta real de Valhalla
obtenida con los puntos de Bogotá anteriores. No consumen el servicio público. Comprueban:

- Cálculo, unidades y conversión del trazado con precisión de seis decimales.
- Perfil `truck`, coordenadas y ausencia de parámetros de dimensiones o peso.
- Guardado, listado, consulta y doble guardado sin duplicados ni nuevas consultas al proveedor.
- Rechazo de coordenadas inválidas, puntos idénticos, nombres inválidos y distancia manipulada.
- Errores de ruta inexistente, proveedor saturado, proveedor caído y respuesta incompleta.
- Persistencia en archivo al cerrar y volver a crear la aplicación Spring Boot.
- Entrega de los archivos del frontend.

Verificación realizada el 30 de octubre de 2026: **13 pruebas pasaron, 0 fallos**.
Además, se probó la web contra el servicio real: selección de puntos en el mapa, entrada manual,
cálculo de **7,756 km** para el ejemplo de Bogotá, guardado y apertura del recorrido después
de reiniciar el proceso Java. La ruta de ejemplo quedó guardada en la base local como
`Prueba Bogotá · Recolección → Planta`. La distancia puede cambiar si el proveedor actualiza sus datos;
abrir la ruta guardada conserva el cálculo original.

Para empaquetar y ejecutar la aplicación completa:

```bash
./mvnw package
java -jar target/rutas-server-0.0.1-SNAPSHOT.jar
```
