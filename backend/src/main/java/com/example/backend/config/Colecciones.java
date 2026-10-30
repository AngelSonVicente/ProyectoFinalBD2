package com.example.backend.config;

/**
 * Nombres de las colecciones de MongoDB usadas por la aplicación.
 */
public final class Colecciones {

	/** Una evaluación (fila válida del CSV) por documento. Base de las consultas analíticas. */
	public static final String EVALUACIONES = "evaluaciones";

	/** Un documento por aspirante con sus evaluaciones embebidas. */
	public static final String ASPIRANTES = "aspirantes";

	/** Filas rechazadas por el ETL con su causa. */
	public static final String REGISTROS_RECHAZADOS = "registros_rechazados";

	/** Historial de cargas de archivos. */
	public static final String CARGAS = "cargas";

	/** Colección auxiliar generada por la consulta 6. */
	public static final String RESUMEN_CARRERA = "resumen_carrera";

	private Colecciones() {
	}

}
