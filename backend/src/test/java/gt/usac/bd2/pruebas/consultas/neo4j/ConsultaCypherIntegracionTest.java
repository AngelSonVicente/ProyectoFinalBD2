package gt.usac.bd2.pruebas.consultas.neo4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import gt.usac.bd2.pruebas.carga.CargaService;

/**
 * Carga un grafo pequeño con resultados calculados a mano y verifica cada
 * consulta Cypher. Requiere MongoDB y Neo4j de docker-compose. Como Neo4j
 * Community solo tiene una base de datos, todos los valores empiezan con
 * ZZTEST, las consultas se filtran por ellos y solo esos nodos se borran.
 *
 * <pre>
 * a: PUBLICO, ZZTEST DEPTO/MUNI, CIVIL    → MATE 1 NO, MATE 2 SI, FISICA 1 SI
 * b: PRIVADO, ZZTEST DEPTO/MUNI, CIVIL    → MATE 1 SI, FISICA 1 NO
 * c: PUBLICO, ZZTEST DEPTO/MUNI, SISTEMAS → MATE 1 NO, MATE 2 NO, MATE 3 SI
 * d: PUBLICO, ZZTEST OTRO/PUEBLO, SISTEMAS→ MATE 1 SI
 * </pre>
 */
@SpringBootTest(properties = "spring.mongodb.uri=mongodb://admin:admin123@localhost:27017/pruebas_test?authSource=admin")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsultaCypherIntegracionTest {

	private static final String CSV = """
			fecha_asignacion,sexo,anio_nacimiento,materia,numero_de_fecha_de_evaluacion,anio_de_ingreso,aprobacion,carrera_objetivo,departamento_institucion_educativa,municipio_institucion_educativa,tipo_institucion_educativa,correlativo_aspirante
			2014-08-23,M,1998,ZZTEST MATE,1,2015,NO APROBADO,ZZTEST CIVIL,ZZTEST DEPTO,ZZTEST MUNI,PUBLICO,zztest-a
			2014-10-11,M,1998,ZZTEST MATE,2,2015,APROBADO,ZZTEST CIVIL,ZZTEST DEPTO,ZZTEST MUNI,PUBLICO,zztest-a
			2014-08-23,M,1998,ZZTEST FISICA,1,2015,APROBADO,ZZTEST CIVIL,ZZTEST DEPTO,ZZTEST MUNI,PUBLICO,zztest-a
			2014-08-23,F,1997,ZZTEST MATE,1,2015,APROBADO,ZZTEST CIVIL,ZZTEST DEPTO,ZZTEST MUNI,PRIVADO,zztest-b
			2014-09-15,F,1997,ZZTEST FISICA,1,2015,NO APROBADO,ZZTEST CIVIL,ZZTEST DEPTO,ZZTEST MUNI,PRIVADO,zztest-b
			2014-08-23,M,1999,ZZTEST MATE,1,2015,NO APROBADO,ZZTEST SISTEMAS,ZZTEST DEPTO,ZZTEST MUNI,PUBLICO,zztest-c
			2014-10-11,M,1999,ZZTEST MATE,2,2015,NO APROBADO,ZZTEST SISTEMAS,ZZTEST DEPTO,ZZTEST MUNI,PUBLICO,zztest-c
			2014-11-20,M,1999,ZZTEST MATE,3,2015,APROBADO,ZZTEST SISTEMAS,ZZTEST DEPTO,ZZTEST MUNI,PUBLICO,zztest-c
			2015-08-20,F,2000,ZZTEST MATE,1,2016,APROBADO,ZZTEST SISTEMAS,ZZTEST OTRO,ZZTEST PUEBLO,PUBLICO,zztest-d
			""";

	@Autowired
	private ConsultaCypherService servicio;

	@Autowired
	private CatalogoConsultasCypher catalogo;

	@Autowired
	private CargaService cargaService;

	@Autowired
	private Neo4jClient neo4jClient;

	@Autowired
	private MongoTemplate mongoTemplate;

	@BeforeAll
	void cargarDatos() {
		limpiar();
		cargaService.cargar(List.of(new MockMultipartFile("archivos", "grafo.csv", "text/csv",
				CSV.getBytes(StandardCharsets.UTF_8))));
	}

	@AfterAll
	void limpiar() {
		neo4jClient.query("""
				MATCH (n)
				WHERE n.id STARTS WITH 'zztest' OR n.nombre STARTS WITH 'ZZTEST'
				   OR n.clave CONTAINS 'ZZTEST' OR n.municipio STARTS WITH 'ZZTEST'
				DETACH DELETE n
				""").run();
		mongoTemplate.getDb().drop();
	}

	private static List<String> nombres(ConsultaCypherService.ResultadoGrafo resultado, String categoria) {
		return resultado.nodes().stream().filter(n -> n.categoria().equals(categoria)).map(ConstructorGrafo.Nodo::nombre)
				.toList();
	}

	@Test
	void hayCincoConsultasYTodasSeEjecutan() {
		assertThat(catalogo.todas()).hasSize(5);
		catalogo.todas().forEach(c -> assertThat(servicio.ejecutar(c.id(),
				Map.of("correlativo", "zztest-a", "departamento", "zztest depto", "materia", "zztest mate"))
				.nodes())
				.as("consulta %d", c.id())
				.isNotEmpty());
	}

	@Test
	void c01RedDeUnAspirante() {
		var resultado = servicio.ejecutar(1, Map.of("correlativo", " ZZTEST-A "));

		// Aspirante, 2 materias, carrera, institución, municipio y departamento
		assertThat(resultado.nodes()).hasSize(7);
		assertThat(nombres(resultado, "Institucion")).containsExactly("PUBLICO (ZZTEST MUNI)");
		// 3 REALIZO (uno por intento) + ASPIRA_A + PROCEDE_DE + UBICADA_EN + PERTENECE_A
		assertThat(resultado.links()).hasSize(7);
		assertThat(resultado.links()).filteredOn(l -> l.tipo().equals("REALIZO")).hasSize(3)
				.allSatisfy(l -> assertThat(l.propiedades()).containsKeys("intento", "aprobado", "fecha"));
		assertThat(resultado.filas()).hasSize(7);
	}

	@Test
	void c02CarrerasPorDepartamento() {
		var resultado = servicio.ejecutar(2, Map.of("departamento", "zztest depto"));

		assertThat(resultado.filas()).extracting(f -> f.get("carrera") + " " + f.get("aspirantes"))
				.containsExactly("ZZTEST CIVIL 2", "ZZTEST SISTEMAS 1");
		// Enlaces calculados Departamento → Carrera con la cantidad como propiedad
		assertThat(resultado.links()).hasSize(2)
				.allSatisfy(l -> assertThat(l.tipo()).isEqualTo("ASPIRANTES"));
		assertThat(resultado.nodes()).hasSize(3);
	}

	@Test
	void c03ReprobaronYAprobaronDespues() {
		var resultado = servicio.ejecutar(3, Map.of("materia", "zztest mate"));

		assertThat(resultado.filas()).extracting(f -> f.get("aspirante") + " " + f.get("intento_aprobado"))
				.containsExactly("zztest-a 2", "zztest-c 3");
		// Cada aspirante muestra el intento 1 reprobado y el primer intento aprobado
		assertThat(resultado.links()).filteredOn(l -> l.tipo().equals("REALIZO")).hasSize(4);
		assertThat(servicio.ejecutar(3, Map.of("materia", "zztest mate", "carrera", "zztest civil")).filas())
				.hasSize(1);
	}

	@Test
	void c04TasaPorDepartamentoYMateria() {
		var todos = servicio.ejecutar(4, Map.of("departamento", "zztest depto", "materia", "zztest mate"));
		assertThat(todos.filas()).singleElement().satisfies(f -> {
			assertThat(f).containsEntry("evaluaciones", 6L).containsEntry("aprobadas", 3L);
			assertThat(f).containsEntry("porcentaje_aprobacion", 50.0);
		});

		var publicos = servicio.ejecutar(4, Map.of("departamento", "zztest depto", "materia", "zztest mate",
				"tipo", "publico"));
		assertThat(publicos.filas().getFirst()).containsEntry("porcentaje_aprobacion", 40.0);
		assertThat(publicos.links()).singleElement()
				.satisfies(l -> assertThat(l.propiedades()).containsEntry("evaluaciones", 5L));
	}

	@Test
	void c05MismoMunicipioYCarrera() {
		var resultado = servicio.ejecutar(5, Map.of("departamento", "zztest depto"));

		// Solo CIVIL en ZZTEST MUNI tiene más de un aspirante
		assertThat(resultado.filas()).extracting(f -> f.get("aspirante"))
				.containsExactlyInAnyOrder("zztest-a", "zztest-b");
		assertThat(resultado.filas()).allSatisfy(f -> assertThat(f).containsEntry("aspirantes_en_grupo", 2L));
		assertThat(nombres(resultado, "Carrera")).containsExactly("ZZTEST CIVIL");
		assertThat(nombres(resultado, "Institucion"))
				.containsExactlyInAnyOrder("PUBLICO (ZZTEST MUNI)", "PRIVADO (ZZTEST MUNI)");
	}

	@Test
	void validaParametrosYConsultasInexistentes() {
		assertThatThrownBy(() -> servicio.ejecutar(1, Map.of()))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("correlativo");
		assertThatThrownBy(() -> servicio.ejecutar(2, Map.of("limite", "0")))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("limite");
		assertThatThrownBy(() -> servicio.ejecutar(2, Map.of("limite", "abc")))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("entero");
		assertThatThrownBy(() -> servicio.ejecutar(99, Map.of()))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("99");
	}

}
