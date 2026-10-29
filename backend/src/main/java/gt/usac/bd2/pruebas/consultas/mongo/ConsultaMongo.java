package gt.usac.bd2.pruebas.consultas.mongo;

import java.util.List;

/**
 * Definición de una consulta leída de resources/queries/mongo/qNN.json.
 *
 * @param coleccionResultado colección que se devuelve al terminar, cuando el
 *                           pipeline escribe con $out en lugar de devolver filas
 * @param json               contenido completo del archivo; el pipeline se
 *                           vuelve a leer en cada ejecución para reemplazar los
 *                           parámetros sobre una copia limpia
 */
public record ConsultaMongo(
		int id,
		String titulo,
		String descripcion,
		String coleccion,
		String coleccionResultado,
		List<Parametro> parametros,
		String json) {

	/**
	 * Parámetro que se recibe por query string y reemplaza al texto
	 * "{{nombre}}" dentro del pipeline.
	 *
	 * @param tipo correlativo (se pasa a minúsculas), entero o texto (se normaliza como en el ETL)
	 */
	public record Parametro(String nombre, String tipo, boolean requerido, String descripcion) {
	}

}
