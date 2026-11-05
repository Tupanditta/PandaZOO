# Especificación de diseño de la interfaz

Define la identidad visual de PandaZOO: paleta, tipografía, forma de los componentes y reglas de composición. Es una refactorización de presentación: **no cambia ningún caso de uso, requisito funcional, texto de pantalla ni flujo de navegación**. Respeta RES-01 (solo componentes técnicos: tablas, métricas y formularios; sin 2D/3D ni animaciones espaciales), RES-06 (sin identificadores técnicos), RNF-06 (teléfono, vertical, 4.7"–6.7") y RNF-07 (estados críticos con color, icono y texto).

---

## 1. Concepto

Aplicación de gestión de un zoo con tono **formal y naturalista**: el gestor trabaja con fichas, tablas y registros, como en el archivo de una reserva natural. La estética evoca un cuaderno de campo: papel cálido, verdes de bosque, tierra y agua. Sin mascotas, ilustraciones ni animaciones (RES-01); la identidad se logra solo con color, tipografía y forma.

Principios:

1. **Ficha como unidad visual.** Cada bloque de datos es una ficha: borde fino, esquinas poco redondeadas, cabecera con filete de color.
2. **Sobriedad.** Pocos colores por pantalla; el verde marca la acción principal y el resto es neutro cálido.
3. **Legibilidad sobre decoración.** Contraste mínimo WCAG AA (4.5:1 para texto normal) en claro y oscuro.
4. **Coherencia.** Todos los colores, formas y espaciados salen del tema; ninguna pantalla define colores propios.

---

## 2. Paleta

Valores `sRGB`. Se definen en un único archivo de tema; las pantallas solo usan los roles de `MaterialTheme.colorScheme` y los extras semánticos de la sección 2.3.

### 2.1. Tema claro: "Claro de bosque"

| Rol | Nombre natural | Valor |
|---|---|---|
| `primary` | Verde bosque | `#2F5D3A` |
| `onPrimary` | Papel | `#FFFFFF` |
| `primaryContainer` | Verde hoja | `#CFE5C8` |
| `onPrimaryContainer` | Verde profundo | `#10301A` |
| `secondary` | Corteza | `#7A5C3E` |
| `onSecondary` | Papel | `#FFFFFF` |
| `secondaryContainer` | Arena | `#EBDCC6` |
| `onSecondaryContainer` | Tierra oscura | `#2E200F` |
| `tertiary` | Laguna | `#2F6F7E` |
| `onTertiary` | Papel | `#FFFFFF` |
| `tertiaryContainer` | Agua clara | `#CDE7EB` |
| `onTertiaryContainer` | Laguna profunda | `#0B2E36` |
| `background` | Pergamino | `#F6F2E7` |
| `onBackground` | Tinta | `#1F2A20` |
| `surface` | Papel de ficha | `#FBF8EF` |
| `onSurface` | Tinta | `#1F2A20` |
| `surfaceVariant` | Lino | `#E7E2D0` |
| `onSurfaceVariant` | Tinta suave | `#4A5043` |
| `outline` | Junco | `#8A8468` |
| `outlineVariant` | Paja | `#CFC8AC` |
| `error` | Arcilla | `#A63A2B` |
| `onError` | Papel | `#FFFFFF` |
| `errorContainer` | Arcilla clara | `#F6D8D0` |
| `onErrorContainer` | Arcilla oscura | `#3F0F08` |

### 2.2. Tema oscuro: "Noche de selva"

