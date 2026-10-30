package com.example.backend.salud;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

class SaludIntegracionTest {

	/** Con las bases de docker-compose levantadas. */
	@Nested
	@SpringBootTest(properties = "spring.mongodb.uri=mongodb://admin:admin123@localhost:27017/pruebas_test?authSource=admin")
	@AutoConfigureMockMvc
	class ConBasesDisponibles {

		@Autowired
		private MockMvc mockMvc;

		@Test
		void ambasDisponibles() throws Exception {
			mockMvc.perform(get("/api/health"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.estado").value("DISPONIBLE"))
					.andExpect(jsonPath("$.mongodb.estado").value("DISPONIBLE"))
					.andExpect(jsonPath("$.neo4j.estado").value("DISPONIBLE"));
		}

	}

	/** Puertos donde no hay nada escuchando: la API levanta igual y reporta la falla. */
	@Nested
	@SpringBootTest(properties = { "spring.mongodb.uri=mongodb://localhost:27999/pruebas_test",
			"spring.neo4j.uri=bolt://localhost:7699" })
	@AutoConfigureMockMvc
	class SinBases {

		@Autowired
		private MockMvc mockMvc;

		@Test
		void reportaCadaBaseCaida() throws Exception {
			mockMvc.perform(get("/api/health"))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.estado").value("NO_DISPONIBLE"))
					.andExpect(jsonPath("$.mongodb.estado").value("NO_DISPONIBLE"))
					.andExpect(jsonPath("$.neo4j.estado").value("NO_DISPONIBLE"))
					.andExpect(jsonPath("$.neo4j.detalle").exists());
		}

		@Test
		void lasConsultasDevuelvenErrorControlado() throws Exception {
			mockMvc.perform(get("/api/buscar"))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.error").value("No se pudo conectar con MongoDB"));
			mockMvc.perform(get("/api/grafo/2"))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.error").exists());
		}

	}

}
