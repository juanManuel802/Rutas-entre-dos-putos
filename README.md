# Transporte: representación, población inicial y fitness

## Decisión para implementar ahora

Usar **matriz de cantidades como base** para estas primeras fases. La propuesta de permutación es válida, está implementada como alternativa y tiene el mismo orden asintótico. En las pruebas incluidas, la matriz tuvo menor tiempo y memoria para los tamaños mayores. Para 10 × 4 la permutación tuvo menor tiempo de creación más primera evaluación: no hay un ganador universal.

Esta decisión cubre exclusivamente datos, codificación, inicialización y fitness. No permite concluir qué representación obtiene mejores soluciones después de cruzar, mutar y reemplazar. Esos operadores no están implementados aquí.

## 1. Contrato matemático implementado

- `n`: número de orígenes; `m`: número de destinos; ambos mayores o iguales a 1.
- Red completa: todas las conexiones origen–destino están disponibles.
- Oferta, demanda y costos: números finitos no negativos. Se admiten enteros y decimales.
- La oferta es un **máximo** por origen. No se exige utilizarla toda cuando sobra.
- Sin faltantes permitidos, cada demanda debe cubrirse exactamente.
- Con faltantes permitidos, se transporta el máximo global posible y solo queda la escasez inevitable.
- El envío total objetivo es `T = min(sum(oferta), sum(demanda))`.
- Costos lineales por unidad transportada; costos opcionales por unidad sobrante en cada origen y faltante en cada destino.

Este contrato hace concreta la propuesta recibida; no pretende resolver cualquier combinación de restricciones. En particular, no modela faltantes voluntarios por conveniencia económica, rutas prohibidas, capacidades por ruta, envíos mínimos, costos fijos de apertura ni costos no lineales. Por ejemplo, con oferta y demanda de 1, transporte de costo 10 y falta permitida de costo 1, un modelo que permita no enviar elegiría faltar; nuestro contrato exige enviar esa unidad.

Si `faltantesPermitidos=False` y la oferta total no alcanza, se informa la incompatibilidad antes de crear la población. El algoritmo no oculta el déficit con nodos ficticios.

## 2. Codificación

### Base: matriz

`matriz[i][j]` es la cantidad enviada del origen `i` al destino `j`. Los costos, ofertas y demandas se comparten entre individuos y no se copian dentro del cromosoma.

### Alternativa: permutación del orden de rutas

Cada conexión tiene identificador `ruta = i*m + j`. El cromosoma es una permutación de `0, ..., n*m-1`, sin repetidos. `divmod(ruta, m)` recupera origen y destino. No se necesita ordenar prioridades reales ni guardar cadenas como `A→X` en cada gen.

El constructor recorre ese orden, asigna `min(ofertaRestante[i], demandaRestante[j])` y actualiza los pendientes. El resultado se guarda temporalmente como envíos positivos; la matriz visual solo se materializa si se solicita. Una permutación determina un plan, pero varios cromosomas pueden determinar el mismo plan.

Esta denominación —permutación del orden de rutas— identifica el método con mayor precisión que atribuirle sin más cualquier algoritmo publicado como «priority-based».

## 3. Población inicial

### Matriz

Para cada uno de los `P` individuos:

1. Crear una matriz de ceros y copias de ofertas y demandas pendientes.
2. Crear listas de orígenes y destinos con cantidad positiva pendiente.
3. Elegir al azar un índice de cada lista.
4. Asignar el mínimo de ambos pendientes y restarlo de ambos.
5. Retirar los índices agotados intercambiándolos con el último y usando `pop()`, operación de tiempo constante.
6. Repetir mientras ambas listas tengan elementos.

Cada asignación positiva agota al menos un origen o destino. Hay como máximo `n+m-1` asignaciones positivas. No hay un ciclo que recorra toneladas una por una. Aunque este núcleo usa O(n+m) pasos, crear la matriz densa cuesta Θ(nm).

### Permutación

Para cada individuo, construir `list(range(n*m))` y barajar con `random.Random(semilla).shuffle(...)`. La primera evaluación lo decodifica. Se admiten duplicados entre individuos; no hay búsqueda indefinida de una población de planes únicos.

No se evalúan todos los vecinos. La diapositiva 8 distingue el tamaño de la vecindad del costo de evaluar una solución: son costos diferentes. Construir y evaluar una población inicial no exige enumerar intercambios.

