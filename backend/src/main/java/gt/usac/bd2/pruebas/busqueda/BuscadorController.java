package gt.usac.bd2.pruebas.busqueda;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/buscar")
@Validated
public class BuscadorController {

	private final BuscadorService servicio;

	public BuscadorController(BuscadorService servicio) {
		this.servicio = servicio;
	}

	/**
	 * Ejemplo: /api/buscar?carrera=sistemas&amp;departamento=guatemala&amp;aprobado=true&amp;pagina=0&amp;tamano=20
	 */
	@GetMapping
	public BuscadorService.PaginaBusqueda buscar(@ModelAttribute FiltrosBusqueda filtros,
			@RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int pagina,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
		return servicio.buscar(filtros, pagina, tamano);
	}

	@GetMapping("/opciones")
	public BuscadorService.Opciones opciones() {
		return servicio.opciones();
	}

}
