package com.example.backend.carga;

import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/cargas")
@Validated
public class CargaController {

	private final CargaService cargaService;

	private final CargaMongoRepository repositorio;

	public CargaController(CargaService cargaService, CargaMongoRepository repositorio) {
		this.cargaService = cargaService;
		this.repositorio = repositorio;
	}

	/** Recibe uno o varios CSV en el campo "archivos" y devuelve el resumen de cada uno. */
	@PostMapping(consumes = "multipart/form-data")
	public List<ResumenCarga> cargar(@RequestParam(name = "archivos", required = false) List<MultipartFile> archivos) {
		if (archivos == null || archivos.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe enviar al menos un archivo CSV");
		}
		return cargaService.cargar(archivos);
	}

	@GetMapping
	public List<Map<String, Object>> historial() {
		return repositorio.listarCargas().stream().map(CargaController::conIdTexto).toList();
	}

	@GetMapping("/{id}")
	public Map<String, Object> detalle(@PathVariable String id) {
		Document carga = repositorio.buscarCarga(aObjectId(id));
		if (carga == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la carga " + id);
		}
		return conIdTexto(carga);
	}

	@GetMapping("/{id}/rechazados")
	public PaginaRechazados rechazados(@PathVariable String id,
			@RequestParam(defaultValue = "0") @Min(0) int pagina,
			@RequestParam(defaultValue = "50") @Min(1) @Max(500) int tamano) {
		ObjectId cargaId = aObjectId(id);
		if (repositorio.buscarCarga(cargaId) == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la carga " + id);
		}
		List<RegistroRechazadoDto> datos = repositorio.listarRechazados(cargaId, pagina, tamano).stream()
				.map(RegistroRechazadoDto::de)
				.toList();
		return new PaginaRechazados(repositorio.contarRechazados(cargaId), pagina, tamano, datos);
	}

	private static ObjectId aObjectId(String id) {
		if (!ObjectId.isValid(id)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de carga inválido: " + id);
		}
		return new ObjectId(id);
	}

	/** ObjectId no se serializa bien a JSON; se devuelve como texto. */
	private static Map<String, Object> conIdTexto(Document carga) {
		Document copia = new Document(carga);
		copia.put("_id", carga.getObjectId("_id").toHexString());
		return copia;
	}

	public record PaginaRechazados(long total, int pagina, int tamano, List<RegistroRechazadoDto> datos) {
	}

	public record RegistroRechazadoDto(long numeroFila, String archivo, List<String> causas, Map<String, Object> filaOriginal) {

		static RegistroRechazadoDto de(Document doc) {
			return new RegistroRechazadoDto(
					((Number) doc.get("numero_fila")).longValue(),
					doc.getString("archivo"),
					doc.getList("causas", String.class),
					doc.get("fila_original", Document.class));
		}

	}

}
