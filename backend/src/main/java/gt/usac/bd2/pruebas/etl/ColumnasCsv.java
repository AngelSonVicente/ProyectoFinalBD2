package gt.usac.bd2.pruebas.etl;

import java.util.List;

/**
 * Columnas que debe traer cada archivo del dataset "Pruebas específicas".
 */
public final class ColumnasCsv {

	public static final String FECHA_ASIGNACION = "fecha_asignacion";
	public static final String SEXO = "sexo";
	public static final String ANIO_NACIMIENTO = "anio_nacimiento";
	public static final String MATERIA = "materia";
	public static final String NUMERO_FECHA_EVALUACION = "numero_de_fecha_de_evaluacion";
	public static final String ANIO_INGRESO = "anio_de_ingreso";
	public static final String APROBACION = "aprobacion";
	public static final String CARRERA_OBJETIVO = "carrera_objetivo";
	public static final String DEPARTAMENTO = "departamento_institucion_educativa";
	public static final String MUNICIPIO = "municipio_institucion_educativa";
	public static final String TIPO_INSTITUCION = "tipo_institucion_educativa";
	public static final String CORRELATIVO = "correlativo_aspirante";

	public static final List<String> OBLIGATORIAS = List.of(
			FECHA_ASIGNACION, SEXO, ANIO_NACIMIENTO, MATERIA, NUMERO_FECHA_EVALUACION, ANIO_INGRESO,
			APROBACION, CARRERA_OBJETIVO, DEPARTAMENTO, MUNICIPIO, TIPO_INSTITUCION, CORRELATIVO);

	private ColumnasCsv() {
	}

}
