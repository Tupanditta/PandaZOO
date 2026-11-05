// Service worker de PandaZOO: primero la red (así una versión nueva llega al recargar) y, sin conexión, la copia
// guardada en la última visita. No toca la partida: esa vive en IndexedDB, no en la caché.
const CACHE = "pandazoo-shell-v1";

self.addEventListener("install", () => self.skipWaiting());

self.addEventListener("activate", (evento) => {
    evento.waitUntil(
        caches.keys()
            .then((claves) => Promise.all(claves.filter((c) => c !== CACHE).map((c) => caches.delete(c))))
            .then(() => self.clients.claim())
    );
});

self.addEventListener("fetch", (evento) => {
    const peticion = evento.request;
    if (peticion.method !== "GET" || new URL(peticion.url).origin !== self.location.origin) return;
    evento.respondWith(
        fetch(peticion)
            .then((respuesta) => {
                if (respuesta.ok) {
                    const copia = respuesta.clone();
                    caches.open(CACHE).then((c) => c.put(peticion, copia));
                }
                return respuesta;
            })
            .catch(() => caches.match(peticion, { ignoreSearch: true }).then((r) => r || caches.match("./")))
    );
});
