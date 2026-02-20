package com.cfbl.platform.core.exception.core;

/**
 * Exception type for presentation/API layer failures.
 */
public class CreditSummaryPresentationException extends CreditSummaryPlatformException {

    public static final String MODULE = "PRESENTATION";

    public CreditSummaryPresentationException(
            ErrorCode code,
            String message,
            DataProviderContext source,
            UpstreamInfo upstream,
            Throwable cause) {
        super(MODULE, code, message, source, upstream, cause);
    }

    public CreditSummaryPresentationException(ErrorCode code, String message) {
        super(MODULE, code, message, null, null, null);
    }
}
