package com.cfbl.platform.core.exception.core;

/**
 * Exception type for data-collection and upstream integration failures.
 */
public class CreditSummaryDataCollectionException extends CreditSummaryPlatformException {

    public static final LayerType LAYER = LayerType.DATA_COLLECTION;

    public CreditSummaryDataCollectionException(
        ErrorCode code,
        String message,
        DataProviderContext providerContext,
        UpstreamInfo upstream,
        Throwable cause
    ) {
        super(LAYER, code, message, providerContext, upstream, cause);
    }

    public CreditSummaryDataCollectionException(ErrorCode code, String message) {
        super(LAYER, code, message, null, null, null);
    }
}
