package com.cfbl.platform.core.exception.core;

import com.cfbl.platform.core.retry.RetryInfo;
import java.util.Objects;

/**
 * Base runtime exception for credit-summary modules.
 */
public class CreditSummaryPlatformException extends RuntimeException {

    public static final String MODULE_PLATFORM = "CREDIT_SUMMARY_PLATFORM";

    private final String module;
    private final ErrorCode code;
    private final DataProviderContext source;
    private final UpstreamInfo upstream;
    private RetryInfo retryInfo;

    public CreditSummaryPlatformException(
            String module,
            ErrorCode code,
            String message,
            DataProviderContext source,
            UpstreamInfo upstream,
            Throwable cause) {
        super(message == null || message.isBlank() ? code.defaultMessage() : message, cause);
        this.module = Objects.requireNonNull(module, "module");
        this.code = Objects.requireNonNull(code, "code");
        this.source = source;
        this.upstream = upstream;
        this.retryInfo = null;
    }

    public CreditSummaryPlatformException(
            ErrorCode code,
            String message,
            DataProviderContext source,
            UpstreamInfo upstream,
            Throwable cause) {
        this(MODULE_PLATFORM, code, message, source, upstream, cause);
    }

    public CreditSummaryPlatformException(ErrorCode code, String message) {
        this(MODULE_PLATFORM, code, message, null, null, null);
    }

    public CreditSummaryPlatformException(ErrorCode code, String message, Throwable cause) {
        this(MODULE_PLATFORM, code, message, null, null, cause);
    }

    public String getModule() {
        return module;
    }

    public ErrorCode getCode() {
        return code;
    }

    public DataProviderContext getSource() {
        return source;
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
