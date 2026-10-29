package gt.usac.bd2.pruebas.carga;

/**
 * Resultado de cargar un archivo, tal como se muestra en la interfaz.
 *
 * @param procesados filas válidas guardadas (incluye las duplicadas, que se reemplazan)
 * @param duplicados filas válidas cuya evaluación ya existía en la base de datos
 */
public record ResumenCarga(
		String id,
		String nombreArchivo,
		long leidos,
		long procesados,
		long rechazados,
		long duplicados,
		long tiempoMs,
		EstadoCarga estado,
		String mensaje) {
}
