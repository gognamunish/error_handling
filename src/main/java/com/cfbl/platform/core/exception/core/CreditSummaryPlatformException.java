package com.cfbl.platform.core.exception.core;

import com.cfbl.platform.core.retry.RetryInfo;
import java.util.Objects;

/**
 * Base runtime exception for credit-summary modules.
 */
public class CreditSummaryPlatformException extends RuntimeException {

    public static final LayerType LAYER_PLATFORM = LayerType.PLATFORM;

    private final LayerType layer;
    private final ErrorCode code;
    private final DataProviderContext providerContext;
    private final UpstreamInfo upstream;
    private RetryInfo retryInfo;

    public CreditSummaryPlatformException(
            LayerType layer,
            ErrorCode code,
            String message,
            DataProviderContext providerContext,
            UpstreamInfo upstream,
            Throwable cause) {
        super(message == null || message.isBlank() ? code.defaultMessage() : message, cause);
        this.layer = Objects.requireNonNull(layer, "layer");
        this.code = Objects.requireNonNull(code, "code");
        this.providerContext = providerContext;
        this.upstream = upstream;
        this.retryInfo = null;
    }

    public CreditSummaryPlatformException(
            ErrorCode code,
            String message,
            DataProviderContext providerContext,
            UpstreamInfo upstream,
            Throwable cause) {
        this(LAYER_PLATFORM, code, message, providerContext, upstream, cause);
    }

    public CreditSummaryPlatformException(ErrorCode code, String message) {
        this(LAYER_PLATFORM, code, message, null, null, null);
    }

    public CreditSummaryPlatformException(ErrorCode code, String message, Throwable cause) {
        this(LAYER_PLATFORM, code, message, null, null, cause);
    }

    public LayerType getLayer() {
        return layer;
    }

    public ErrorCode getCode() {
        return code;
    }

    public DataProviderContext getProviderContext() {
        return providerContext;
    }

    public UpstreamInfo getUpstream() {
        return upstream;
    }

    public RetryInfo getRetryInfo() {
        return retryInfo;
    }

    public void attachRetryInfo(RetryInfo retryInfo) {
        this.retryInfo = retryInfo;
    }
}
