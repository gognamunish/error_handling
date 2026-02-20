package com.cfbl.platform.core.retry;

/**
 * Basic retry settings used by {@link RetryPolicyExecutor}.
 */
public record RetrySettings(
    boolean enabled,
    int maxAttempts,
    long waitDurationMs
) {

    /**
     * Default retry settings for provider calls.
     */
    public static RetrySettings defaults() {
        return new RetrySettings(true, 3, 200L);
    }

    /**
     * Effective max attempts for the current mode.
     */
    public int effectiveMaxAttempts() {
        return enabled ? maxAttempts : 1;
    }
}
