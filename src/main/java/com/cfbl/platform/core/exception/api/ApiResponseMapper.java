package com.cfbl.platform.core.exception.api;

import com.cfbl.platform.core.exception.core.CreditSummaryPlatformException;
import com.cfbl.platform.core.exception.core.ErrorCode;
import com.cfbl.platform.core.exception.core.UpstreamInfo;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Maps platform exceptions into {@link ApiResponse} error envelopes.
 */
@Component
public class ApiResponseMapper {

    /**
     * Converts a platform exception into an error response body.
     */
    public ApiResponse<Void> fromPlatformException(CreditSummaryPlatformException ex) {
        HttpStatus status = ex.getCode().httpStatus();
        return ApiResponse.error(
            status,
            ex.getCode().name(),
            ex.getModule(),
            status.getReasonPhrase(),
            ex.getMessage(),
            ex.getSource(),
            ex.getRetryInfo(),
            toUpstream(ex.getUpstream()),
            List.of()
        );
    }

    /**
     * Converts a generic error code into an error response body.
     */
    public ApiResponse<Void> fromCode(ErrorCode code, String message) {
        HttpStatus status = code.httpStatus();
        return ApiResponse.error(
            status,
            code.name(),
            CreditSummaryPlatformException.MODULE_PLATFORM,
            status.getReasonPhrase(),
            message == null || message.isBlank() ? code.defaultMessage() : message,
            null,
            null,
            null,
            List.of()
        );
    }

    private ApiResponse.Upstream toUpstream(UpstreamInfo upstream) {
        if (upstream == null) {
            return null;
        }

        return new ApiResponse.Upstream(
            upstream.httpStatus(),
            upstream.rawMessage(),
            upstream.responseTimeMs()
        );
    }
}
