package com.pandazoo.data

/** Ruta de un fichero temporal de base de datos para los tests de ficheros (lo único específico de plataforma en los tests). */
expect fun rutaTemporal(nombre: String): String

/** Borra el fichero de base de datos y sus auxiliares de WAL. */
expect fun borrarArchivoDeBd(ruta: String)

/** Modo de diario que debe tener una base de datos en fichero: WAL donde el motor lo admite; sql.js (web) no. */
expect val modoDiarioEsperado: String