| Rol | Valor |
|---|---|
| `primary` | `#9CCB93` |
| `onPrimary` | `#0C2A14` |
| `primaryContainer` | `#24472C` |
| `onPrimaryContainer` | `#CFE5C8` |
| `secondary` | `#D9BC96` |
| `onSecondary` | `#3A280F` |
| `secondaryContainer` | `#54402A` |
| `onSecondaryContainer` | `#EBDCC6` |
| `tertiary` | `#8CC7D3` |
| `onTertiary` | `#06242B` |
| `tertiaryContainer` | `#1D515D` |
| `onTertiaryContainer` | `#CDE7EB` |
| `background` | `#111812` |
| `onBackground` | `#E4E6DA` |
| `surface` | `#18211A` |
| `onSurface` | `#E4E6DA` |
| `surfaceVariant` | `#2B342B` |
| `onSurfaceVariant` | `#C2C7B4` |
| `outline` | `#8D9380` |
| `outlineVariant` | `#454D40` |
| `error` | `#F0A090` |
| `onError` | `#4A130B` |
| `errorContainer` | `#7A281C` |
| `onErrorContainer` | `#F6D8D0` |

El tema sigue el modo claro/oscuro del sistema (`isSystemInDarkTheme`). No hay selector propio.

### 2.3. Colores semánticos adicionales

Material 3 no tiene rol de éxito ni de advertencia. Se añaden como extras del tema, con pareja de contenedor, para los avisos (RNF-07, RNF-11):

| Rol | Claro | Oscuro | Uso |
|---|---|---|---|
| `exito` / `exitoContenedor` | `#2F6B3A` / `#D5EBD0` | `#9CD39A` / `#24472C` | Operación confirmada |
| `advertencia` / `advertenciaContenedor` | `#8A5A00` / `#F8E3B5` | `#E6BE6A` / `#4D3A10` | Aviso que requiere atención |
| `info` / `infoContenedor` | `#2F6F7E` / `#CDE7EB` | `#8CC7D3` / `#1D515D` | Notificación informativa no interactiva |

Un estado crítico nunca se comunica solo por color: lleva además icono (símbolo de texto) y texto explícito (RNF-07).

---

## 3. Tipografía

Sin fuentes incrustadas (menor tamaño y mismo resultado en Android e iOS): se usan las familias genéricas del sistema.

| Uso | Familia | Peso | Tamaño |
|---|---|---|---|
| Título de pantalla (barra superior) | Serif | Semibold | 22 sp |
| Título de ficha / sección | Serif | Semibold | 18 sp |
| Cuerpo y datos | Sans (por defecto) | Normal | 15 sp |
| Etiqueta de campo / dato secundario | Sans | Medium | 13 sp |
| Botones | Sans | Semibold | 15 sp |

El serif da el aire de registro formal; el sans mantiene las cifras legibles. Las cifras de métricas usan el mismo cuerpo, alineadas a la derecha.

---

## 4. Forma, espaciado y elevación

| Elemento | Regla |
|---|---|
| Esquinas | Pequeñas: 6 dp (campos, chips), 10 dp (fichas y botones). Nunca píldoras |
| Fichas | Fondo `surface`, borde 1 dp `outlineVariant`, sin sombra; filete de 4 dp en el borde izquierdo con el color de la sección |
| Espaciado | Rejilla de 4 dp; margen lateral de pantalla 16 dp; separación entre fichas 12 dp; relleno interior de ficha 16 dp |
| Elevación | Plana. Solo la barra superior se distingue por color |
| Separadores | Línea de 1 dp `outlineVariant` entre filas de datos; sin sombras ni degradados |

---

## 5. Componentes

### 5.1. Barra superior

Fondo `primary`, texto `onPrimary`, título en serif. Debajo, un filete de 3 dp `secondary` (corteza) que recuerda el borde de una ficha. El botón "Volver" se muestra como botón de texto con contorno `onPrimary`.

### 5.2. Fondo de pantalla

`background` (pergamino / selva nocturna) liso. Sin imágenes.

### 5.3. Ficha (`Ficha`)

Contenedor reutilizable: cabecera opcional con título en serif y filete de color `primary`, cuerpo con filas de datos. Se usa para finanzas, inventario, hábitats y espacios.

### 5.4. Fila de dato (`Fila`)

Etiqueta (`onSurfaceVariant`, 13 sp) a la izquierda; valor (`onSurface`, 15 sp, semibold) a la derecha; separador inferior de 1 dp.

