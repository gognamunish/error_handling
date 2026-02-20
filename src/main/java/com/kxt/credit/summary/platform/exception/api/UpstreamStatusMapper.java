package com.kxt.credit.summary.platform.exception.api;

import com.kxt.credit.summary.platform.exception.core.ErrorCode;

public final class UpstreamStatusMapper {

    private UpstreamStatusMapper() {
    }

    public static ErrorCode mapHttpStatus(int upstreamStatus) {
        return switch (upstreamStatus) {
            case 429, 503 -> ErrorCode.UPSTREAM_UNAVAILABLE;
            case 504 -> ErrorCode.UPSTREAM_TIMEOUT;
            default -> ErrorCode.UPSTREAM_BAD_GATEWAY;
        };
    }
}
