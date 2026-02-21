package com.cfbl.platform.core.executor;

import com.cfbl.platform.core.config.PlatformProperties;
import com.cfbl.platform.core.config.PlatformProperties.ServiceDefinition;
import com.cfbl.platform.core.retry.RetrySettings;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Builds {@link WebClientHolder} instances from {@link PlatformProperties} service definitions.
 *
 * <p>This is the single place where REST endpoint and retry policy are resolved from
 * configuration for outbound WebClient integrations.
 */
@Component
public class WebClientHolderFactory {

    private final PlatformProperties platformProperties;
    private final WebClient.Builder webClientBuilder;

    public WebClientHolderFactory(PlatformProperties platformProperties, WebClient.Builder webClientBuilder) {
        this.platformProperties = platformProperties;
        this.webClientBuilder = webClientBuilder;
    }

    /**
     * Creates a holder for the given service id.
     *
     * @param serviceId service key in {@code kxt.platform.services}
     * @param fallbackEndpoint endpoint used when the service entry is missing
     * @return holder containing endpoint, WebClient and retry settings
     */
    public WebClientHolder create(String serviceId, String fallbackEndpoint) {
        Objects.requireNonNull(serviceId, "serviceId");
        ServiceDefinition service = platformProperties.getServices().get(serviceId);
        String endpoint = resolveEndpoint(service, fallbackEndpoint, serviceId);
        RetrySettings retrySettings = service != null && service.getRetrySettings() != null
            ? service.getRetrySettings()
            : RetrySettings.defaults();

        WebClient webClient = webClientBuilder.clone().baseUrl(endpoint).build();
        return new WebClientHolder(serviceId, endpoint, webClient, retrySettings);
    }

    private String resolveEndpoint(ServiceDefinition service, String fallbackEndpoint, String serviceId) {
        String endpoint = service != null ? service.getEndpointUrl() : fallbackEndpoint;
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("No endpoint configured for serviceId=" + serviceId);
        }
        return endpoint;
    }
}
