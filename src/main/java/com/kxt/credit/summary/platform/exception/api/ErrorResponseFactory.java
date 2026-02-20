package com.kxt.credit.summary.platform.exception.api;

import com.kxt.credit.summary.platform.config.PlatformProperties;
import com.kxt.credit.summary.platform.exception.core.DataSourceContext;
import com.kxt.credit.summary.platform.exception.core.ErrorCode;
import com.kxt.credit.summary.platform.exception.core.PlatformException;
import com.kxt.credit.summary.platform.exception.core.UpstreamInfo;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ErrorResponseFactory {

    private static final String MASKED = "[masked]";

    private final PlatformProperties platformProperties;

    public ErrorResponseFactory(PlatformProperties platformProperties) {
        this.platformProperties = platformProperties;
    }

    public ErrorResponse fromPlatformException(PlatformException ex) {
        HttpStatus status = ex.getCode().httpStatus();
        return baseResponse(status, ex.getCode(), ex.getMessage(), toSource(ex.getSource()), toUpstream(ex.getUpstream()));
    }

    public ErrorResponse fromCode(ErrorCode code, String message) {
        HttpStatus status = code.httpStatus();
        return baseResponse(status, code, message, null, null);
    }

    private ErrorResponse baseResponse(
        HttpStatus status,
        ErrorCode code,
        String message,
        ErrorResponse.Source source,
        ErrorResponse.Upstream upstream
    ) {
        return new ErrorResponse(
            Instant.now(),
            resolveTraceId(),
            status.value(),
            status.getReasonPhrase(),
            code.name(),
            message == null || message.isBlank() ? code.defaultMessage() : message,
            source,
            upstream,
            List.of()
        );
    }

    private ErrorResponse.Source toSource(DataSourceContext source) {
        if (source == null) {
            return null;
        }

        String endpoint = platformProperties.isExposeEndpointInErrors()
            ? source.resolvedEndpoint()
            : MASKED;

        return new ErrorResponse.Source(
            source.protocol() == null ? null : source.protocol().name(),
            source.serviceId(),
            endpoint,
            source.protocolMeta()
        );
    }

    private ErrorResponse.Upstream toUpstream(UpstreamInfo upstream) {
        if (upstream == null) {
            return null;
        }

        return new ErrorResponse.Upstream(
            upstream.httpStatus(),
            upstream.rawMessage(),
            upstream.responseTimeMs()
        );
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? "N/A" : traceId;
    }
}
