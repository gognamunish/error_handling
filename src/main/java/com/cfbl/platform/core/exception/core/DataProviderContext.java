package com.cfbl.platform.core.exception.core;

import java.time.Instant;
import java.util.Map;

/**
 * Metadata describing a provider call that produced response data or an error.
 */
public record DataProviderContext(
    Protocol protocol,
    String serviceId,
    String endpoint,
    Map<String, String> protocolAttributes,
    long responseTimeMs,
    Instant collectedAt
) {
    /**
     * Supported integration protocols for provider interactions.
     */
    public enum Protocol {
        REST,
        SOAP,
        JDBC,
        KAFKA,
        FILE
    }
}
