/* Vista HTML/JS: toda consulta y persistencia pasa por el web service Java. */
const $ = (id) => document.getElementById(id);
const number = new Intl.NumberFormat("es-CO", { maximumFractionDigits: 3 });
const date = new Intl.DateTimeFormat("es-CO", { dateStyle: "medium", timeStyle: "short" });
const map = L.map("map", { scrollWheelZoom: false }).setView([4.635, -74.085], 12);
L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
  maxZoom: 19, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
}).addTo(map);

let selecting = "origin";
let busy = false;
let currentRoute = null;
let routeLayer = null;
const markers = { origin: null, destination: null };
const points = { origin: null, destination: null };
let savedRoutes = [];

function status(message, error = false) {
  $("status").textContent = message;
  $("status").classList.toggle("error", error);
}

function selectPoint(which) {
  selecting = which;
  for (const key of ["origin", "destination"]) {
    $(`select-${key}`).setAttribute("aria-pressed", String(key === which));
    $(`${key}-card`).classList.toggle("active", key === which);
  }
  $("map-instruction").textContent = which === "origin" ? "Haz clic para marcar el origen" : "Haz clic para marcar el destino";
}

function updateControls() {
  document.querySelectorAll("button, input").forEach((element) => { element.disabled = busy; });
  $("calculate").disabled = busy || !points.origin || !points.destination;
  $("save").disabled = busy || !currentRoute || Boolean(currentRoute.guardadaEn);
  $("fit").disabled = busy || !currentRoute;
  Object.values(markers).filter(Boolean).forEach((marker) => busy ? marker.dragging.disable() : marker.dragging.enable());
}

function clearResult() {
  currentRoute = null;
  if (routeLayer) { map.removeLayer(routeLayer); routeLayer = null; }
  $("distance").textContent = "—";
  $("duration").textContent = "Calcula la ruta entre los puntos seleccionados";
  $("route-label").textContent = "Tu recorrido aparecerá aquí";
  $("route-state").textContent = "";
  $("save-form").hidden = true;
  renderSaved();
}

function setPoint(which, point, writeInputs = true) {
  points[which] = point;
  if (markers[which]) { map.removeLayer(markers[which]); markers[which] = null; }
  if (point) {
    const letter = which === "origin" ? "A" : "B";
    markers[which] = L.marker([point.lat, point.lon], {
      draggable: true, title: which === "origin" ? "A: origen" : "B: destino",
      icon: L.divIcon({ className: `map-marker ${which}`, html: letter, iconSize: [32, 32], iconAnchor: [16, 16] })
    }).addTo(map);
    markers[which].bindTooltip(`${letter}: ${point.lat.toFixed(6)}, ${point.lon.toFixed(6)}`);
    markers[which].on("dragend", (event) => {
      const position = event.target.getLatLng();
      clearResult();
      setPoint(which, { lat: rounded(position.lat), lon: rounded(position.wrap().lng) });
      status("Punto actualizado. Calcula nuevamente la ruta.");
      updateControls();
    });
  }
  if (writeInputs) {
    $(`${which}-lat`).value = point ? point.lat.toFixed(6) : "";
    $(`${which}-lon`).value = point ? point.lon.toFixed(6) : "";
  }
}

function rounded(value) { return Number(value.toFixed(6)); }

map.on("click", (event) => {
  if (busy || Math.abs(event.latlng.lat) > 90) return;
  clearResult();
  setPoint(selecting, { lat: rounded(event.latlng.lat), lon: rounded(event.latlng.wrap().lng) });
  if (selecting === "origin") selectPoint("destination");
  status(points.origin && points.destination ? "Puntos listos. Pulsa Calcular ruta." : "Selecciona el punto de destino (B).");
  updateControls();
});

for (const which of ["origin", "destination"]) {
  $(`select-${which}`).addEventListener("click", () => selectPoint(which));
  for (const axis of ["lat", "lon"]) {
    $(`${which}-${axis}`).addEventListener("input", () => {
      clearResult();
      const latInput = $(`${which}-lat`);
      const lonInput = $(`${which}-lon`);
      const valid = latInput.value !== "" && lonInput.value !== "" && latInput.validity.valid && lonInput.validity.valid;
      setPoint(which, valid ? { lat: Number(latInput.value), lon: Number(lonInput.value) } : null, false);
      status("Completa ambos puntos y pulsa Calcular ruta.");
      updateControls();
    });
  }
}

async function api(path, method = "GET", body) {
  let response;
  try {
    response = await fetch(`/api/rutas${path}`, {
      method, headers: body ? { "Content-Type": "application/json" } : {},
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(40000)
    });
  } catch (error) {
    throw new Error(error.name === "TimeoutError" ? "La consulta tardó demasiado. Intenta nuevamente." : "No se pudo conectar con el servidor Java.");
  }
  const data = await response.json();
  if (!response.ok) throw new Error(data.mensaje || "No se pudo completar la operación.");
  return data;
}

