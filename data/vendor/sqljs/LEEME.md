# sql.js 1.14.1 (vendorizado)

SQLite compilado a WebAssembly que usa la versión web (PWA) de PandaZOO. Archivos copiados sin modificar del paquete
npm `sql.js@1.14.1` (`dist/sql-wasm-browser.js` y `dist/sql-wasm-browser.wasm`), con licencia MIT (`LICENSE`).

- La PWA los copia a su carpeta de salida y los carga con una etiqueta `<script>` (`webApp`).
- Los tests de `:data` en Node los cargan con `data/vendor/sqljs/preparar-tests.mjs`.

Para actualizar: `npm pack sql.js@<versión>` y sustituir los dos archivos.
