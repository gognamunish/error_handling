package com.cfbl.platform.core.exception.core;

/**
 * Exception type for data-collection and upstream integration failures.
 */
public class CreditSummaryDataCollectionException extends CreditSummaryPlatformException {

    public static final String MODULE = "DATA_COLLECTION";

    public CreditSummaryDataCollectionException(
        ErrorCode code,
        String message,
        DataProviderContext source,
        UpstreamInfo upstream,
        Throwable cause
    ) {
        super(MODULE, code, message, source, upstream, cause);
    }

    public CreditSummaryDataCollectionException(ErrorCode code, String message) {
        super(MODULE, code, message, null, null, null);
    }
}
