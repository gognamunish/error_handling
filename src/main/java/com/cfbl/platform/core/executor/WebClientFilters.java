package com.cfbl.platform.core.executor;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

/**
 * Provides WebClient filters that integrate with the platform's execution
 * context propagation.
 */
public class WebClientFilters {

    /**
     * An ExchangeFilterFunction that resolves Authentication from the Reactor
     * Context
     * (as populated by {@link RestCallExecutor}) and injects it as a Bearer Token
     * Authorizaton header.
     * <p>
     * This filter dynamically evaluates the Reactor Context on every execution,
     * making it safe for
     * WebClient Retry scenarios executing on background I/O threads.
     *
     * @return an {@link ExchangeFilterFunction} that adds the Authorization header
     *         if an Authentication object is present in the context.
     */
    public static ExchangeFilterFunction bearerTokenAuthFilter() {
        return (request, next) -> Mono.deferContextual(ctx -> {
            // Retrieve the Authentication object securely placed by RestCallExecutor
            Authentication auth = ctx.getOrDefault("CURRENT_AUTHENTICATION", null);

            if (auth != null && auth.getCredentials() != null) {
                ClientRequest newRequest = ClientRequest.from(request)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.getCredentials().toString())
                        .build();
                return next.exchange(newRequest);
            }

            // Fallback for unauthenticated or missing contexts
            return next.exchange(request);
        });
    }
}
