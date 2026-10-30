package com.example.backend.consultas.neo4j;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/grafo")
public class GrafoController {

	private final CatalogoConsultasCypher catalogo;

	private final ConsultaCypherService servicio;

	public GrafoController(CatalogoConsultasCypher catalogo, ConsultaCypherService servicio) {
		this.catalogo = catalogo;
		this.servicio = servicio;
	}

	public record ConsultaInfo(int id, String titulo, String descripcion, List<ConsultaCypher.Parametro> parametros,
			String cypher) {
	}

	/** Consultas disponibles con sus filtros, para el selector del explorador de grafos. */
	@GetMapping
	public List<ConsultaInfo> listar() {
		return catalogo.todas().stream()
				.map(c -> new ConsultaInfo(c.id(), c.titulo(), c.descripcion(), c.parametros(), c.cypher()))
				.toList();
	}

	/** Ejecuta una consulta. Los filtros (departamento, carrera, materia, limite...) van como query string. */
	@GetMapping("/{id}")
	public ConsultaCypherService.ResultadoGrafo ejecutar(@PathVariable int id,
			@RequestParam Map<String, String> parametros) {
		return servicio.ejecutar(id, parametros);
	}

}
