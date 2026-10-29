package gt.usac.bd2.pruebas.carga;

public enum EstadoCarga {

	EN_PROCESO,

	/** Todas las filas se procesaron correctamente. */
	COMPLETADO,

	/** El archivo se procesó, pero algunas filas fueron rechazadas. */
	COMPLETADO_CON_RECHAZOS,

	/** El archivo no se pudo procesar (formato inválido, base de datos caída, etc.). */
	FALLIDO

}
