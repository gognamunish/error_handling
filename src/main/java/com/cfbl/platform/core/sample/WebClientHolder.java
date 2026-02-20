package com.cfbl.platform.core.sample;

import java.util.Objects;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Holder for a preconfigured WebClient and its provider identity metadata.
 */
public record WebClientHolder(
    String serviceId,
    String endpointUrl,
    WebClient webClient
) {

    public WebClientHolder {
        Objects.requireNonNull(serviceId, "serviceId");
        Objects.requireNonNull(endpointUrl, "endpointUrl");
        Objects.requireNonNull(webClient, "webClient");
    }
}
