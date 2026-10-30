package com.example.backend.consultas.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import com.example.backend.carga.CargaNeo4jRepository;
import com.example.backend.carga.CargaService;

/**
 * Carga un conjunto pequeño de datos con resultados calculados a mano y
 * verifica cada consulta. Requiere el MongoDB de docker-compose (base pruebas_test).
 *
 * <pre>
 * a: M, 1998, PUBLICO, MIXCO, CIVIL     → 2015: MATE 1 NO, MATE 2 SI, FISICA 1 SI   (edad 17)
 * b: F, 1997, PRIVADO, ESCUINTLA, SIST. → 2015: MATE 1 SI, FISICA 1 NO              (edad 18)
 * c: M, sin año, PUBLICO, MIXCO, NINGUNO→ 2015: MATE 1 NO                           (sin edad)
 * d: F, 2000, PUBLICO, VILLA NUEVA, SIST→ 2016: MATE 1 SI                           (edad 16)
 * </pre>
 */
@SpringBootTest(properties = "spring.mongodb.uri=mongodb://admin:admin123@localhost:27017/pruebas_test?authSource=admin")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsultaMongoIntegracionTest {

	private static final String CSV = """
			fecha_asignacion,sexo,anio_nacimiento,materia,numero_de_fecha_de_evaluacion,anio_de_ingreso,aprobacion,carrera_objetivo,departamento_institucion_educativa,municipio_institucion_educativa,tipo_institucion_educativa,correlativo_aspirante
			2014-08-23,M,1998,MATEMATICA,1,2015,NO APROBADO,INGENIERIA CIVIL,GUATEMALA,MIXCO,PUBLICO,a
			2014-10-11,M,1998,MATEMATICA,2,2015,APROBADO,INGENIERIA CIVIL,GUATEMALA,MIXCO,PUBLICO,a
			2014-08-23,M,1998,FISICA,1,2015,APROBADO,INGENIERIA CIVIL,GUATEMALA,MIXCO,PUBLICO,a
			2014-08-23,F,1997,MATEMATICA,1,2015,APROBADO,INGENIERIA EN SISTEMAS,ESCUINTLA,ESCUINTLA,PRIVADO,b
			2014-09-15,F,1997,FISICA,1,2015,NO APROBADO,INGENIERIA EN SISTEMAS,ESCUINTLA,ESCUINTLA,PRIVADO,b
			2014-08-23,M,SIN REGISTRO,MATEMATICA,1,2015,NO APROBADO,NINGUNO,GUATEMALA,MIXCO,PUBLICO,c
			2015-08-20,F,2000,MATEMATICA,1,2016,APROBADO,INGENIERIA EN SISTEMAS,GUATEMALA,VILLA NUEVA,PUBLICO,d
			""";

	@Autowired
	private ConsultaMongoService servicio;

	@Autowired
	private CatalogoConsultasMongo catalogo;

	@Autowired
	private CargaService cargaService;

	@Autowired
	private MongoTemplate mongoTemplate;

	@MockitoBean
	private CargaNeo4jRepository grafo;

	@BeforeAll
	void cargarDatos() {
		mongoTemplate.getDb().drop();
		cargaService.cargar(List.of(new MockMultipartFile("archivos", "consultas.csv", "text/csv",
				CSV.getBytes(StandardCharsets.UTF_8))));
	}

	private List<Document> ejecutar(int id) {
		return servicio.ejecutar(id, Map.of()).datos();
	}

	/** Indexa las filas por el valor de un campo para comparar fácilmente. */
	private static Map<Object, Document> por(List<Document> filas, String campo) {
		return filas.stream().collect(Collectors.toMap(d -> d.get(campo), Function.identity()));
	}

	private static double num(Document d, String campo) {
		return ((Number) d.get(campo)).doubleValue();
	}

	@Test
	void hayVeinteConsultasYTodasSeEjecutan() {
		assertThat(catalogo.todas()).hasSize(20);
		catalogo.todas().forEach(c -> assertThat(servicio.ejecutar(c.id(), Map.of("correlativo", "a")).datos())
				.as("consulta %d", c.id())
				.isNotEmpty());
	}

	@Test
	void q01AspirantesPorTipoDeInstitucion() {
		Map<Object, Document> filas = por(ejecutar(1), "tipo_institucion");
		assertThat(num(filas.get("PUBLICO"), "aspirantes")).isEqualTo(3);
		assertThat(num(filas.get("PRIVADO"), "aspirantes")).isEqualTo(1);
	}

	@Test
	void q02Y04AprobadosYPorcentajePorMateria() {
		assertThat(num(por(ejecutar(2), "materia").get("MATEMATICA"), "aprobadas")).isEqualTo(3);

		Map<Object, Document> filas = por(ejecutar(4), "materia");
		assertThat(num(filas.get("MATEMATICA"), "porcentaje_aprobacion")).isEqualTo(60.0);
		assertThat(num(filas.get("FISICA"), "porcentaje_aprobacion")).isEqualTo(50.0);
	}

	@Test
	void q05EdadPromedioPorCarreraExcluyeSinEdad() {
		Map<Object, Document> filas = por(ejecutar(5), "carrera_objetivo");
		assertThat(num(filas.get("INGENIERIA EN SISTEMAS"), "edad_promedio")).isEqualTo(17.0);
		assertThat(filas).doesNotContainKey("SIN CARRERA");
	}

	@Test
	void q06GeneraLaColeccionResumenCarrera() {
		ejecutar(6);
		Document sistemas = mongoTemplate.getCollection("resumen_carrera")
				.find(new Document("_id", "INGENIERIA EN SISTEMAS")).first();
		assertThat(num(sistemas, "aspirantes")).isEqualTo(2);
		assertThat(num(sistemas, "total_evaluaciones")).isEqualTo(3);
		assertThat(num(sistemas, "porcentaje_aprobacion")).isEqualTo(66.67);
		assertThat(sistemas.getList("materias", String.class)).containsExactly("FISICA", "MATEMATICA");
		assertThat(mongoTemplate.getCollection("resumen_carrera").countDocuments()).isEqualTo(3);
	}

	@Test
	void q09EvaluacionesPorMesEnInstitucionesPublicas() {
		List<Document> filas = ejecutar(9);
		assertThat(filas).extracting(d -> d.get("mes") + " " + d.get("materia") + " " + d.get("evaluaciones"))
				.containsExactly("2014-08 FISICA 1", "2014-08 MATEMATICA 2", "2014-10 MATEMATICA 1",
						"2015-08 MATEMATICA 1");
	}

	@Test
	void q10TopCarrerasDe16A18Anios() {
		List<Document> filas = ejecutar(10);
		assertThat(filas).extracting(d -> d.get("carrera_objetivo"))
				.containsExactly("INGENIERIA EN SISTEMAS", "INGENIERIA CIVIL");
		assertThat(num(filas.getFirst(), "aspirantes")).isEqualTo(2);
	}

	@Test
	void q11HistorialPorMateriaAceptaCorrelativoEnMayusculas() {
		Map<Object, Document> filas = por(servicio.ejecutar(11, Map.of("correlativo", " A ")).datos(), "materia");
		assertThat(num(filas.get("MATEMATICA"), "intentos")).isEqualTo(2);
		assertThat(filas.get("MATEMATICA").getBoolean("aprobada")).isTrue();
		assertThat(filas.get("MATEMATICA").getList("evaluaciones", Document.class)).hasSize(2);
	}

	@Test
	void q12DistribucionPorSexoYTipo() {
		List<Document> filas = ejecutar(12);
		assertThat(filas).extracting(d -> d.get("sexo") + " " + d.get("tipo_institucion") + " " + d.get("aspirantes"))
				.containsExactlyInAnyOrder("M PUBLICO 2", "F PUBLICO 1", "F PRIVADO 1");
	}

	@Test
	void q13TasaDeAprobacionPorEdad() {
		Map<Object, Document> filas = por(ejecutar(13), "edad");
		assertThat(num(filas.get(16), "porcentaje_aprobacion")).isEqualTo(100.0);
		assertThat(num(filas.get(17), "porcentaje_aprobacion")).isEqualTo(66.67);
		assertThat(num(filas.get(18), "porcentaje_aprobacion")).isEqualTo(50.0);
	}

	@Test
	void q14PromedioDeIntentosPorMateria() {
		Document mate = por(ejecutar(14), "materia").get("MATEMATICA");
		assertThat(num(mate, "promedio_intentos")).isEqualTo(1.25);
		assertThat(num(mate, "maximo_intentos")).isEqualTo(2);
	}

	@Test
	void q15HistorialCompleto() {
		Document a = servicio.ejecutar(15, Map.of("correlativo", "a")).datos().getFirst();
		assertThat(a.getString("correlativo_aspirante")).isEqualTo("a");
		assertThat(num(a, "total_evaluaciones")).isEqualTo(3);
		assertThat(num(a, "evaluaciones_aprobadas")).isEqualTo(2);
	}

	@Test
	void q16ReprobadosEnPrimerIntento() {
		assertThat(ejecutar(16)).extracting(d -> d.get("carrera_objetivo") + " " + d.get("aspirantes_reprobados"))
				.containsExactly("INGENIERIA CIVIL 1", "INGENIERIA EN SISTEMAS 1");
	}

	@Test
	void q17TopMunicipios() {
		Document primero = ejecutar(17).getFirst();
		assertThat(primero.getString("municipio")).isEqualTo("MIXCO");
		assertThat(num(primero, "aspirantes")).isEqualTo(2);
	}

	@Test
	void q18TasaPorTipoDeInstitucion() {
		Map<Object, Document> filas = por(ejecutar(18), "tipo_institucion");
		assertThat(num(filas.get("PUBLICO"), "porcentaje_aprobacion")).isEqualTo(60.0);
		assertThat(num(filas.get("PRIVADO"), "porcentaje_aprobacion")).isEqualTo(50.0);
	}

	@Test
	void q19CarrerasPorDepartamento() {
		Map<Object, Document> filas = por(ejecutar(19), "departamento");
		assertThat(filas.get("GUATEMALA").getList("carreras", Document.class))
				.extracting(c -> c.getString("carrera_objetivo"))
				.containsExactlyInAnyOrder("INGENIERIA CIVIL", "INGENIERIA EN SISTEMAS");
	}

	@Test
	void q20EvolucionPorAnio() {
		Map<Object, Document> filas = por(ejecutar(20), "anio_ingreso");
		assertThat(num(filas.get(2015), "total")).isEqualTo(6);
		assertThat(num(filas.get(2015), "porcentaje_aprobadas")).isEqualTo(50.0);
		assertThat(num(filas.get(2016), "porcentaje_aprobadas")).isEqualTo(100.0);
	}

	@Test
	void validaParametrosYConsultasInexistentes() {
		assertThatThrownBy(() -> servicio.ejecutar(11, Map.of()))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("correlativo");
		assertThatThrownBy(() -> servicio.ejecutar(99, Map.of()))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("99");
	}

}
