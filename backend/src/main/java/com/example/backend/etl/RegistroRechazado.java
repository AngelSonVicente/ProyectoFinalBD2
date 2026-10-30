package com.example.backend.etl;

import java.util.List;
import java.util.Map;

/**
 * Fila que no pasó la validación. Se guarda la fila original para poder
 * identificarla y todas las causas encontradas.
 */
public record RegistroRechazado(long numeroFila, List<String> causas, Map<String, String> filaOriginal) {

	public String causa() {
		return String.join("; ", causas);
	}

}
