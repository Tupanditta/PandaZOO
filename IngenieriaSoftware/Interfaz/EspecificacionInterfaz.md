# Especificación de diseño de la interfaz

Define la identidad visual de PandaZOO tal como está implementada: paleta, tipografía, forma de los componentes, marco de pantalla (cabecera y barra inferior), ventanas flotantes y reglas de composición. Los textos de usuario y los flujos concretos están en `Otros/DecisionesImplementacion.md` y en los casos de uso. Respeta RES-01 (solo componentes técnicos: tablas, métricas y formularios; sin 2D/3D ni animaciones espaciales), RES-06 (sin identificadores técnicos), RNF-06 (teléfono, vertical, 4.7"–6.7") y RNF-07 (estados críticos con color, icono y texto).

---

## 1. Concepto

Aplicación de gestión de un zoo con tono **formal y naturalista**: el gestor trabaja con fichas, tablas y registros, como en el archivo de una reserva natural. La estética evoca un cuaderno de campo: papel cálido, verdes de bosque, tierra y agua. Sin mascotas, ilustraciones ni animaciones (RES-01); la identidad se logra con color, tipografía, forma e iconos vectoriales sencillos. Los únicos efectos de brillo (contorno degradado, resplandor y reflejos de las fichas de rango épico y exclusivo) son estáticos.

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
| `primary` | Verde salvia | `#5E9068` |
| `onPrimary` | Papel | `#FFFFFF` |
| `primaryContainer` | Verde hoja | `#DDEDD8` |
| `onPrimaryContainer` | Verde profundo | `#1E3D27` |
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
| `primary` | `#A9D0A1` |
| `onPrimary` | `#0C2A14` |
| `primaryContainer` | `#2F5238` |
| `onPrimaryContainer` | `#DDEDD8` |
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
| `exito` / `exitoContenedor` | `#4A7F55` / `#E0F0DC` | `#9CD39A` / `#24472C` | Operación confirmada |
| `advertencia` / `advertenciaContenedor` | `#8A5A00` / `#F8E3B5` | `#E6BE6A` / `#4D3A10` | Aviso que requiere atención |
| `info` / `infoContenedor` | `#2F6F7E` / `#CDE7EB` | `#8CC7D3` / `#1D515D` | Notificación informativa no interactiva |

Un estado crítico nunca se comunica solo por color: lleva además icono (símbolo de texto) y texto explícito (RNF-07).

### 2.4. Tonos de rango

Cada rango de especie tiene un tono (filete y fondo suave de su ficha). **El rango no se escribe: se comunica con el color**; el texto «Común», «Raro», etc. solo aparece donde hace falta, como en la ficha de compra.

| Rango | Filete claro | Fondo claro | Filete oscuro | Fondo oscuro |
|---|---|---|---|---|
| Común | `#8A9A86` | `#EDF0E8` | `#A7B3A2` | `#242C25` |
| Poco común | `#4F9A5E` | `#E0F0DC` | `#7CC48A` | `#1F3A25` |
| Raro | `#3C7FB8` | `#DCEAF6` | `#7FB4E3` | `#1D3547` |
| Épico | `#8456B5` | `#EADFF4` | `#BE9AE6` | `#33244A` |
| Exclusivo | `#B59044` | `#F1E8CF` | `#D2B574` | `#3A3220` |

Los rangos épico y exclusivo añaden un contorno degradado de 2 dp y un resplandor; el exclusivo suma además reflejos diagonales y su nombre se dibuja con degradado dorado. Todo es estático: una animación continua impediría que la interfaz quedase en reposo.

---

## 3. Tipografía

Se incrustan dos fuentes variables como recursos compartidos (`composeResources/font`), para que Android y la web tengan el mismo aspecto: **Fraunces** (serif con carácter) para títulos y **Nunito** (redondeada) para el resto.

