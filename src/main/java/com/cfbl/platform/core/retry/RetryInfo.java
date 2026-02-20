package com.cfbl.platform.core.retry;

/**
 * Retry execution metadata attached to API responses and platform exceptions.
 */
public record RetryInfo(
    int attempted,
    int maxAttempts,
    boolean retried,
    boolean exhausted
) {
}
