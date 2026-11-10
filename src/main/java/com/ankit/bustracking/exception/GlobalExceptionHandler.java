package com.ankit.bustracking.exception;

import com.ankit.bustracking.dto.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<ErrorResponseDTO> buildResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors) {
        String path = request != null ? request.getRequestURI() : "";
        ErrorResponseDTO body = new ErrorResponseDTO(
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                fieldErrors
        );
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        String summary = errors.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("; "));
        if (summary.isEmpty()) {
            summary = "Validation failed";
        }

        return buildResponse(HttpStatus.BAD_REQUEST, summary, request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed or unreadable JSON request body", request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        String message = "Invalid value for parameter '" + exception.getName() + "'";
        return buildResponse(HttpStatus.BAD_REQUEST, message, request, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgumentException(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalStateException(
            IllegalStateException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request, null);
    }

    @ExceptionHandler(BusNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleBusNotFoundException(
            BusNotFoundException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        String raw = exception.getMostSpecificCause() != null
                ? exception.getMostSpecificCause().getMessage()
                : exception.getMessage();
        String lower = raw != null ? raw.toLowerCase() : "";
        String message = "Duplicate record already exists";
        if (lower.contains("bus_number")) {
            message = "A bus with this bus number already exists";
        } else if (lower.contains("registration_number")) {
            message = "A bus with this registration number already exists";
        } else if (lower.contains("driver_id")) {
            message = "A driver with this email ID already exists";
        } else if (lower.contains("phone_number")) {
            message = "A driver with this phone number already exists";
        } else if (lower.contains("license_number")) {
            message = "A driver with this license number already exists";
        } else if (lower.contains("route_code")) {
            message = "A route with this route code already exists";
        } else if (lower.contains("stop_name") || lower.contains("latitude")) {
            message = "A stop with the same name and coordinates already exists";
        }
        return buildResponse(HttpStatus.CONFLICT, message, request, null);
    }

    @ExceptionHandler(DriverNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleDriverNotFoundException(
            DriverNotFoundException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request, null);
    }

    @ExceptionHandler(StopNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleStopNotFoundException(
            StopNotFoundException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request, null);
    }

    @ExceptionHandler(RouteNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleRouteNotFoundException(
            RouteNotFoundException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidTripTimeException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidTripTimeException(
            InvalidTripTimeException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request, null);
    }

    @ExceptionHandler(BusNotActiveException.class)
    public ResponseEntity<ErrorResponseDTO> handleBusNotActiveException(
            BusNotActiveException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request, null);
    }

    @ExceptionHandler(DriverInactiveException.class)
    public ResponseEntity<ErrorResponseDTO> handleDriverInactiveException(
            DriverInactiveException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request, null);
    }

    @ExceptionHandler(RouteInactiveException.class)
    public ResponseEntity<ErrorResponseDTO> handleRouteInactiveException(
            RouteInactiveException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request, null);
    }

    @ExceptionHandler(DriverUnavailableException.class)
    public ResponseEntity<ErrorResponseDTO> handleDriverUnavailableException(
            DriverUnavailableException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request, null);
    }

    @ExceptionHandler(TripNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleTripNotFoundException(
            TripNotFoundException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request, null);
    }

    @ExceptionHandler(BusScheduleConflictException.class)
    public ResponseEntity<ErrorResponseDTO> handleBusScheduleConflictException(
            BusScheduleConflictException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request, null);
    }

    @ExceptionHandler(DriverScheduleConflictException.class)
    public ResponseEntity<ErrorResponseDTO> handleDriverScheduleConflictException(
            DriverScheduleConflictException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request, null);
    }

    @ExceptionHandler(TripStatusException.class)
    public ResponseEntity<ErrorResponseDTO> handleTripStatusException(
            TripStatusException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request, null);
    }

    @ExceptionHandler(DriverNotMatchedException.class)
    public ResponseEntity<ErrorResponseDTO> handleDriverNotMatchedException(
            DriverNotMatchedException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, exception.getMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected server error occurred",
                request,
                null
        );
    }
}
