package com.healthlife.gateway.config;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

/**
 * Global exception handler for the Gateway service. Converts all unhandled exceptions into
 * RFC 7807 {@link ProblemDetail} responses so that clients receive consistent, machine-readable
 * error payloads regardless of the underlying failure.
 */
@RestControllerAdvice
public class GatewayGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayGlobalExceptionHandler.class);

    @ExceptionHandler(ResourceAccessException.class)
    public ProblemDetail handleResourceAccess(ResourceAccessException ex) {
        log.warn("Downstream service unreachable: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "The requested service is temporarily unavailable. Please retry later.");
        problem.setTitle("Service Unavailable");
        problem.setType(URI.create("https://healthlife.com/errors/service-unavailable"));
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled gateway exception: {}", ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please contact support.");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://healthlife.com/errors/internal-error"));
        return problem;
    }
}
