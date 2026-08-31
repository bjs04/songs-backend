package com.songs.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import com.songs.dtos.OmnibarResponseDTO;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SongAlreadyExistsException.class)
    public ResponseEntity<Object> handleSongAlreadyExistsException(SongAlreadyExistsException ex) {
        
        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("message", ex.getMessage());
        
        return new ResponseEntity<>(errorBody, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("message", ex.getMessage());
        return new ResponseEntity<>(errorBody, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        
        // Grab the first validation error message it finds (e.g., "Song title is mandatory.")
        FieldError firstError = ex.getBindingResult().getFieldErrors().get(0);
        
        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("message", firstError.getDefaultMessage());
        
        return new ResponseEntity<>(errorBody, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<OmnibarResponseDTO> handleAIFailures(RestClientException ex) {
        logger.error("AI Agent Connection Error", ex);

        OmnibarResponseDTO fallbackResponse = new OmnibarResponseDTO(
                "MESSAGE", 
                "I'm having a little trouble connecting right now. Please try again in a moment!", 
                null
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(fallbackResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGlobalException(Exception ex) {
        logger.error("Unexpected application error", ex);

        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("timestamp", LocalDateTime.now());
        errorBody.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        errorBody.put("error", "Internal Server Error");
        errorBody.put("message", "An unexpected error occurred.");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody);
    }

}