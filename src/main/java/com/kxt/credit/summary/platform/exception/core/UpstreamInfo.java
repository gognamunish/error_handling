package com.kxt.credit.summary.platform.exception.core;

public record UpstreamInfo(
    Integer httpStatus,
    String rawMessage,
    Long responseTimeMs
) {
}
