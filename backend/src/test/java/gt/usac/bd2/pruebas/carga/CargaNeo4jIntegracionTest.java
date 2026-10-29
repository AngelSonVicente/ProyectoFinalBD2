package gt.usac.bd2.pruebas.carga;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.neo4j.core.Neo4jClient;

/**
 * Requiere MongoDB y Neo4j de docker-compose. Neo4j Community solo tiene una
 * base de datos, así que todos los valores de prueba empiezan con ZZTEST y
 * solo esos nodos se borran: los datos reales no se tocan.
 */
@SpringBootTest(properties = {
		"spring.mongodb.uri=mongodb://admin:admin123@localhost:27017/pruebas_test?authSource=admin",
		"app.carga.tamano-lote=2" })
class CargaNeo4jIntegracionTest {

	private static final String ENCABEZADO = "fecha_asignacion,sexo,anio_nacimiento,materia,numero_de_fecha_de_evaluacion,"
			+ "anio_de_ingreso,aprobacion,carrera_objetivo,departamento_institucion_educativa,"
			+ "municipio_institucion_educativa,tipo_institucion_educativa,correlativo_aspirante\n";

	// 2 aspirantes del mismo municipio e institución; el primero hace 2 intentos de la misma materia
	private static final String CSV = ENCABEZADO
			+ "2014-08-23,M,1998,ZZTEST MATE,1,2015,NO APROBADO,ZZTEST CIVIL,ZZTEST DEPTO,ZZTEST MUNI,PUBLICO,zztest-a\n"
			+ "2014-10-11,SIN REGISTRO,SIN REGISTRO,ZZTEST MATE,2,2015,APROBADO,ZZTEST CIVIL,ZZTEST DEPTO,ZZTEST MUNI,PUBLICO,zztest-a\n"
			+ "2014-08-23,F,1997,ZZTEST FISICA,1,2015,APROBADO,ZZTEST CIVIL,ZZTEST DEPTO,ZZTEST MUNI,PUBLICO,zztest-b\n";

	@Autowired
	private CargaService cargaService;

	@Autowired
	private Neo4jClient neo4jClient;

	@Autowired
	private MongoTemplate mongoTemplate;

	@BeforeEach
	@AfterEach
	void limpiar() {
		neo4jClient.query("""
				MATCH (n)
				WHERE n.id STARTS WITH 'zztest' OR n.nombre STARTS WITH 'ZZTEST'
				   OR n.clave CONTAINS 'ZZTEST' OR n.municipio STARTS WITH 'ZZTEST'
				DETACH DELETE n
				""").run();
		mongoTemplate.getDb().drop();
	}

	private ResumenCarga cargar() {
		return cargaService.cargar(new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8)), "prueba.csv");
	}

	private long contar(String cypher) {
		return neo4jClient.query(cypher).fetchAs(Long.class).one().orElseThrow();
	}

	@Test
	void creaNodosYRelacionesSinDuplicar() {
		ResumenCarga resumen = cargar();

		assertThat(resumen.estado()).isEqualTo(EstadoCarga.COMPLETADO);
		assertThat(contar("MATCH (a:Aspirante) WHERE a.id STARTS WITH 'zztest' RETURN count(a)")).isEqualTo(2);
		assertThat(contar("MATCH (m:Materia) WHERE m.nombre STARTS WITH 'ZZTEST' RETURN count(m)")).isEqualTo(2);
		assertThat(contar("MATCH (c:Carrera {nombre: 'ZZTEST CIVIL'}) RETURN count(c)")).isEqualTo(1);
		assertThat(contar("MATCH (i:Institucion {clave: 'PUBLICO|ZZTEST DEPTO|ZZTEST MUNI'}) RETURN count(i)"))
				.isEqualTo(1);
		assertThat(contar("""
				MATCH (:Institucion {clave: 'PUBLICO|ZZTEST DEPTO|ZZTEST MUNI'})-[:UBICADA_EN]->(:Municipio)
				      -[:PERTENECE_A]->(:Departamento {nombre: 'ZZTEST DEPTO'})
				RETURN count(*)
				""")).isEqualTo(1);
		// Dos intentos de la misma materia = dos relaciones REALIZO
		assertThat(contar("MATCH (:Aspirante {id: 'zztest-a'})-[r:REALIZO]->(:Materia {nombre: 'ZZTEST MATE'}) RETURN count(r)"))
				.isEqualTo(2);
		assertThat(contar("MATCH (:Aspirante {id: 'zztest-a'})-[:ASPIRA_A]->(c:Carrera) RETURN count(c)")).isEqualTo(1);
	}

	@Test
	void conservaElDatoConocidoCuandoLlegaSinRegistro() {
		cargar();

		Map<String, Object> aspirante = neo4jClient
				.query("MATCH (a:Aspirante {id: 'zztest-a'}) RETURN a.sexo AS sexo, a.anio_nacimiento AS anio")
				.fetch().one().orElseThrow();
		assertThat(aspirante).containsEntry("sexo", "M").containsEntry("anio", 1998L);

		Map<String, Object> intento = neo4jClient.query("""
				MATCH (:Aspirante {id: 'zztest-a'})-[r:REALIZO {intento: 1}]->(:Materia)
				RETURN r.aprobado AS aprobado, r.edad AS edad
				""").fetch().one().orElseThrow();
		assertThat(intento).containsEntry("aprobado", false).containsEntry("edad", 17L);
	}

	@Test
	void volverACargarNoDuplicaElGrafo() {
		cargar();
		cargar();

		assertThat(contar("MATCH (a:Aspirante) WHERE a.id STARTS WITH 'zztest' RETURN count(a)")).isEqualTo(2);
		assertThat(contar("MATCH (a:Aspirante)-[r:REALIZO]->() WHERE a.id STARTS WITH 'zztest' RETURN count(r)"))
				.isEqualTo(3);
		assertThat(contar("MATCH (a:Aspirante)-[r:PROCEDE_DE]->() WHERE a.id STARTS WITH 'zztest' RETURN count(r)"))
				.isEqualTo(2);
	}

}
