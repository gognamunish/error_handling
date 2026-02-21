package com.cfbl.platform.core.integration.model;

import com.cfbl.platform.core.exception.core.DataProviderContext;
import com.cfbl.platform.core.retry.RetryInfo;

/**
 * Integration-layer result model for outbound provider calls.
 *
 * @param <T> provider payload type
 */
public record ProviderResult<T>(
        int status,
        T data,
        DataProviderContext metadata,
        RetryInfo retry) {

    /**
     * Creates a successful provider result without retry metadata.
     */
    public static <T> ProviderResult<T> success(int status, T data, DataProviderContext metadata) {
        return new ProviderResult<>(status, data, metadata, null);
    }

    /**
     * Creates a successful provider result with retry metadata.
     */
    public static <T> ProviderResult<T> success(int status, T data, DataProviderContext metadata, RetryInfo retry) {
        return new ProviderResult<>(status, data, metadata, retry);
    }
}
