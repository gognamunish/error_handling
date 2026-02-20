package com.kxt.credit.summary.platform.exception.core;

import java.time.Instant;
import java.util.Map;

public record DataSourceContext(
    Protocol protocol,
    String serviceId,
    String resolvedEndpoint,
    Map<String, String> protocolMeta,
    long responseTimeMs,
    Instant collectedAt
) {

    public enum Protocol {
        REST,
        SOAP,
        JDBC,
        KAFKA,
        FILE
    }
}
