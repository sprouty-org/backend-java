package si.uni.fri.sprouty.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import si.uni.fri.sprouty.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles specific business logic exceptions thrown via ResponseStatusException.
     * Maps to 401, 404, 409 etc.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        logger.warn("Business logic error: {} - Status: {}", ex.getReason(), ex.getStatusCode());

        String message = ex.getReason() != null ? ex.getReason() : "A business logic error occurred.";
        return buildResponse(message, (HttpStatus) ex.getStatusCode());
    }

    /**
     * Handles validation and bad input errors.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        logger.warn("Validation error: {}", ex.getMessage());
        return buildResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /**
     * Final catch-all for unhandled exceptions (Runtime, NullPointer, Database down).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        // Log the full stack trace for internal debugging
        logger.error("Internal System Error: ", ex);

        String genericMessage = "An unexpected error occurred. Please contact support if the issue persists.";
        return buildResponse(genericMessage, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> buildResponse(String message, HttpStatus status) {
        ErrorResponse error = new ErrorResponse(
                message,
                System.currentTimeMillis(),
                status.value()
        );
        return new ResponseEntity<>(error, status);
    }
}