function fitRoute() {
  if (!routeLayer) return;
  const bounds = routeLayer.getBounds();
  if (points.origin) bounds.extend([points.origin.lat, points.origin.lon]);
  if (points.destination) bounds.extend([points.destination.lat, points.destination.lon]);
  map.fitBounds(bounds, { padding: [45, 45], maxZoom: 16 });
}

function showRoute(route) {
  clearResult();
  currentRoute = route;
  setPoint("origin", route.origen);
  setPoint("destination", route.destino);
  routeLayer = L.geoJSON(route.geometria, { style: { color: "#226bca", weight: 5, opacity: .9 } }).addTo(map);
  fitRoute();
  $("distance").textContent = number.format(route.distanciaMetros / 1000);
  $("duration").textContent = `Tiempo estimado: ${Math.max(1, Math.round(route.duracionSegundos / 60))} min`;
  $("route-label").textContent = route.nombre || "Origen A → Destino B";
  $("route-state").textContent = route.guardadaEn ? `Guardada el ${date.format(new Date(route.guardadaEn))}` : "Ruta calculada · pendiente de guardar";
  $("save-form").hidden = Boolean(route.guardadaEn);
  $("route-name").value = route.nombre || "";
  $("map-instruction").textContent = "A · Origen → B · Destino";
  renderSaved();
}

$("route-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  if (busy || !points.origin || !points.destination) return;
  const request = { origen: points.origin, destino: points.destination };
  busy = true;
  clearResult();
  updateControls();
  $("calculate").textContent = "Calculando…";
  status("Consultando la ruta por carretera para camión…");
  try {
    showRoute(await api("/calcular", "POST", request));
    status("Ruta lista. Puedes darle un nombre y guardarla.");
  } catch (error) { status(error.message, true); }
  finally { busy = false; $("calculate").textContent = "Calcular ruta ↗"; updateControls(); }
});

$("save-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const name = $("route-name").value.trim();
  if (busy || !currentRoute) return;
  if (!name) { status("Escribe un nombre para el recorrido.", true); return; }
  busy = true;
  updateControls();
  status("Guardando el recorrido…");
  try {
    const saved = await api("", "POST", { id: currentRoute.id, nombre: name });
    savedRoutes = [saved, ...savedRoutes.filter((route) => route.id !== saved.id)];
    showRoute(saved);
    status("Ruta guardada. Puedes volver a abrirla desde la lista.");
  } catch (error) { status(error.message, true); }
  finally { busy = false; updateControls(); }
});

function renderSaved() {
  const container = $("saved-routes");
  container.replaceChildren();
  $("saved-count").textContent = savedRoutes.length;
  if (!savedRoutes.length) {
    const empty = document.createElement("p");
    empty.className = "empty";
    empty.textContent = "Aún no hay rutas guardadas. Tu primer recorrido aparecerá aquí.";
    container.append(empty);
  }
  for (const route of savedRoutes) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "saved-route";
    button.classList.toggle("selected", currentRoute?.id === route.id);
    button.setAttribute("aria-label", `Abrir ${route.nombre}`);
    button.disabled = busy;
    for (const [tag, content, className] of [
      ["strong", route.nombre, ""],
      ["span", `${number.format(route.distanciaMetros / 1000)} km · Camión`, "saved-distance"],
      ["span", `A: ${route.origen.lat.toFixed(5)}, ${route.origen.lon.toFixed(5)}`, ""],
      ["span", `B: ${route.destino.lat.toFixed(5)}, ${route.destino.lon.toFixed(5)}`, ""]
    ]) {
      const element = document.createElement(tag);
      element.textContent = content;
      element.className = className;
      button.append(element);
    }
    button.addEventListener("click", async () => {
      if (busy) return;
      busy = true;
      updateControls();
      status("Abriendo la ruta guardada…");
      try {
        showRoute(await api(`/${route.id}`));
        status("Mostrando los puntos, el trazado y la distancia guardados.");
      } catch (error) { status(error.message, true); }
      finally { busy = false; updateControls(); }
    });
    container.append(button);
  }
}

async function loadSaved() {
  if (busy) return;
  busy = true;
  updateControls();
  $("list-status").textContent = "Cargando recorridos…";
  try {
    savedRoutes = await api("");
    renderSaved();
    $("list-status").textContent = "";
  } catch (error) { $("list-status").textContent = error.message; }
  finally { busy = false; updateControls(); }
}

$("reset").addEventListener("click", () => {
  clearResult();
  setPoint("origin", null);
  setPoint("destination", null);
  $("route-name").value = "";
  selectPoint("origin");
  status("Selecciona el punto de origen (A).");
  updateControls();
});
$("fit").addEventListener("click", fitRoute);
$("refresh").addEventListener("click", loadSaved);
selectPoint("origin");
loadSaved();
