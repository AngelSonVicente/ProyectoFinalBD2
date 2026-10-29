package gt.usac.bd2.pruebas.etl;

/**
 * Error que impide procesar el archivo completo (por ejemplo, encabezados
 * faltantes o un archivo ilegible). Los errores de una sola fila no lanzan
 * esta excepción: se reportan como {@link RegistroRechazado}.
 */
public class EtlException extends RuntimeException {

	public EtlException(String mensaje) {
		super(mensaje);
	}

	public EtlException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}

}
