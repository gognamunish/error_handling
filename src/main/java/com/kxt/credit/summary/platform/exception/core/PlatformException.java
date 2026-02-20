package com.kxt.credit.summary.platform.exception.core;

import java.util.Objects;

public class PlatformException extends RuntimeException {

    private final ErrorCode code;
    private final DataSourceContext source;
    private final UpstreamInfo upstream;

    public PlatformException(
        ErrorCode code,
        String message,
        DataSourceContext source,
        UpstreamInfo upstream,
        Throwable cause
    ) {
        super(message == null || message.isBlank() ? code.defaultMessage() : message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.source = source;
        this.upstream = upstream;
    }

    public PlatformException(ErrorCode code, String message) {
        this(code, message, null, null, null);
    }

    public PlatformException(ErrorCode code, String message, Throwable cause) {
        this(code, message, null, null, cause);
    }

    public ErrorCode getCode() {
        return code;
    }

    public DataSourceContext getSource() {
        return source;
    }

    public UpstreamInfo getUpstream() {
        return upstream;
    }
}
