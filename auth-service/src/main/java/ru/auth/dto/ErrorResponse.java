package ru.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ErrorResponse {

    private String code;

    private String message;

    private Instant timestamp;

    private String path;

    private String service;

    private String correlationId;
}