### 5.5. Elemento de lista (hábitat, espacio)

Ficha pulsable con filete izquierdo. El color del filete identifica el tipo de entidad:

| Entidad | Filete |
|---|---|
| Hábitat disponible | `primary` |
| Hábitat no disponible | `outline` (la ficha se muestra atenuada) |
| Espacio activo | `tertiary` |
| Espacio inactivo | `outline` |

El estado siempre se acompaña de su texto; el color solo refuerza.

### 5.6. Botones

| Tipo | Estilo |
|---|---|
| Principal (confirmar, navegar) | Relleno `primary`, texto `onPrimary`, esquina 10 dp, alto mínimo 48 dp |
| Secundario ("Volver") | Contorno `outline`, texto `primary` |
| Deshabilitado | Colores de contenedor atenuados de Material 3 |

### 5.7. Selección (chips)

Chip de selección a todo el ancho. Seleccionado: fondo `primaryContainer`, texto `onPrimaryContainer`, borde `primary`. No seleccionado: fondo `surface`, borde `outlineVariant`.

### 5.8. Campos de texto

Contorno `outline`; enfocado, contorno `primary` de 2 dp; etiqueta flotante; esquina 6 dp. Error: contorno `error`.

### 5.9. Mensajes y avisos

Bloque con fondo de contenedor semántico, filete izquierdo del color pleno, **icono de texto** y mensaje:

| Tipo | Icono | Color |
|---|---|---|
| Éxito | `✔` | `exito` |
| Error o crítico | `!` en triángulo (`⚠`) | `error` |
| Advertencia | `⚠` | `advertencia` |
| Informativo | `i` | `info` |

El texto del mensaje no cambia respecto a la versión anterior; el icono es un elemento adicional, no parte del texto del mensaje.

### 5.10. Carga

Texto "Cargando…" centrado en `onBackground` sobre el fondo de pantalla.

---

## 6. Pantallas actuales

| Pantalla | Composición |
|---|---|
| Inicio (CU-16) | Ficha "Finanzas" (filete `primary`), ficha "Inventario" (filete `secondary`), avisos, dos botones principales a todo el ancho |
| Tienda de recursos (CU-07) | Fila de saldo en ficha, chips de oferta, campo de cantidad, coste total en ficha de resumen, botón principal |
| Hábitats (RF-08) | Lista de fichas con nombre a la izquierda y estado a la derecha |
| Espacios (RF-08) | Botón principal "Comprar espacio" y lista de fichas con nombre en serif y detalle |
| Comprar espacio (CU-03) | Saldo en ficha, campo de denominación, chips de capacidad, botón principal |
| Configurar espacio (CU-02) | Ficha de detalle, nota de capacidad en cursiva, campo, chips de hábitat, botón principal |

Las pantallas de sprints futuros reutilizan estos componentes; no definen estilos propios.

---

## 7. Criterios de aceptación

1. Todos los colores de la interfaz proceden del tema; ninguna pantalla contiene valores de color literales.
2. Los pares texto/fondo de las tablas 2.1, 2.2 y 2.3 cumplen contraste AA.
3. Los textos visibles de cada pantalla son idénticos a los anteriores a la refactorización, y no se muestran identificadores técnicos (RES-06).
4. Los tests de UI del Sprint 1 pasan sin cambios en sus aserciones.
5. En 4.7" y 6.7", en vertical, no hay recortes ni desplazamiento horizontal (RNF-06).
6. Claro y oscuro funcionan según el ajuste del sistema.
7. Ningún componente nuevo introduce imágenes, 2D/3D ni animaciones espaciales (RES-01).

## 8. Alcance de la refactorización

Incluido: archivo de tema (colores, tipografía, formas), componentes visuales compartidos, aplicación a las seis pantallas existentes.
Excluido: nuevas funciones, casos de uso, cambios de navegación, de textos o de datos, e inicio de cualquier sprint posterior.
