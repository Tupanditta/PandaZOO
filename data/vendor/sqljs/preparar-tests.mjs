// Precarga de sql.js para los tests de `:data` en Node. Se ejecuta con `node --import` antes de los tests, y
// como el `await` de nivel superior bloquea el arranque, el motor ya está disponible cuando empieza el primero.
import { createRequire } from "node:module";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const carpeta = dirname(fileURLToPath(import.meta.url));
const initSqlJs = createRequire(import.meta.url)(join(carpeta, "sql-wasm-browser.js"));
globalThis.__pandazooSqlJs = await initSqlJs({ wasmBinary: readFileSync(join(carpeta, "sql-wasm-browser.wasm")) });