| Uso | Familia | Peso | Tamaño |
|---|---|---|---|
| Título de pantalla (cabecera) | Fraunces | Semibold | 22 sp |
| Título de ficha / sección | Fraunces | Semibold | 18 sp |
| Cuerpo y datos | Nunito | Normal | 15 sp |
| Etiqueta de campo / dato secundario | Nunito | Medium | 13 sp |
| Botones | Nunito | Semibold | 15 sp |

Fraunces da el aire de registro formal; Nunito mantiene las cifras legibles. Las cifras de métricas usan el mismo cuerpo, alineadas a la derecha.

---

## 4. Forma, espaciado y elevación

| Elemento | Regla |
|---|---|
| Esquinas | Pequeñas: 6 dp (campos, chips), 10 dp (fichas y botones). Las ventanas flotantes usan 22 a 24 dp, y los botones redondos o en píldora existen solo dentro de ellas |
| Fichas | Fondo `surface`, borde 1 dp `outlineVariant`, sin sombra; filete de 4 dp en el borde izquierdo con el color de la sección |
| Espaciado | Rejilla de 4 dp; margen lateral de pantalla 16 dp; separación entre fichas 12 dp; relleno interior de ficha 16 dp |
| Elevación | Plana. La cabecera se distingue por color; solo las ventanas flotantes y las fichas épicas y exclusivas llevan sombra |
| Separadores | Línea de 1 dp `outlineVariant` entre filas de datos; sin sombras ni degradados |

---

## 5. Componentes

### 5.1. Cabecera

Fondo `primaryContainer`, texto `onPrimaryContainer`, margen de 20 dp, respetando la barra de estado del sistema. Debajo, un filete de 3 dp `secondary` (corteza) que recuerda el borde de una ficha. Tiene dos filas:

1. Botón «Volver» circular de 48 dp con flecha (solo en pantallas que no son de sección), el título de la pantalla en Fraunces, la satisfacción global (abre el monitor de satisfacción) y los iconos de guía y de tienda, de 48 dp.
2. Cuatro datos pulsables, cada uno con icono y valor: saldo (abre los beneficios), bambú en kg (abre la tienda), medicinas (abre la tienda) y prestigio (abre el panel de prestigio).

Todos los objetivos táctiles miden al menos 48 dp. Mientras carga, los valores muestran «…».

### 5.1.1. Barra inferior

Barra de navegación Material 3 con tres secciones: **Hábitats**, **Misiones** y **Avisos**. La sección de la pantalla actual se resalta con `primaryContainer`. Las pantallas que no cuelgan de ninguna sección (tienda de recursos, tienda de animales, compra de animal, monitor, prestigio, beneficios y guía) no resaltan ninguna. Pulsar una sección lleva a su raíz.

### 5.2. Fondo de pantalla

`background` (pergamino / selva nocturna) liso. Sin imágenes.

### 5.3. Ficha (`Ficha`)

Contenedor reutilizable: cabecera opcional con título en serif y filete de color `primary`, cuerpo con filas de datos. Se usa en casi todas las pantallas. Admite un rango, que le da su tono (sección 2.4) y, en épico y exclusivo, su brillo; y puede mostrarse atenuada (60 % de opacidad) cuando no está disponible.

### 5.4. Fila de dato (`Fila`)

Etiqueta (`onSurfaceVariant`, 13 sp) a la izquierda; valor (`onSurface`, 15 sp, semibold) a la derecha; separador inferior de 1 dp.

### 5.5. Elemento de lista (hábitat, espacio)

Ficha pulsable con filete izquierdo. El color del filete identifica el tipo de entidad:

| Entidad | Filete |
|---|---|
| Hábitat disponible | `primary` |
| Hábitat no disponible | `outline` (la ficha se muestra atenuada y no se puede abrir) |
| Espacio | `tertiary`, con la ocupación como cifra y barra fina que se llena con los animales alojados |
| Misión pendiente | `secondary` |
| Misión cumplida | `primary` |
| Misión con animal exclusivo seguro | `#B59044` con brillo exclusivo |
| Animal o especie | el tono de su rango |

