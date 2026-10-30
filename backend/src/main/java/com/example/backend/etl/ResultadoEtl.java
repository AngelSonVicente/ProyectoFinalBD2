package com.example.backend.etl;

/**
 * Resumen del procesamiento de un archivo.
 */
public record ResultadoEtl(String nombreArchivo, long leidos, long validos, long rechazados, long tiempoMs) {
}
