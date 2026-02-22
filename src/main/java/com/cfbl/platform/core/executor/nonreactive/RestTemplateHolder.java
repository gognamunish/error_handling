package com.cfbl.platform.core.executor.nonreactive;

import com.cfbl.platform.core.retry.RetrySettings;
import java.util.Objects;
import org.springframework.web.client.RestTemplate;

/**
 * Immutable holder for a preconfigured synchronous REST client and provider
 * metadata.
 */
public record RestTemplateHolder(
        String serviceId,
        String endpointUrl,
        RestTemplate restTemplate,
        RetrySettings retrySettings) {

    public RestTemplateHolder(String serviceId, String endpointUrl, RestTemplate restTemplate) {
        this(serviceId, endpointUrl, restTemplate, RetrySettings.defaults());
    }

    public RestTemplateHolder {
        Objects.requireNonNull(serviceId, "serviceId");
        Objects.requireNonNull(endpointUrl, "endpointUrl");
        Objects.requireNonNull(restTemplate, "restTemplate");
        retrySettings = retrySettings == null ? RetrySettings.defaults() : retrySettings;
    }
}