El estado siempre se acompaña de su texto, salvo el rango, que solo se comunica por color; el color de lo demás solo refuerza. Los espacios no tienen estado inactivo: se borran al venderlos.

### 5.6. Botones

| Tipo | Estilo |
|---|---|
| Principal (confirmar, navegar) | Relleno `primary`, texto `onPrimary`, esquina 10 dp, alto mínimo 48 dp |
| Secundario | Contorno `outline`, texto `primary` |
| Navegación hacia atrás | Botón circular de 48 dp con flecha en la cabecera |
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

El icono es un elemento adicional, no parte del texto del mensaje.

Los avisos de severidad baja y media de la pantalla de avisos se descartan deslizándolos hacia cualquier lado; al deslizar aparece un icono de cerrar sobre `surfaceVariant`. Los de severidad alta no se deslizan.

### 5.10. Carga

Texto "Cargando…" en `onBackground` sobre el fondo de pantalla.

### 5.11. Ventana flotante de confirmación

Toda acción con coste o irreversible (reclamar beneficios, reclamar recompensa, alimentar a un animal, a un espacio o a un hábitat, tratar, vender un animal, ampliar o vender un espacio, comprar un espacio) se confirma en una ventana flotante: superficie de 24 dp de esquina con borde fino y cabecera de color `primaryContainer` (`errorContainer` si es irreversible o peligrosa) que contiene una insignia circular de 72 dp con el icono de la acción; debajo, el título; una fila de **fichas de dato** (valor grande y etiqueta pequeña, sobre `surfaceVariant`; sobre `errorContainer` y con icono de error si el dato indica un problema, como bambú o medicinas insuficientes); una nota opcional; y dos botones: **cancelar**, circular de 56 dp con contorno y una cruz, y **confirmar**, píldora ancha de 56 dp con una marca de verificación (`primary`, o `error` si es peligrosa).

Los botones no llevan texto visible. Su texto («Cancelar», «Confirmar venta»…) vive en la semántica de accesibilidad y es el que usan los lectores de pantalla y las pruebas. Tocar fuera cancela. Detalle de las cifras de cada ventana: `Otros/DecisionesImplementacion.md` y los casos de uso.

### 5.12. Ventana de premio

Ventana de 22 dp de esquina con el tono del rango del premio, una insignia circular de 64 dp con el icono, la etiqueta («Regalo de bienvenida» o vacía) y el premio. Si el rango del animal se sortea entre varios, la ventana empieza mostrando «Animal aleatorio» con la tabla de rangos y porcentajes (cada rango con su color) y un botón «¡SUERTE!»; al pulsarlo se revela el animal. Mientras no se revela, tocar fuera no la cierra. Un animal exclusivo luce el contorno brillante, los reflejos y el nombre dorado, y lo anuncia con «Animal exclusivo». Las ventanas del regalo de bienvenida se muestran de una en una (fondos, bambú, medicinas y cada animal).

---

## 6. Pantallas actuales

Todas se montan sobre el marco común (cabecera y barra inferior) y la raíz es la lista de hábitats. No existe una pantalla de inicio aparte: sus datos (saldo, bambú, medicinas y prestigio) viven en la cabecera.

