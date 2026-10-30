package com.example.backend.error;

import java.time.Instant;
import java.util.stream.Collectors;

import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.mongodb.MongoException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Convierte cualquier excepción en una respuesta JSON {@link ApiError} con el
 * código HTTP adecuado. Así la API nunca devuelve una página de error ni deja
 * al frontend sin explicación.
 */
@RestControllerAdvice
public class ManejadorErrores {

	private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

	/** Errores lanzados a propósito por los servicios (404, 400, 503...). */
	@ExceptionHandler(ResponseStatusException.class)
	ResponseEntity<ApiError> estado(ResponseStatusException e, HttpServletRequest solicitud) {
		Throwable causa = e.getCause();
		return respuesta(e.getStatusCode(), e.getReason(), causa == null ? null : causa.getMessage(), solicitud);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<ApiError> parametroFaltante(MissingServletRequestParameterException e,
			HttpServletRequest solicitud) {
		return respuesta(HttpStatus.BAD_REQUEST, "Falta el parámetro '" + e.getParameterName() + "'", null,
				solicitud);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ApiError> tipoInvalido(MethodArgumentTypeMismatchException e, HttpServletRequest solicitud) {
		return respuesta(HttpStatus.BAD_REQUEST,
				"El parámetro '" + e.getName() + "' tiene un valor inválido: " + e.getValue(), null, solicitud);
	}

	/** Filtros del buscador con un tipo incorrecto, por ejemplo anio=abc. */
	@ExceptionHandler(BindException.class)
	ResponseEntity<ApiError> enlaceInvalido(BindException e, HttpServletRequest solicitud) {
		String campos = e.getFieldErrors().stream()
				.map(f -> "'" + f.getField() + "' (" + f.getRejectedValue() + ")")
				.collect(Collectors.joining(", "));
		return respuesta(HttpStatus.BAD_REQUEST, "Valor inválido en los parámetros " + campos, null, solicitud);
	}

	/** Restricciones como @Min y @Max en los parámetros de los controladores. */
	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ApiError> restriccion(ConstraintViolationException e, HttpServletRequest solicitud) {
		String mensajes = e.getConstraintViolations().stream()
				.map(v -> parametro(v.getPropertyPath().toString()) + ": " + v.getMessage())
				.sorted()
				.collect(Collectors.joining("; "));
		return respuesta(HttpStatus.BAD_REQUEST, "Parámetros inválidos. " + mensajes, null, solicitud);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ApiError> validacion(HandlerMethodValidationException e, HttpServletRequest solicitud) {
		String mensajes = e.getParameterValidationResults().stream()
				.flatMap(r -> r.getResolvableErrors().stream()
						.map(err -> r.getMethodParameter().getParameterName() + ": " + err.getDefaultMessage()))
				.sorted()
				.collect(Collectors.joining("; "));
		return respuesta(HttpStatus.BAD_REQUEST, "Parámetros inválidos. " + mensajes, null, solicitud);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ApiError> archivoGrande(MaxUploadSizeExceededException e, HttpServletRequest solicitud) {
		return respuesta(HttpStatus.CONTENT_TOO_LARGE,
				"El archivo supera el tamaño máximo permitido (200 MB por archivo, 500 MB por solicitud)", null,
				solicitud);
	}

	@ExceptionHandler(MultipartException.class)
	ResponseEntity<ApiError> archivoInvalido(MultipartException e, HttpServletRequest solicitud) {
		return respuesta(HttpStatus.BAD_REQUEST, "No se pudieron leer los archivos enviados", e.getMessage(),
				solicitud);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiError> cuerpoInvalido(HttpMessageNotReadableException e, HttpServletRequest solicitud) {
		return respuesta(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido", null, solicitud);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiError> rutaInexistente(NoResourceFoundException e, HttpServletRequest solicitud) {
		return respuesta(HttpStatus.NOT_FOUND, "No existe la ruta " + solicitud.getRequestURI(), null, solicitud);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ApiError> metodoNoPermitido(HttpRequestMethodNotSupportedException e,
			HttpServletRequest solicitud) {
		return respuesta(HttpStatus.METHOD_NOT_ALLOWED,
				"El método " + e.getMethod() + " no está permitido en " + solicitud.getRequestURI(), null, solicitud);
	}

	@ExceptionHandler(MongoException.class)
	ResponseEntity<ApiError> mongo(MongoException e, HttpServletRequest solicitud) {
		return baseDeDatos("MongoDB", e, solicitud);
	}

	@ExceptionHandler(Neo4jException.class)
	ResponseEntity<ApiError> neo4j(Neo4jException e, HttpServletRequest solicitud) {
		return baseDeDatos("Neo4j", e, solicitud);
	}

	/** Spring envuelve los errores de MongoTemplate y Neo4jClient en DataAccessException. */
	@ExceptionHandler(DataAccessException.class)
	ResponseEntity<ApiError> accesoADatos(DataAccessException e, HttpServletRequest solicitud) {
		Throwable causa = e.getMostSpecificCause();
		String motor = causa instanceof Neo4jException ? "Neo4j"
				: causa instanceof MongoException ? "MongoDB" : "la base de datos";
		return baseDeDatos(motor, causa, solicitud);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> inesperado(Exception e, HttpServletRequest solicitud) {
		// Excepciones estándar de Spring MVC que no tienen un manejador propio arriba
		if (e instanceof ErrorResponse respuestaSpring) {
			return respuesta(respuestaSpring.getStatusCode(), respuestaSpring.getBody().getDetail(), null, solicitud);
		}
		log.error("Error no controlado en {}", solicitud.getRequestURI(), e);
		return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno en el servidor",
				e.getClass().getSimpleName(), solicitud);
	}

	private ResponseEntity<ApiError> baseDeDatos(String motor, Throwable e, HttpServletRequest solicitud) {
		boolean sinConexion = e instanceof MongoTimeoutException || e instanceof MongoSocketException
				|| e instanceof ServiceUnavailableException;
		String mensaje = sinConexion ? "No se pudo conectar con " + motor
				: "Error al ejecutar la operación en " + motor;
		log.warn("{}: {}", mensaje, e.getMessage());
		return respuesta(HttpStatus.SERVICE_UNAVAILABLE, mensaje, e.getMessage(), solicitud);
	}

	/** "buscar.tamano" → "tamano" */
	private static String parametro(String ruta) {
		return ruta.substring(ruta.lastIndexOf('.') + 1);
	}

	private static ResponseEntity<ApiError> respuesta(HttpStatusCode estado, String error, String detalle,
			HttpServletRequest solicitud) {
		return ResponseEntity.status(estado)
				.body(new ApiError(estado.value(), error, detalle, solicitud.getRequestURI(), Instant.now()));
	}

}
