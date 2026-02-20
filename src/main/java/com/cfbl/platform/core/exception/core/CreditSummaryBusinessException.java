package com.cfbl.platform.core.exception.core;

/**
 * Exception type for business-layer failures.
 */
public class CreditSummaryBusinessException extends CreditSummaryPlatformException {

    public static final String MODULE = "BUSINESS";

    public CreditSummaryBusinessException(
        ErrorCode code,
        String message,
        DataProviderContext source,
        UpstreamInfo upstream,
        Throwable cause
    ) {
        super(MODULE, code, message, source, upstream, cause);
    }

    public CreditSummaryBusinessException(ErrorCode code, String message) {
        super(MODULE, code, message, null, null, null);
    }
}
