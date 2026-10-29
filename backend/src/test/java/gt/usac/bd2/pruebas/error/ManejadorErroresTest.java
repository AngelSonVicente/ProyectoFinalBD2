package gt.usac.bd2.pruebas.error;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mongodb.MongoTimeoutException;

import gt.usac.bd2.pruebas.busqueda.BuscadorService;

/**
 * Simula fallas de las bases de datos para comprobar que la API responde con
 * un error controlado y no con una página de error.
 */
@SpringBootTest(properties = "spring.mongodb.uri=mongodb://admin:admin123@localhost:27017/pruebas_test?authSource=admin")
@AutoConfigureMockMvc
class ManejadorErroresTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BuscadorService buscador;

	@Test
	void mongoSinConexion() throws Exception {
		when(buscador.buscar(any(), anyInt(), anyInt())).thenThrow(new MongoTimeoutException("Timed out"));

		mockMvc.perform(get("/api/buscar"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("No se pudo conectar con MongoDB"))
				.andExpect(jsonPath("$.detalle").value("Timed out"));
	}

	@Test
	void neo4jEnvueltoPorSpring() throws Exception {
		when(buscador.opciones()).thenThrow(new DataAccessResourceFailureException("falló",
				new ServiceUnavailableException("Unable to connect to localhost:7687")));

		mockMvc.perform(get("/api/buscar/opciones"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("No se pudo conectar con Neo4j"));
	}

	@Test
	void errorInesperadoNoExponeLaTraza() throws Exception {
		when(buscador.opciones()).thenThrow(new IllegalStateException("detalle interno"));

		mockMvc.perform(get("/api/buscar/opciones"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("Ocurrió un error interno en el servidor"))
				.andExpect(jsonPath("$.detalle").value("IllegalStateException"));
	}

}
