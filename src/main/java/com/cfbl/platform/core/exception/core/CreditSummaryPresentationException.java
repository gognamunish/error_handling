package com.cfbl.platform.core.exception.core;

/**
 * Exception type for presentation/API layer failures.
 */
public class CreditSummaryPresentationException extends CreditSummaryPlatformException {

    public static final LayerType LAYER = LayerType.PRESENTATION;

    public CreditSummaryPresentationException(
            ErrorCode code,
            String message,
            DataProviderContext providerContext,
            UpstreamInfo upstream,
            Throwable cause) {
        super(LAYER, code, message, providerContext, upstream, cause);
    }

    public CreditSummaryPresentationException(ErrorCode code, String message) {
        super(LAYER, code, message, null, null, null);
    }
}
