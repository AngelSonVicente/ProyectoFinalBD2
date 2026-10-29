package gt.usac.bd2.pruebas.config;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Indexes;

/**
 * Crea los índices al iniciar la aplicación. createIndex no hace nada si el
 * índice ya existe, así que es seguro ejecutarlo en cada arranque.
 */
@Component
public class MongoIndices implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(MongoIndices.class);

	private final MongoTemplate mongoTemplate;

	public MongoIndices(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		try {
			crearIndices();
			log.info("Índices de MongoDB verificados");
		}
		catch (MongoException e) {
			// Sin MongoDB la API sigue levantando y responde con error controlado en cada solicitud
			log.warn("No se pudieron crear los índices de MongoDB: {}", e.getMessage());
		}
	}

	private void crearIndices() {
		MongoCollection<Document> evaluaciones = mongoTemplate.getCollection(Colecciones.EVALUACIONES);
		evaluaciones.createIndex(Indexes.ascending("correlativo_aspirante"));
		evaluaciones.createIndex(Indexes.ascending("materia", "aprobado"));
		evaluaciones.createIndex(Indexes.ascending("carrera_objetivo", "anio_ingreso"));
		evaluaciones.createIndex(Indexes.ascending("tipo_institucion"));
		evaluaciones.createIndex(Indexes.ascending("departamento", "municipio"));

		mongoTemplate.getCollection(Colecciones.ASPIRANTES).createIndex(Indexes.ascending("carrera_objetivo"));
		mongoTemplate.getCollection(Colecciones.REGISTROS_RECHAZADOS)
				.createIndex(Indexes.ascending("carga_id", "numero_fila"));
		mongoTemplate.getCollection(Colecciones.CARGAS).createIndex(Indexes.descending("fecha_inicio"));
	}

}
