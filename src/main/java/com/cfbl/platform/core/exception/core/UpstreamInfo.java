package com.cfbl.platform.core.exception.core;

/**
 * Captures raw upstream response details for diagnostic purposes.
 */
public record UpstreamInfo(
    Integer httpStatus,
    String rawMessage,
    Long responseTimeMs
) {
}
