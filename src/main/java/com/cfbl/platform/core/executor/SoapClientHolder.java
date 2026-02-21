package com.cfbl.platform.core.executor;

import com.cfbl.platform.core.retry.RetrySettings;
import java.util.Objects;

/**
 * Immutable holder for SOAP provider identity and retry policy metadata.
 *
 * <p>Used by {@link SoapCallExecutor} to keep port invocation concerns separate from
 * configuration resolution.
 */
public record SoapClientHolder(
    String serviceId,
    String endpointUrl,
    RetrySettings retrySettings
) {

    public SoapClientHolder(String serviceId, String endpointUrl) {
        this(serviceId, endpointUrl, RetrySettings.defaults());
    }

    public SoapClientHolder {
        Objects.requireNonNull(serviceId, "serviceId");
        Objects.requireNonNull(endpointUrl, "endpointUrl");
        retrySettings = retrySettings == null ? RetrySettings.defaults() : retrySettings;
    }
}
