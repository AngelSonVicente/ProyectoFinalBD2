package com.example.backend.consultas.mongo;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mongo")
public class ConsultaMongoController {

	private final CatalogoConsultasMongo catalogo;

	private final ConsultaMongoService servicio;

	public ConsultaMongoController(CatalogoConsultasMongo catalogo, ConsultaMongoService servicio) {
		this.catalogo = catalogo;
		this.servicio = servicio;
	}

	public record ConsultaInfo(int id, String titulo, String descripcion, String coleccion,
			List<ConsultaMongo.Parametro> parametros) {
	}

	/** Lista de consultas disponibles, para armar el dashboard. */
	@GetMapping
	public List<ConsultaInfo> listar() {
		return catalogo.todas().stream()
				.map(c -> new ConsultaInfo(c.id(), c.titulo(), c.descripcion(), c.coleccion(), c.parametros()))
				.toList();
	}

	/** Ejecuta una consulta. Los parámetros (por ejemplo, correlativo) van como query string. */
	@GetMapping("/{id}")
	public ConsultaMongoService.ResultadoConsulta ejecutar(@PathVariable int id,
			@RequestParam Map<String, String> parametros) {
		return servicio.ejecutar(id, parametros);
	}

}