| Pantalla | Composición |
|---|---|
| Hábitats (RF-08) | Lista de fichas con el nombre del hábitat; los bloqueados se ven atenuados y no se abren |
| Espacios de un hábitat (RF-08) | Botones «Comprar espacio» y «Alimentar a todos» (se desactiva si nadie tiene hambre); fichas de espacio con ocupación y barra |
| Comprar espacio (CU-03) | Lista de capacidades, cada una con una insignia con las plazas, «N animales» y una pastilla con el precio («Gratis» en el espacio gratuito). Al elegir una capacidad, una ventana flotante pide la denominación y ofrece «Comprar — <coste>» cuando el nombre es válido |
| Animales de un espacio | Ficha de cada animal con nombre, especie, nivel y las excepciones (enfermo, hambre) y satisfacción; botón «Comprar un animal para este espacio», acceso a configurar el espacio y, si el espacio está vacío, «Vender espacio» (ventana con lo que se recupera) |
| Configurar espacio (CU-02, CU-03) | Detalle, cambio de nombre y hábitat y «Ampliar capacidad» (opciones con su coste real y ventana de confirmación; sin opciones si ya tiene la capacidad máxima) |
| Ficha del animal | Líneas de datos (especie, ubicación, nivel y progreso, valor de venta, alimentación, salud, satisfacción, pareja, mejor amigo), habilidades y rasgos, afinidades (CU-23) y acciones alimentar, tratar, vender, editar y reubicar, con ventana de confirmación |
| Tienda de recursos (CU-07) | Saldo en ficha, opciones de recurso con insignia e icono, campo de cantidad, coste total, botón principal |
| Tienda de animales | Ficha por especie con el tono de su rango, el nombre y el precio; «Gratis» si hay una unidad concedida; las bloqueadas muestran el prestigio que las desbloquea y las exclusivas «—» |
| Comprar animal (CU-01) | Saldo, ficha de la especie, hábitat y espacio (o fijos si se parte de un espacio), nombre, dos habilidades, dos rasgos, mejor amigo opcional y botón «Confirmar compra» |
| Beneficios (CU-08, CU-16) | Fichas de pendiente y saldo, botón «Reclamar beneficios» con ventana, y desglose desplegable por hábitat, espacio y animal |
| Misiones (CU-20, CU-15) | Una ficha por misión con descripción, barra de avance, progreso y recompensa; «Reclamar recompensa» con ventana y ventana de premio |
| Avisos (CU-21) | Botón «Seguimiento crítico», filtro por severidad (Todos, Alta, Media, Baja) y avisos abiertos; los de severidad baja y media se deslizan para descartarlos. Los cerrados no se muestran |
| Seguimiento crítico (CU-18) | Animales desnutridos o enfermos con sus ciclos y acceso a la ficha |
| Prestigio (CU-19) | Fichas de prestigio actual, siguiente prestigio con lo que desbloquea, estadísticas del parque y máximos históricos |
| Monitor de satisfacción (CU-17) | Parque y desglose desplegable hábitat, espacio y animal |
| Guía (CU-22) | Apartado «Convivencia» con las pestañas «Hábitats» (tabla de caras) y «Animales» (selector y tres bloques plegables) |

Las pantallas futuras reutilizan estos componentes; no definen estilos propios.

---

## 7. Criterios de aceptación

1. Todos los colores de la interfaz proceden del tema; ninguna pantalla contiene valores de color literales.
2. Los pares texto/fondo de las tablas 2.1, 2.2 y 2.3 cumplen contraste AA.
3. No se muestran identificadores técnicos (RES-06) y la terminología es la de `Otros/DecisionesImplementacion.md`, sección 11.
4. Las pruebas de UI de cada sprint pasan y localizan los botones de las ventanas por su texto semántico.
5. En 4.7" y 6.7", en vertical, no hay recortes ni desplazamiento horizontal (RNF-06).
6. Claro y oscuro funcionan según el ajuste del sistema.
7. Ningún componente nuevo introduce imágenes, 2D/3D ni animaciones espaciales (RES-01). Se admiten iconos vectoriales sencillos y los efectos de brillo estáticos de la sección 2.4.

## 8. Alcance

Incluido: archivo de tema (colores, tipografías, formas, tonos de rango), componentes visuales compartidos (ficha, fila de dato, aviso, ventanas), marco de pantalla y todas las pantallas de la sección 6.
Excluido: reglas de negocio, casos de uso y datos.
