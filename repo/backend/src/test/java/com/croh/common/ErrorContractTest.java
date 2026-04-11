package com.croh.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorContractTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void errorResponse_serializesToExpectedJsonShape() throws Exception {
        ErrorResponse error = new ErrorResponse(
                "VALIDATION_ERROR",
                "Request validation failed",
                List.of(new ErrorResponse.FieldError("username", "NotBlank", "Username is required")),
                "abc-123",
                Instant.parse("2026-01-01T00:00:00Z")
        );

        String json = objectMapper.writeValueAsString(error);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = objectMapper.readValue(json, Map.class);

        assertEquals("VALIDATION_ERROR", map.get("code"));
        assertEquals("Request validation failed", map.get("message"));
        assertEquals("abc-123", map.get("correlationId"));
        assertNotNull(map.get("timestamp"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> fieldErrors = (List<Map<String, String>>) map.get("fieldErrors");
        assertEquals(1, fieldErrors.size());
        assertEquals("username", fieldErrors.get(0).get("field"));
        assertEquals("NotBlank", fieldErrors.get(0).get("reason"));
        assertEquals("Username is required", fieldErrors.get(0).get("message"));
    }

    @Test
    void errorResponse_emptyFieldErrors_serializesAsEmptyList() throws Exception {
        ErrorResponse error = new ErrorResponse(
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                List.of(),
                "def-456",
                Instant.now()
        );

        String json = objectMapper.writeValueAsString(error);

        assertTrue(json.contains("\"fieldErrors\":[]"));
        assertTrue(json.contains("\"code\":\"INTERNAL_ERROR\""));
    }
}
