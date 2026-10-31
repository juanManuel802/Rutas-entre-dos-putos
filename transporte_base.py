"""Transporte: datos, población inicial y fitness. Python 3.10+, sin dependencias.

Red completa, costos lineales no negativos, oferta máxima y envío total máximo.
Con faltantesPermitidos=False se exige atender toda la demanda.
Con True solo se permite el faltante inevitable por escasez global.
No implementa selección, cruce, mutación ni reemplazo.
Ejecutar: python transporte_base.py
"""

import math
import random


def prepararProblema(oferta, demanda, costos, faltantesPermitidos=False,
                     costoFaltante=None, costoSobrante=None, tolerancia=1e-9):
    n, m = len(oferta), len(demanda)
    if not n or not m or len(costos) != n or any(len(f) != m for f in costos):
        raise ValueError("Dimensiones incompatibles o vacías.")
    if not math.isfinite(tolerancia) or tolerancia < 0:
        raise ValueError("La tolerancia debe ser finita y no negativa.")
    costoFaltante = [0] * m if costoFaltante is None else list(costoFaltante)
    costoSobrante = [0] * n if costoSobrante is None else list(costoSobrante)
    if len(costoFaltante) != m or len(costoSobrante) != n:
        raise ValueError("Debe haber un costo por destino/origen.")
    valores = list(oferta) + list(demanda) + costoFaltante + costoSobrante
    valores += [x for fila in costos for x in fila]
    if any(not isinstance(x, (int, float)) or not math.isfinite(x) or x < 0
           for x in valores):
        raise ValueError("Se requieren números finitos no negativos.")
    totalOferta, totalDemanda = sum(oferta), sum(demanda)
    if not math.isfinite(totalOferta + totalDemanda):
        raise ValueError("Los totales exceden el rango numérico.")
    if not faltantesPermitidos and totalOferta + tolerancia < totalDemanda:
        raise ValueError("Oferta insuficiente: no existe plan que cubra la demanda.")
    return dict(oferta=tuple(oferta), demanda=tuple(demanda),
                costos=tuple(tuple(fila) for fila in costos), n=n, m=m,
                faltantesPermitidos=faltantesPermitidos,
                costoFaltante=tuple(costoFaltante), costoSobrante=tuple(costoSobrante),
                envioObjetivo=min(totalOferta, totalDemanda), tolerancia=tolerancia)


def crearPermutacion(problema, rng):
    rutas = list(range(problema["n"] * problema["m"]))
    rng.shuffle(rutas)
    return rutas


def validarPermutacion(problema, cromosoma):
    longitud = problema["n"] * problema["m"]
    vistos = bytearray(longitud)
    if len(cromosoma) != longitud:
        raise ValueError("Longitud incorrecta del cromosoma.")
    for ruta in cromosoma:
        if type(ruta) is not int or not 0 <= ruta < longitud or vistos[ruta]:
            raise ValueError("Se requiere una permutación de 0 a n*m-1.")
        vistos[ruta] = 1


def decodificar(problema, cromosoma, validar=True):
    if validar:
        validarPermutacion(problema, cromosoma)
    ofertaRestante = list(problema["oferta"])
    demandaRestante = list(problema["demanda"])
    envios, costo = [], 0
    m, costos = problema["m"], problema["costos"]
    for ruta in cromosoma:
        i, j = divmod(ruta, m)
        cantidad = min(ofertaRestante[i], demandaRestante[j])
        if cantidad > 0:
            envios.append((i, j, cantidad))
            costo += cantidad * costos[i][j]
            ofertaRestante[i] -= cantidad
            demandaRestante[j] -= cantidad
    return dict(envios=envios, costoTransporte=costo,
                ofertaRestante=ofertaRestante, demandaRestante=demandaRestante)


def convertirMatriz(problema, plan):
    matriz = [[0] * problema["m"] for _ in range(problema["n"])]
    for i, j, cantidad in plan["envios"]:
        matriz[i][j] = cantidad
    return matriz


def crearMatriz(problema, rng):
    n, m = problema["n"], problema["m"]
    matriz = [[0] * m for _ in range(n)]
    ofertaRestante = list(problema["oferta"])
    demandaRestante = list(problema["demanda"])
    origenes = [i for i in range(n) if ofertaRestante[i] > 0]
    destinos = [j for j in range(m) if demandaRestante[j] > 0]
    while origenes and destinos:
        p, q = rng.randrange(len(origenes)), rng.randrange(len(destinos))
        i, j = origenes[p], destinos[q]
        cantidad = min(ofertaRestante[i], demandaRestante[j])
        matriz[i][j] = cantidad
        ofertaRestante[i] -= cantidad
        demandaRestante[j] -= cantidad
        if ofertaRestante[i] == 0:
            origenes[p] = origenes[-1]
            origenes.pop()
        if demandaRestante[j] == 0:
            destinos[q] = destinos[-1]
            destinos.pop()
    return matriz


