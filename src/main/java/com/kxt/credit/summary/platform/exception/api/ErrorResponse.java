package com.kxt.credit.summary.platform.exception.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    Instant timestamp,
    String traceId,
    int status,
    String error,
    String code,
    String message,
    Source source,
    Upstream upstream,
    List<ValidationError> validationErrors
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Source(
        String protocol,
        String serviceId,
        String endpoint,
        Map<String, String> protocolMeta
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Upstream(
        Integer httpStatus,
        String rawMessage,
        Long responseTimeMs
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ValidationError(
        String field,
        String message
    ) {
    }
}