## 4. Fitness concreto

Sea `q_i` el total enviado por el origen `i`, `r_j` el total recibido por el destino `j` y `C` el costo de transporte:

`C = suma(cantidad[i][j] * costo[i][j])`.

El costo de sobrantes es `Cs = suma(max(oferta[i] - q_i, 0) * costoSobrante[i])`.

Cuando se permiten faltantes, `Cf = suma(max(demanda[j] - r_j, 0) * costoFaltante[j])`; en modo estricto `Cf = 0`.

El incumplimiento `I` se calcula de esta forma:

- **Demanda estricta:** suma de excesos de oferta más suma de diferencias absolutas entre lo recibido y lo demandado.
- **Faltantes por escasez permitidos:** suma de excesos de oferta, más excesos de recepción sobre la demanda, más `abs(totalEnviado - T)`.

Las cantidades negativas son datos inválidos y se rechazan; no se reparan. Una tolerancia absoluta configurable, por defecto `1e-9` en las unidades de cantidad, permite considerar cero un incumplimiento agregado menor o igual a ella.

La fórmula es:

`fitness = 1 / (1 + C + Cs + Cf + pesoPenalizacion * I)`.

**En todos los individuos construidos bajo el contrato, I = 0.** Por eso esta fase no necesita inventar un valor de penalización: `pesoPenalizacion=None` es válido cuando I es cero. Si se intenta evaluar una matriz que incumple, debe proporcionarse explícitamente un peso; de lo contrario se lanza un error. No se ha elegido un peso universal para futuros operadores que generen incumplimientos.

Un faltante permitido es una variable del modelo y su costo económico se incluye en `Cf`; no es una violación. El peso de incumplimiento y el costo por faltante tienen funciones distintas.

Si hay escasez y todos los destinos tienen el mismo costo por faltante, el total faltante es constante (`demandaTotal - ofertaTotal`): ese término desplaza el fitness, pero no decide cuál reparto es mejor. Si interesa distinguir destinos, deben existir costos por destino fundamentados en el problema.

## 5. Ejemplo recibido, verificado

Oferta `[20, 30]`, demanda `[25, 15]` y cromosoma `[3, 0, 2, 1]`, equivalente a `[B→Y, A→X, B→X, A→Y]`:

| Origen | X | Y | Sobrante |
| --- | ---: | ---: | ---: |
| A | 20 | 0 | 0 |
| B | 5 | 15 | 10 |

Con los costos ilustrativos `[[2, 7], [3, 1]]`, se obtiene `C = 70` y `fitness = 1/71`. Los costos fueron añadidos únicamente para ejecutar el ejemplo; el enunciado original no los especificaba.

## 6. Complejidad de esta implementación

Se asume aritmética de tamaño acotado y generación aleatoria por operación constante. No se analiza el costo en bits de enteros arbitrariamente grandes.

| Operación | Matriz | Permutación |
| --- | --- | --- |
| Validar/copiar los datos una vez | Θ(nm) | Θ(nm) |
| Crear un cromosoma | Θ(nm) | Θ(nm) |
| Memoria del cromosoma | Θ(nm) | Θ(nm) |
| Evaluar un individuo desde cero | Θ(nm) | Θ(nm), incluyendo decodificación |
| Crear y evaluar P individuos | Θ(Pnm) | Θ(Pnm) |
| Memoria de la población | Θ(Pnm) | Θ(Pnm) |
| Auxiliar durante evaluación, sin contar cromosoma/datos | O(n+m) | O(n+m) |
| Fitness desde costo y pendientes ya calculados | Θ(n+m) | Θ(n+m) |

La matriz generadora usa además O(n+m) listas activas y pendientes. La validación opcional de una permutación usa Θ(nm) tiempo y memoria adicional mediante `bytearray`; se hace en entradas externas y puede omitirse en cromosomas generados internamente. Si se incluye, la cota auxiliar total de la alternativa pasa a O(nm).

El decodificador visita todas las rutas, incluso después de agotar pendientes: su tiempo es Θ(nm), no O(n+m). Los envíos positivos sí están acotados por n+m-1 en este constructor. Ese límite no se aplica a toda matriz factible ni a todo futuro operador.

