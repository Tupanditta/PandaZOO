
### CU-01:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Adquirir Espécimen
* **Objetivo:** Validar y confirmar atómicamente la adquisición de un espécimen desbloqueado, completar su ficha y ubicarlo en un espacio compatible con capacidad disponible.

### CU-02:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Configurar Espacio
* **Objetivo:** cambiar la denominación y/o el hábitat asignado de un espacio existente, sin alterar su capacidad máxima y validando que el hábitat de destino disponga de capacidad suficiente.

### CU-03:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Adquirir Espacios
* **Objetivo:** comprar un nuevo espacio en la tienda y ubicarlo en un hábitat

### CU-04:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Reubicar Espécimen
* **Objetivo:** mover un espécimen de un espacio concreto, a un espacio diferente

### CU-05:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Editar Ficha Espécimen
* **Objetivo:** editar una o varias variables modificables de la ficha técnica de un espécimen

### CU-06:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Vender Espécimen
* **Objetivo:** vender un espécimen del zoo

### CU-07:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Adquirir Insumos
* **Objetivo:** adquirir insumos en la tienda utilizando dinero

### CU-08:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Reclamar Beneficios
* **Objetivo:** reclamar los beneficios acumulados obtenidos por los ciclos

### CU-09:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Aplicar Tratamiento Médico
* **Objetivo:** aplicar tratamiento médico a un espécimen en estado enfermo utilizando los insumos médicos necesarios

### CU-10:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Aceptar Reproducción
* **Objetivo:** aceptar de forma idempotente la reproducción notificada, validar el aforo y completar la ficha técnica de la descendencia

### CU-11:
* **Actor Principal:** Motor de Simulación
* **Nombre:** Ejecutar ciclo de Simulación
* **Objetivo:** ejecutar un ciclo identificado, actualizar el estado en el orden definido y persistir sus resultados sin duplicar efectos

### CU-12:
* **Actor Principal:** Motor de Simulación
* **Nombre:** Procesar Simulación en Diferido
* **Objetivo:** inicializar la sesión tras tiempo inactivo, restaurar el último estado consistente y procesar el desfase sin duplicar ciclos

### CU-13:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Alimentar Espécimen
* **Objetivo:** Proporcionar una ración de alimento a un animal con hambre activa, deduciendo el insumo del inventario.

### CU-14:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Dar de Baja Espacio Vacío
* **Objetivo:** Confirmar la baja lógica de un espacio vacío sin romper referencias históricas ni operaciones pendientes

### CU-15:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Reclamar Recompensa de Misión
* **Objetivo:** Validar el cumplimiento de un objetivo en el módulo de progreso y acreditar los fondos o ítems devengados.

### CU-16:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Consultar Inventario y Finanzas
* **Objetivo:** Visualizar el stock de raciones, insumos médicos universales, costes fijos proyectados y balance monetario disponible.

### CU-17:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Consultar Monitor de Satisfacción
* **Objetivo:** Consultar los indicadores de satisfacción por nivel jerárquico (parque, hábitat, espacio, individuo) y desplegar las estadísticas globales

### CU-18:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Gestionar Alertas y Seguimiento Crítico
* **Objetivo:** Consultar el panel centralizado de animales desnutridos o enfermos y acceder de forma directa a la ficha técnica para su resolución.

### CU-19:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Consultar Prestigio
* **Objetivo:** Consultar prestigio actual, máximo histórico y los datos que influyen en el estado del prestigio actual (satisfacción global y animales totales)

### CU-20:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Consultar Misiones y Progreso
* **Objetivo:** consultar las misiones activas, sus objetivos, progreso y recompensas disponibles.

### CU-21:
* **Actor Principal:** Gestor del Zoo
* **Nombre:** Consultar Historial de Avisos
* **Objetivo:** consultar la bitácora cronológica de avisos, diferenciando sucesos informativos, notificaciones operativas interactivas y alertas críticas de salud.
