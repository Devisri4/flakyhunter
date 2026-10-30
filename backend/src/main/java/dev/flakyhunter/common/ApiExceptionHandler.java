package dev.flakyhunter.common;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Turns every error into a ProblemDetail; other MVC errors are handled by the Spring base class. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ProblemDetail handleApi(ApiException e) {
		return ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
	}

	// e.g. two requests creating the same slug at once: the unique index wins
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleConflict(DataIntegrityViolationException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This conflicts with existing data");
	}

	/** "password: must be 8 to 72 characters" instead of Spring's generic "Invalid request content." */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String detail = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> e.getField() + ": " + e.getDefaultMessage())
				.sorted()
				.collect(Collectors.joining("; "));
		ProblemDetail body = ex.getBody();
		body.setDetail(detail.isEmpty() ? "Invalid request" : detail);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

}
