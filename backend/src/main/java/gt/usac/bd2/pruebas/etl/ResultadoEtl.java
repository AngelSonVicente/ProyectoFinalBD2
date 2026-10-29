package gt.usac.bd2.pruebas.etl;

/**
 * Resumen del procesamiento de un archivo.
 */
public record ResultadoEtl(String nombreArchivo, long leidos, long validos, long rechazados, long tiempoMs) {
}
