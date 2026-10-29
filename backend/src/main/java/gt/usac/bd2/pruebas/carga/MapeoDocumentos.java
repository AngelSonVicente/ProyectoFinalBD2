package gt.usac.bd2.pruebas.carga;

import java.time.ZoneOffset;
import java.util.Date;

import org.bson.Document;
import org.bson.types.ObjectId;

import gt.usac.bd2.pruebas.etl.RegistroEvaluacion;
import gt.usac.bd2.pruebas.etl.RegistroRechazado;

/**
 * Convierte los registros del ETL en documentos de MongoDB. Los nombres de
 * campo siguen los del CSV original para que las consultas sean fáciles de
 * leer en mongosh.
 */
final class MapeoDocumentos {

	private MapeoDocumentos() {
	}

	/**
	 * Identificador determinístico de una evaluación. Si el mismo archivo se
	 * carga dos veces, la evaluación se reemplaza en lugar de duplicarse.
	 */
	static String idEvaluacion(RegistroEvaluacion r) {
		return r.correlativoAspirante() + "|" + r.materia() + "|" + r.fechaAsignacion() + "|" + r.intento() + "|"
				+ r.anioIngreso();
	}

	static Document evaluacion(RegistroEvaluacion r, ObjectId cargaId, String archivo) {
		return new Document("_id", idEvaluacion(r))
				.append("correlativo_aspirante", r.correlativoAspirante())
				.append("fecha_asignacion", aDate(r))
				.append("anio_asignacion", r.anioAsignacion())
				.append("mes_asignacion", r.mesAsignacion())
				.append("sexo", r.sexo())
				.append("anio_nacimiento", r.anioNacimiento())
				.append("edad", r.edad())
				.append("materia", r.materia())
				.append("intento", r.intento())
				.append("anio_ingreso", r.anioIngreso())
				.append("aprobado", r.aprobado())
				.append("carrera_objetivo", r.carreraObjetivo())
				.append("departamento", r.departamento())
				.append("municipio", r.municipio())
				.append("tipo_institucion", r.tipoInstitucion())
				.append("carga_id", cargaId)
				.append("archivo", archivo);
	}

	/**
	 * Resumen de la evaluación que se embebe en el documento del aspirante.
	 * El orden de los campos debe ser siempre el mismo para que $addToSet
	 * reconozca los duplicados.
	 */
	static Document evaluacionEmbebida(RegistroEvaluacion r) {
		return new Document("evaluacion_id", idEvaluacion(r))
				.append("materia", r.materia())
				.append("fecha_asignacion", aDate(r))
				.append("intento", r.intento())
				.append("anio_ingreso", r.anioIngreso())
				.append("aprobado", r.aprobado())
				.append("edad", r.edad())
				.append("carrera_objetivo", r.carreraObjetivo())
				.append("tipo_institucion", r.tipoInstitucion())
				.append("municipio", r.municipio())
				.append("departamento", r.departamento());
	}

	static Document rechazado(RegistroRechazado r, ObjectId cargaId, String archivo) {
		return new Document("carga_id", cargaId)
				.append("archivo", archivo)
				.append("numero_fila", r.numeroFila())
				.append("causas", r.causas())
				.append("causa", r.causa())
				.append("fila_original", new Document(r.filaOriginal()));
	}

	private static Date aDate(RegistroEvaluacion r) {
		return Date.from(r.fechaAsignacion().atStartOfDay(ZoneOffset.UTC).toInstant());
	}

}
