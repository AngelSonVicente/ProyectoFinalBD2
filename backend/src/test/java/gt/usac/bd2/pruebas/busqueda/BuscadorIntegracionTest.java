package gt.usac.bd2.pruebas.busqueda;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import gt.usac.bd2.pruebas.carga.CargaNeo4jRepository;
import gt.usac.bd2.pruebas.carga.CargaService;

/**
 * Prueba el buscador y las respuestas de error a través de HTTP. Requiere el
 * MongoDB de docker-compose (base pruebas_test).
 */
@SpringBootTest(properties = "spring.mongodb.uri=mongodb://admin:admin123@localhost:27017/pruebas_test?authSource=admin")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BuscadorIntegracionTest {

	private static final String CSV = """
			fecha_asignacion,sexo,anio_nacimiento,materia,numero_de_fecha_de_evaluacion,anio_de_ingreso,aprobacion,carrera_objetivo,departamento_institucion_educativa,municipio_institucion_educativa,tipo_institucion_educativa,correlativo_aspirante
			2014-08-23,M,1998,MATEMÁTICA,1,2015,NO APROBADO,INGENIERÍA CIVIL,GUATEMALA,MIXCO,PUBLICO,a1
			2014-10-11,M,1998,MATEMÁTICA,2,2015,APROBADO,INGENIERÍA CIVIL,GUATEMALA,MIXCO,PUBLICO,a1
			2014-08-23,M,1998,FISICA,1,2015,APROBADO,INGENIERÍA CIVIL,GUATEMALA,MIXCO,PUBLICO,a1
			2014-08-23,F,1997,MATEMÁTICA,1,2015,APROBADO,INGENIERIA EN SISTEMAS,ESCUINTLA,ESCUINTLA,PRIVADO,a2
			2014-09-15,F,1997,FISICA,1,2015,NO APROBADO,INGENIERIA EN SISTEMAS,ESCUINTLA,ESCUINTLA,PRIVADO,a2
			2014-08-23,M,SIN REGISTRO,MATEMÁTICA,1,2015,NO APROBADO,NINGUNO,GUATEMALA,MIXCO,PUBLICO,b1
			2015-08-20,F,2000,MATEMÁTICA,1,2016,APROBADO,INGENIERIA EN SISTEMAS,GUATEMALA,VILLA NUEVA,PUBLICO,c1
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CargaService cargaService;

	@Autowired
	private MongoTemplate mongoTemplate;

	@MockitoBean
	private CargaNeo4jRepository grafo;

	@BeforeAll
	void cargarDatos() {
		mongoTemplate.getDb().drop();
		cargaService.cargar(List.of(new MockMultipartFile("archivos", "buscador.csv", "text/csv",
				CSV.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void sinFiltrosDevuelveTodoPaginado() throws Exception {
		mockMvc.perform(get("/api/buscar").param("tamano", "3").param("pagina", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(7))
				.andExpect(jsonPath("$.totalPaginas").value(3))
				.andExpect(jsonPath("$.datos.length()").value(1))
				// Ordenado por _id: la última evaluación es la del aspirante c1
				.andExpect(jsonPath("$.datos[0].correlativo_aspirante").value("c1"))
				.andExpect(jsonPath("$.datos[0].carga_id").doesNotExist());
	}

	@Test
	void combinaFiltrosYNormalizaElTexto() throws Exception {
		mockMvc.perform(get("/api/buscar").param("carrera", "sistemas").param("departamento", "guatemala"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.datos[0].correlativo_aspirante").value("c1"))
				.andExpect(jsonPath("$.filtros.carrera").value("SISTEMAS"));

		// "ingeniería civil" con tilde encuentra lo que el ETL guardó sin tilde
		mockMvc.perform(get("/api/buscar").param("carrera", "ingeniería civil").param("aprobado", "true"))
				.andExpect(jsonPath("$.total").value(2));

		mockMvc.perform(get("/api/buscar").param("institucion", "privado").param("materia", "fisica"))
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.datos[0].aprobado").value(false));

		mockMvc.perform(get("/api/buscar").param("municipio", "villa").param("anio", "2016"))
				.andExpect(jsonPath("$.total").value(1));
	}

	@Test
	void aspiranteBuscaPorInicioDelCorrelativo() throws Exception {
		mockMvc.perform(get("/api/buscar").param("aspirante", " A"))
				.andExpect(jsonPath("$.total").value(5))
				.andExpect(jsonPath("$.datos[*].correlativo_aspirante", everyItem(startsWith("a"))));
		mockMvc.perform(get("/api/buscar").param("aspirante", "a1"))
				.andExpect(jsonPath("$.total").value(3));
	}

	@Test
	void losCaracteresEspecialesSeBuscanLiteralmente() throws Exception {
		mockMvc.perform(get("/api/buscar").param("carrera", ".*").param("aspirante", "("))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void opcionesParaLasListas() throws Exception {
		mockMvc.perform(get("/api/buscar/opciones"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.carreras", hasItem("SIN CARRERA")))
				.andExpect(jsonPath("$.materias.length()").value(2))
				.andExpect(jsonPath("$.departamentos[0]").value("ESCUINTLA"))
				.andExpect(jsonPath("$.municipios.length()").value(3))
				.andExpect(jsonPath("$.aniosIngreso[1]").value(2016));
	}

	@Test
	void parametrosInvalidosDevuelvenErrorJson() throws Exception {
		mockMvc.perform(get("/api/buscar").param("tamano", "500"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.estado").value(400))
				.andExpect(jsonPath("$.error", containsString("tamano")))
				.andExpect(jsonPath("$.ruta").value("/api/buscar"))
				.andExpect(jsonPath("$.timestamp").exists());

		mockMvc.perform(get("/api/buscar").param("anio", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", containsString("anio")));

		mockMvc.perform(get("/api/cargas/no-es-un-id"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", containsString("inválido")));

		mockMvc.perform(get("/api/mongo/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", containsString("id")));
	}

	@Test
	void rutasYMetodosInexistentesDevuelvenErrorJson() throws Exception {
		mockMvc.perform(get("/api/no-existe"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("No existe la ruta /api/no-existe"));

		mockMvc.perform(delete("/api/buscar"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.error", containsString("DELETE")));

		mockMvc.perform(get("/api/mongo/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("No existe la consulta 99"));
	}

}