def calcularFitness(problema, costoTransporte, ofertaRestante, demandaRestante,
                    pesoPenalizacion=None):
    if pesoPenalizacion is not None and (
        not math.isfinite(pesoPenalizacion) or pesoPenalizacion < 0
    ):
        raise ValueError("El peso de penalización debe ser finito y no negativo.")
    sobrantes = [max(x, 0) for x in ofertaRestante]
    faltantes = [max(x, 0) for x in demandaRestante]
    incumplimiento = sum(max(-x, 0) for x in ofertaRestante)
    if problema["faltantesPermitidos"]:
        incumplimiento += sum(max(-x, 0) for x in demandaRestante)
        enviado = sum(problema["oferta"]) - sum(ofertaRestante)
        incumplimiento += abs(problema["envioObjetivo"] - enviado)
    else:
        incumplimiento += sum(abs(x) for x in demandaRestante)
    if incumplimiento <= problema["tolerancia"]:
        incumplimiento = 0
    if incumplimiento and pesoPenalizacion is None:
        raise ValueError("Plan inválido: indicar explícitamente pesoPenalizacion.")
    costoSobrante = sum(x * c for x, c in zip(sobrantes, problema["costoSobrante"]))
    costoFaltante = (sum(x * c for x, c in zip(faltantes, problema["costoFaltante"]))
                     if problema["faltantesPermitidos"] else 0)
    penalizacion = (pesoPenalizacion or 0) * incumplimiento
    costoEvaluado = costoTransporte + costoSobrante + costoFaltante + penalizacion
    if not math.isfinite(costoEvaluado) or costoEvaluado < 0:
        raise ValueError("El costo evaluado excede el rango numérico o es negativo.")
    return dict(fitness=1 / (1 + costoEvaluado), costoEvaluado=costoEvaluado,
                costoTransporte=costoTransporte, costoSobrante=costoSobrante,
                costoFaltante=costoFaltante, incumplimiento=incumplimiento,
                penalizacion=penalizacion, factible=incumplimiento == 0,
                sobrantes=sobrantes, faltantes=faltantes)


def evaluarPermutacion(problema, cromosoma, validar=True, pesoPenalizacion=None):
    plan = decodificar(problema, cromosoma, validar)
    return calcularFitness(problema, plan["costoTransporte"],
                           plan["ofertaRestante"], plan["demandaRestante"],
                           pesoPenalizacion)


def evaluarMatriz(problema, matriz, validar=True, pesoPenalizacion=None):
    n, m = problema["n"], problema["m"]
    if validar and (len(matriz) != n or any(len(fila) != m for fila in matriz)):
        raise ValueError("Dimensiones incorrectas de la matriz.")
    ofertaRestante = list(problema["oferta"])
    demandaRestante = list(problema["demanda"])
    costo = 0
    for i in range(n):
        for j in range(m):
            cantidad = matriz[i][j]
            if validar and (not math.isfinite(cantidad) or cantidad < 0):
                raise ValueError("Cantidades no finitas o negativas.")
            ofertaRestante[i] -= cantidad
            demandaRestante[j] -= cantidad
            costo += cantidad * problema["costos"][i][j]
    return calcularFitness(problema, costo, ofertaRestante, demandaRestante,
                           pesoPenalizacion)


def crearPoblacion(problema, tamano, representacion="matriz", semilla=42):
    if type(tamano) is not int or tamano < 1:
        raise ValueError("El tamaño de población debe ser un entero positivo.")
    if representacion not in ("matriz", "permutacion"):
        raise ValueError("Representación desconocida.")
    rng = random.Random(semilla)
    crear = crearMatriz if representacion == "matriz" else crearPermutacion
    return [crear(problema, rng) for _ in range(tamano)]


if __name__ == "__main__":
    # ── CONFIGURACIÓN DEL PROBLEMA ─────────────────────────────────────
    # Modificar estos valores para aplicar a cualquier caso.

    # Oferta máxima de cada origen (lista de n elementos).
    OFERTA = [20, 30]

    # Demanda de cada destino (lista de m elementos).
    DEMANDA = [25, 15]

    # Costos unitarios de transporte (matriz n × m).
    COSTOS = [
        [2, 7],
        [3, 1],
    ]

    # ¿Permitir faltantes por escasez global?
    FALTANTES_PERMITIDOS = False

    # Costo por unidad faltante en cada destino (None = ceros).
    COSTO_FALTANTE = None

    # Costo por unidad sobrante en cada origen (None = ceros).
    COSTO_SOBRANTE = None

    # Tolerancia absoluta para considerar incumplimiento como cero.
    TOLERANCIA = 1e-9

    # Peso de penalización para planes inválidos (None si no se espera).
    PESO_PENALIZACION = None

    # Cromosoma de ejemplo para decodificar (permutación de 0..n*m-1).
    # Poner None para omitir el ejemplo de decodificación.
    CROMOSOMA_EJEMPLO = [3, 0, 2, 1]

    # Tamaño de la población inicial.
    TAMANO_POBLACION = 20

    # Semilla para reproducibilidad.
    SEMILLA = 42

    # Representaciones a evaluar: subconjunto de {"matriz", "permutacion"}.
    REPRESENTACIONES = ["matriz", "permutacion"]

    # ── FIN DE CONFIGURACIÓN ───────────────────────────────────────────

    problema = prepararProblema(
        OFERTA, DEMANDA, COSTOS,
        faltantesPermitidos=FALTANTES_PERMITIDOS,
        costoFaltante=COSTO_FALTANTE,
        costoSobrante=COSTO_SOBRANTE,
        tolerancia=TOLERANCIA,
    )

    if CROMOSOMA_EJEMPLO is not None:
        plan = decodificar(problema, CROMOSOMA_EJEMPLO)
        print("Plan del ejemplo:", convertirMatriz(problema, plan))
        print("Evaluación:", evaluarPermutacion(
            problema, CROMOSOMA_EJEMPLO, pesoPenalizacion=PESO_PENALIZACION))

    for representacion in REPRESENTACIONES:
        poblacion = crearPoblacion(problema, TAMANO_POBLACION, representacion,
                                   semilla=SEMILLA)
        evaluar = evaluarMatriz if representacion == "matriz" else evaluarPermutacion
        evaluaciones = [evaluar(problema, x, pesoPenalizacion=PESO_PENALIZACION)
                        for x in poblacion]
        mejor = max(evaluaciones, key=lambda x: x["fitness"])
        print(representacion, "mejor costo inicial:", mejor["costoEvaluado"])
