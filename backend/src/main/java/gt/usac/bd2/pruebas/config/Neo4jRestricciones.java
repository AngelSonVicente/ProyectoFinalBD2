package gt.usac.bd2.pruebas.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

/**
 * Crea las restricciones de unicidad del grafo al iniciar. Además de evitar
 * nodos duplicados, cada restricción crea un índice que hace rápidos los
 * MERGE de la carga.
 */
@Component
public class Neo4jRestricciones implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(Neo4jRestricciones.class);

	private static final List<String> RESTRICCIONES = List.of(
			"CREATE CONSTRAINT aspirante_id IF NOT EXISTS FOR (a:Aspirante) REQUIRE a.id IS UNIQUE",
			"CREATE CONSTRAINT materia_nombre IF NOT EXISTS FOR (m:Materia) REQUIRE m.nombre IS UNIQUE",
			"CREATE CONSTRAINT carrera_nombre IF NOT EXISTS FOR (c:Carrera) REQUIRE c.nombre IS UNIQUE",
			"CREATE CONSTRAINT departamento_nombre IF NOT EXISTS FOR (d:Departamento) REQUIRE d.nombre IS UNIQUE",
			"CREATE CONSTRAINT municipio_clave IF NOT EXISTS FOR (m:Municipio) REQUIRE m.clave IS UNIQUE",
			"CREATE CONSTRAINT institucion_clave IF NOT EXISTS FOR (i:Institucion) REQUIRE i.clave IS UNIQUE",
			// Índice sobre la relación para que el MERGE de cada evaluación no recorra todas las del aspirante
			"CREATE INDEX realizo_id IF NOT EXISTS FOR ()-[r:REALIZO]-() ON (r.id)");

	private final Neo4jClient neo4jClient;

	public Neo4jRestricciones(Neo4jClient neo4jClient) {
		this.neo4jClient = neo4jClient;
	}

	@Override
	public void run(ApplicationArguments args) {
		try {
			RESTRICCIONES.forEach(cypher -> neo4jClient.query(cypher).run());
			log.info("Restricciones de Neo4j verificadas");
		}
		catch (DataAccessException | org.neo4j.driver.exceptions.Neo4jException e) {
			// Sin Neo4j la API sigue levantando y responde con error controlado en cada solicitud
			log.warn("No se pudieron crear las restricciones de Neo4j: {}", e.getMessage());
		}
	}

}
