// Genera los iconos PNG de la PWA (un panda sobre el verde del tema) sin dependencias externas.
// Uso: node webApp/tools/generar-iconos.mjs
import { deflateSync } from "node:zlib";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const salida = join(dirname(fileURLToPath(import.meta.url)), "..", "src", "jsMain", "resources", "icons");
mkdirSync(salida, { recursive: true });

const VERDE = [0x5e, 0x90, 0x68], BLANCO = [255, 255, 255], NEGRO = [0x1b, 0x1b, 0x1b];

// Formas en coordenadas unitarias (0..1); la última que contiene el punto gana.
const elipse = (cx, cy, rx, ry, color) => (x, y) => ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1 ? color : null;
const formas = [
    elipse(0.27, 0.25, 0.11, 0.11, NEGRO), // orejas
    elipse(0.73, 0.25, 0.11, 0.11, NEGRO),
    elipse(0.5, 0.53, 0.33, 0.30, BLANCO), // cara
    elipse(0.38, 0.49, 0.075, 0.10, NEGRO), // manchas de los ojos
    elipse(0.62, 0.49, 0.075, 0.10, NEGRO),
    elipse(0.385, 0.485, 0.025, 0.03, BLANCO), // ojos
    elipse(0.615, 0.485, 0.025, 0.03, BLANCO),
    elipse(0.5, 0.60, 0.05, 0.035, NEGRO), // nariz
];

function color(x, y) {
    let c = VERDE;
    for (const f of formas) c = f(x, y) ?? c;
    return c;
}

function crc32(buf) {
    let c, crc = 0xffffffff;
    for (const b of buf) {
        c = (crc ^ b) & 0xff;
        for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        crc = (crc >>> 8) ^ c;
    }
    return (crc ^ 0xffffffff) >>> 0;
}

function trozo(tipo, datos) {
    const cuerpo = Buffer.concat([Buffer.from(tipo, "ascii"), datos]);
    const largo = Buffer.alloc(4);
    largo.writeUInt32BE(datos.length);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(cuerpo));
    return Buffer.concat([largo, cuerpo, crc]);
}

function png(tam) {
    const sub = 3; // supermuestreo 3x3 para suavizar los bordes
    const filas = Buffer.alloc(tam * (tam * 3 + 1));
    for (let y = 0; y < tam; y++) {
        const base = y * (tam * 3 + 1);
        filas[base] = 0;
        for (let x = 0; x < tam; x++) {
            let r = 0, g = 0, b = 0;
            for (let j = 0; j < sub; j++) {
                for (let i = 0; i < sub; i++) {
                    const c = color((x + (i + 0.5) / sub) / tam, (y + (j + 0.5) / sub) / tam);
                    r += c[0]; g += c[1]; b += c[2];
                }
            }
            const n = sub * sub;
            filas[base + 1 + x * 3] = Math.round(r / n);
            filas[base + 2 + x * 3] = Math.round(g / n);
            filas[base + 3 + x * 3] = Math.round(b / n);
        }
    }
    const cabecera = Buffer.alloc(13);
    cabecera.writeUInt32BE(tam, 0);
    cabecera.writeUInt32BE(tam, 4);
    cabecera[8] = 8; // profundidad
    cabecera[9] = 2; // RGB
    return Buffer.concat([
        Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
        trozo("IHDR", cabecera),
        trozo("IDAT", deflateSync(filas)),
        trozo("IEND", Buffer.alloc(0)),
    ]);
}

for (const tam of [180, 192, 512]) {
    writeFileSync(join(salida, `icono-${tam}.png`), png(tam));
    console.log(`icono-${tam}.png`);
}
