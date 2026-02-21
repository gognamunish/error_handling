package com.cfbl.platform.core.exception.core;

/**
 * Exception type for business-layer failures.
 */
public class CreditSummaryBusinessException extends CreditSummaryPlatformException {

    public static final LayerType LAYER = LayerType.BUSINESS;

    public CreditSummaryBusinessException(
        ErrorCode code,
        String message,
        DataProviderContext providerContext,
        UpstreamInfo upstream,
        Throwable cause
    ) {
        super(LAYER, code, message, providerContext, upstream, cause);
    }

    public CreditSummaryBusinessException(ErrorCode code, String message) {
        super(LAYER, code, message, null, null, null);
    }
}