Si se guarda un fitness ya calculado y el individuo no cambia, recuperarlo cuesta O(1) en ambas representaciones. El benchmark no usa esa caché.

## 7. Mediciones reproducibles

`benchmark.py` compara Python estándar contra Python estándar, sin NumPy. Usa población de 20, una ejecución de calentamiento y la mediana de 5 repeticiones. Los datos son casos balanceados generados con semillas fijas. Los tiempos excluyen preparar los datos, validar entradas externas y mostrar resultados.

Para la evaluación aislada, ambas representaciones corresponden exactamente a los mismos planes y sus resultados se comparan antes de medir. Para creación más primera evaluación, cada representación usa su propio inicializador. No se está comparando la calidad de una búsqueda genética.

| Tamaño | Crear + primer fitness: matriz (ms) | Permutación (ms) | Pico al crear matriz (MiB) | Permutación (MiB) |
| --- | ---: | ---: | ---: | ---: |
| 10 × 4 | 0,477 | 0,381 | 0,024 | 0,011 |
| 50 × 50 | 7,094 | 17,977 | 0,449 | 1,755 |
| 100 × 100 | 24,551 | 72,865 | 1,657 | 7,477 |
| 200 × 200 | 97,162 | 328,469 | 6,358 | 30,365 |

El JSON conserva todos los valores, versión de Python y plataforma. Los tiempos son locales, sensibles al equipo y a la carga. La memoria es el pico de asignaciones Python de `tracemalloc` durante la creación, no la memoria total del proceso. Incluye la representación concreta de listas y enteros de Python: no demuestra que una permutación empaquetada en otro tipo numérico ocupe más que cualquier matriz.

La diferencia tiene una explicación operativa: barajar la permutación requiere trabajo aleatorio sobre nm elementos; el constructor matricial solo toma decisiones aleatorias en O(n+m) asignaciones, además de crear sus celdas. Las dos siguen siendo Θ(nm).

## 8. Ejecutar y reutilizar

```bash
python transporte_base.py
python -m unittest -v test_transporte.py
python benchmark.py --poblacion 20 --repeticiones 5 --salida resultados.json
```

Ejemplo de integración:

```python
from transporte_base import prepararProblema, crearPoblacion, evaluarMatriz

problema = prepararProblema(
    oferta=[20, 30],
    demanda=[25, 15],
    costos=[[2, 7], [3, 1]],
)
poblacion = crearPoblacion(problema, 40, "matriz", semilla=42)
evaluaciones = [evaluarMatriz(problema, individuo) for individuo in poblacion]
```

La implementación usa funciones y datos simples para poder integrarlos después en el modelado POO acordado. No fija una arquitectura de clases.

## 9. Verificación y fundamento

Pasaron 7 pruebas automatizadas: ejemplo recibido, escasez con y sin permiso, ceros/decimales, entradas inválidas, penalización y exigencia de envío máximo, invariantes/equivalencia en 300 casos aleatorios, y reproducibilidad. También se evaluaron las 720 permutaciones de un caso decimal 3 × 2. Esto comprueba los casos ensayados, no demuestra rendimiento ni optimalidad universal.

- **Codificación y Vecindad**, diapositivas numeradas 6, 7, 8, 9 y 23 (páginas PDF 7, 8, 9, 10 y 24): separar representación y solución, escoger movimientos coherentes, distinguir costo de evaluación y vecindad, y usar un constructor indirecto. No prescribe que un vector supere a una matriz ni demuestra la factibilidad de nuestro modelo particular.
- **Michalewicz, Genetic Algorithms + Data Structures = Evolution Programs**, sección 9.1.2, páginas impresas 145–147: inicialización por mínimo residual y permutación del orden de conexiones. Páginas 148 y 154–155: representación matricial y comparación experimental de los métodos.
- La gestión explícita de faltantes/sobrantes, los identificadores `i*m+j`, la implementación, las validaciones y las mediciones son la concreción de este trabajo. El ejemplo del libro es de transporte clásico balanceado; no se atribuye a él una garantía para todas las variantes desequilibradas.

La codificación por orden de rutas no genera todas las matrices factibles. En el caso clásico lineal balanceado, la búsqueda entre planes básicos es pertinente porque existe un óptimo básico; esta observación no garantiza que una población aleatoria o un AG lo encuentre. No debe extenderse sin análisis a otras restricciones u objetivos.
