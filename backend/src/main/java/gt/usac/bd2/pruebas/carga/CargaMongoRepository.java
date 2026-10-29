package gt.usac.bd2.pruebas.carga;

import static com.mongodb.client.model.Filters.eq;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import gt.usac.bd2.pruebas.config.Colecciones;
import gt.usac.bd2.pruebas.etl.ReglasLimpieza;
import gt.usac.bd2.pruebas.etl.RegistroEvaluacion;
import gt.usac.bd2.pruebas.etl.RegistroRechazado;

/**
 * Escrituras de la carga en MongoDB. Todo se envía por lotes con bulkWrite
 * para no hacer una ida y vuelta al servidor por cada fila.
 */
@Repository
public class CargaMongoRepository {

	private static final BulkWriteOptions EN_ORDEN = new BulkWriteOptions().ordered(true);

	private final MongoTemplate mongoTemplate;

	public CargaMongoRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	/**
	 * Guarda un lote de evaluaciones y actualiza el documento de cada aspirante.
	 *
	 * @return cuántas evaluaciones del lote ya existían (duplicadas)
	 */
	public long guardarEvaluaciones(List<RegistroEvaluacion> lote, ObjectId cargaId, String archivo) {
		if (lote.isEmpty()) {
			return 0;
		}

		List<WriteModel<Document>> evaluaciones = new ArrayList<>(lote.size());
		List<WriteModel<Document>> aspirantes = new ArrayList<>(lote.size());
		ReplaceOptions upsert = new ReplaceOptions().upsert(true);
		UpdateOptions upsertAspirante = new UpdateOptions().upsert(true);

		for (RegistroEvaluacion r : lote) {
			Document doc = MapeoDocumentos.evaluacion(r, cargaId, archivo);
			evaluaciones.add(new ReplaceOneModel<>(eq("_id", doc.get("_id")), doc, upsert));
			aspirantes.add(new UpdateOneModel<>(eq("_id", r.correlativoAspirante()), actualizacionAspirante(r),
					upsertAspirante));
		}

		// En orden para que dos filas idénticas del mismo lote se detecten como duplicado
		var resultado = mongoTemplate.getCollection(Colecciones.EVALUACIONES).bulkWrite(evaluaciones, EN_ORDEN);
		mongoTemplate.getCollection(Colecciones.ASPIRANTES).bulkWrite(aspirantes, EN_ORDEN);

		return lote.size() - resultado.getUpserts().size();
	}

	/**
	 * Un aspirante aparece en varias filas. Los datos de procedencia se
	 * actualizan con la fila más reciente procesada; el sexo y el año de
	 * nacimiento solo se escriben si la fila los trae, para no borrar un dato
	 * conocido con un SIN REGISTRO.
	 */
	private static Bson actualizacionAspirante(RegistroEvaluacion r) {
		List<Bson> cambios = new ArrayList<>();
		cambios.add(Updates.set("carrera_objetivo", r.carreraObjetivo()));
		cambios.add(Updates.set("departamento", r.departamento()));
		cambios.add(Updates.set("municipio", r.municipio()));
		cambios.add(Updates.set("tipo_institucion", r.tipoInstitucion()));
		cambios.add(Updates.addToSet("carreras", r.carreraObjetivo()));
		cambios.add(Updates.addToSet("materias", r.materia()));
		cambios.add(Updates.addToSet("evaluaciones", MapeoDocumentos.evaluacionEmbebida(r)));
		if (!ReglasLimpieza.SIN_REGISTRO.equals(r.sexo())) {
			cambios.add(Updates.set("sexo", r.sexo()));
		}
		if (r.anioNacimiento() != null) {
			cambios.add(Updates.set("anio_nacimiento", r.anioNacimiento()));
		}
		return Updates.combine(cambios);
	}

	public void guardarRechazados(List<RegistroRechazado> lote, ObjectId cargaId, String archivo) {
		if (lote.isEmpty()) {
			return;
		}
		List<Document> documentos = lote.stream().map(r -> MapeoDocumentos.rechazado(r, cargaId, archivo)).toList();
		mongoTemplate.getCollection(Colecciones.REGISTROS_RECHAZADOS).insertMany(documentos);
	}

	public void registrarInicio(ObjectId cargaId, String archivo, Date inicio) {
		mongoTemplate.getCollection(Colecciones.CARGAS).insertOne(new Document("_id", cargaId)
				.append("archivo", archivo)
				.append("estado", EstadoCarga.EN_PROCESO.name())
				.append("fecha_inicio", inicio));
	}

	public void registrarFin(ResumenCarga resumen, Date fin) {
		mongoTemplate.getCollection(Colecciones.CARGAS).updateOne(eq("_id", new ObjectId(resumen.id())),
				Updates.combine(
						Updates.set("estado", resumen.estado().name()),
						Updates.set("leidos", resumen.leidos()),
						Updates.set("procesados", resumen.procesados()),
						Updates.set("rechazados", resumen.rechazados()),
						Updates.set("duplicados", resumen.duplicados()),
						Updates.set("tiempo_ms", resumen.tiempoMs()),
						Updates.set("mensaje", resumen.mensaje()),
						Updates.set("fecha_fin", fin)));
	}

	public List<Document> listarCargas() {
		return mongoTemplate.getCollection(Colecciones.CARGAS).find().sort(Sorts.descending("fecha_inicio"))
				.into(new ArrayList<>());
	}

	public Document buscarCarga(ObjectId cargaId) {
		return mongoTemplate.getCollection(Colecciones.CARGAS).find(eq("_id", cargaId)).first();
	}

	public long contarRechazados(ObjectId cargaId) {
		return mongoTemplate.getCollection(Colecciones.REGISTROS_RECHAZADOS).countDocuments(eq("carga_id", cargaId));
	}

	public List<Document> listarRechazados(ObjectId cargaId, int pagina, int tamano) {
		return mongoTemplate.getCollection(Colecciones.REGISTROS_RECHAZADOS)
				.find(eq("carga_id", cargaId))
				.sort(Sorts.ascending("numero_fila"))
				.skip(pagina * tamano)
				.limit(tamano)
				.into(new ArrayList<>());
	}

}
